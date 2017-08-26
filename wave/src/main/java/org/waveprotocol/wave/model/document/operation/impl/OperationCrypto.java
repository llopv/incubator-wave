/**
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements.  See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership.  The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License.  You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied.  See the License for the
 * specific language governing permissions and limitations
 * under the License.
 */

package org.waveprotocol.wave.model.document.operation.impl;

import java.util.function.Function;

import org.waveprotocol.box.webclient.client.WaveCryptoManager;
import org.waveprotocol.box.webclient.client.WaveCryptoManager.Callback;
import org.waveprotocol.wave.model.document.operation.AnnotationBoundaryMap;
import org.waveprotocol.wave.model.document.operation.AnnotationBoundaryMapBuilder;
import org.waveprotocol.wave.model.document.operation.Attributes;
import org.waveprotocol.wave.model.document.operation.AttributesUpdate;
import org.waveprotocol.wave.model.document.operation.DocOp;
import org.waveprotocol.wave.model.document.operation.DocOpComponentType;
import org.waveprotocol.wave.model.document.operation.DocOpCursor;
import org.waveprotocol.wave.model.document.operation.algorithm.DocOpCollector;
import org.waveprotocol.wave.model.operation.wave.BlipContentOperation;
import org.waveprotocol.wave.model.operation.wave.BlipOperation;
import org.waveprotocol.wave.model.operation.wave.WaveletBlipOperation;
import org.waveprotocol.wave.model.operation.wave.WaveletOperation;
import org.waveprotocol.wave.model.operation.wave.WaveletOperationContext;

/**
 * Encrypts and decrypts Wavelet operations
 *
 * @author David Llop (llop@protonmail.com)
 */
public class OperationCrypto {

  private static final String CIPHER_TAG = "cipher/";

  public static WaveCryptoManager crypto = new WaveCryptoManager();

  /**
   * Attach every operation component the cursor visits in a
   * {@code DocOpBuilder}.
   */
  private static abstract class DocOpBuilderCursor implements DocOpCursor {

    protected DocOpBuilder builder = new DocOpBuilder();

    public DocOp getNewDop(DocOp dop) {
      dop.apply(this);
      return builder.build();
    }

    @Override
    public void annotationBoundary(AnnotationBoundaryMap map) {
      builder.annotationBoundary(map);
    }

    @Override
    public void characters(String chars) {
      builder.characters(chars);
    }

    @Override
    public void elementStart(String type, Attributes attrs) {
      builder.elementStart(type, attrs);
    }

    @Override
    public void elementEnd() {
      builder.elementEnd();
    }

    @Override
    public void retain(int itemCount) {
      builder.retain(itemCount);
    }

    @Override
    public void deleteCharacters(String chars) {
      builder.deleteCharacters(chars);
    }

    @Override
    public void deleteElementStart(String type, Attributes attrs) {
      builder.deleteElementStart(type, attrs);
    }

    @Override
    public void deleteElementEnd() {
      builder.deleteElementEnd();
    }

    @Override
    public void replaceAttributes(Attributes oldAttrs, Attributes newAttrs) {
      builder.replaceAttributes(oldAttrs, newAttrs);
    }

    @Override
    public void updateAttributes(AttributesUpdate attrUpdate) {
      builder.updateAttributes(attrUpdate);
    }
  }

  /**
   * Replaces text in "characters" and "delete characters" components by
   * asterisks (*) and collects the text in "characters" components in a string.
   */
  private static class Obfuscator extends DocOpBuilderCursor {

    protected String collectedText = "";

    public String getCollectedText() {
      return collectedText;
    }

    private String obfuscate(String text) {
      char[] chars = text.toCharArray();
      for (int i = 0; i < text.length(); i++) {
        chars[i] = '*';
      }
      return new String(chars);
    }

    @Override
    public void characters(String chars) {
      collectedText += chars;
      builder.characters(obfuscate(chars));
    }

    @Override
    public void deleteCharacters(String chars) {
      builder.deleteCharacters(obfuscate(chars));
    }
  }

  /**
   * Replaces all texts present in characters components by the decrypted text.
   */
  private static class Deobfuscator extends DocOpBuilderCursor {

    private String text;
    private int offset = 0;

    public Deobfuscator(String text) {
      this.text = text;
    }

    @Override
    public void characters(String chars) {
      String chunk = text.substring(offset, offset + chars.length());
      offset += chars.length();
      builder.characters(chunk);
    }
  }

  /**
   * Counts how many positions the cursor spans.
   */
  private static class Counter extends DocOpBuilderCursor {

    int count = 0;

    @Override
    public void characters(String chars) {
      count += chars.length();
    }

    @Override
    public void elementEnd() {
      count += 1;
    }

    @Override
    public void elementStart(String type, Attributes attrs) {
      count += 1;
    }

    @Override
    public void retain(int itemCount) {
      count += itemCount;
    }
  }

  /**
   * Encrypts a WaveletOperation
   *
   * @param waveId
   * @param op
   *          wavelet operation
   * @param id
   *          string that identifies this op
   * @param callback
   */
  public static void encrypt(String waveId, WaveletOperation op, String id,
      final Function<WaveletOperation, Void> callback) {
    CipherWrapper wrapper = new CipherWrapper();
    DocOp dop = wrapper.unwrap(op);
    if (dop == null) {
      callback.apply(op);
    } else {
      encrypt(waveId, dop, id, (DocOp encryptedDop) -> callback.apply(wrapper.rewrap(encryptedDop)));
    }
  }

  /**
   * Decrypts a WaveletOperation
   *
   * @param waveId
   * @param op
   *          wavelet operation
   * @param callback
   */
  public static void decrypt(String waveId, WaveletOperation op, final Function<WaveletOperation, Void> callback) {
    CipherWrapper wrapper = new CipherWrapper();
    DocOp dop = wrapper.unwrap(op);
    if (dop == null) {
      callback.apply(op);
    } else {
      decrypt(waveId, dop, (DocOp decryptedDop) -> callback.apply(wrapper.rewrap(decryptedDop)));
    }
  }

  /**
   * Unwraps and re-wraps a DocOp from a WaveletOperation.
   *
   * DocOps are wrapped inside BlipOperations and BlipOperations are wrapped
   * inside WaveletOperations, so we need to save WaveletOperation and
   * BlipOperation data to re-wrap.
   *
   */
  private static class CipherWrapper {
    private WaveletOperationContext context;
    private String blipId;

    public DocOp unwrap(WaveletOperation op) {
      if (op instanceof WaveletBlipOperation) {
        WaveletBlipOperation wop = (WaveletBlipOperation) op;
        if (wop.getBlipOp() instanceof BlipContentOperation) {
          BlipContentOperation bop = (BlipContentOperation) wop.getBlipOp();
          blipId = wop.getBlipId();
          context = bop.getContext();
          return bop.getContentOp();
        }
      }
      return null;
    }

    public WaveletOperation rewrap(DocOp dop) {
      BlipOperation bop = new BlipContentOperation(context, dop);
      return new WaveletBlipOperation(blipId, bop);
    }
  }

  /**
   * Adds a cipher/* annotation from the beginning to the end of the op with
   * some text
   *
   * @param dop
   * @param id
   * @param text
   * @return
   */
  private static DocOp annotate(DocOp dop, String id, String text) {
    Counter counter = new Counter();
    dop.apply(counter);
    String annotTag = CIPHER_TAG + id;
    AnnotationBoundaryMap boundary = new AnnotationBoundaryMapBuilder().change(annotTag, null, text).build();
    AnnotationBoundaryMap end = new AnnotationBoundaryMapBuilder().end(annotTag).build();
    DocOp ann = new DocOpBuilder().annotationBoundary(boundary).retain(counter.count).annotationBoundary(end).build();
    DocOpCollector collector = new DocOpCollector();
    collector.add(dop);
    collector.add(ann);
    return collector.composeAll();
  }

  /**
   * Removes the cipher/* annotation
   *
   * @param dop
   * @return
   */
  private static DocOp unannotate(DocOp dop) {

    if (cipherIndex(dop) < 0) {
      return dop;
    }

    DocOpBuilderCursor cursor = new DocOpBuilderCursor() {
      @Override
      public void annotationBoundary(AnnotationBoundaryMap map) {
        AnnotationBoundaryMapBuilder mapBuilder = new AnnotationBoundaryMapBuilder();
        for (int i = 0; i < map.endSize(); i++) {
          if (!map.getEndKey(i).startsWith(CIPHER_TAG)) {
            mapBuilder.end(map.getEndKey(i));
          }
        }
        for (int i = 0; i < map.changeSize(); i++) {
          if (!map.getChangeKey(i).startsWith(CIPHER_TAG)) {
            mapBuilder.change(map.getChangeKey(i), map.getOldValue(i), map.getNewValue(i));
          }
        }
        map = mapBuilder.build();
        if (map.changeSize() > 0 || map.endSize() > 0) {
          builder.annotationBoundary(mapBuilder.build());
        }
      }
    };

    return cursor.getNewDop(dop);
  }

  /**
   * Encrypts DocOp
   *
   * @param waveId
   * @param dop
   * @param id
   * @param callback
   */
  public static void encrypt(String waveId, DocOp dop, String id, final Function<DocOp, Void> callback) {
    Obfuscator cursor = new Obfuscator();
    DocOp obfuscatedDop = cursor.getNewDop(dop);
    String text = cursor.getCollectedText();
    if (!text.isEmpty()) {
      crypto.getCipher(waveId).encrypt(text, "", new Callback<String, Throwable>() {
        @Override
        public void onSuccess(String ciphertext) {
          callback.apply(annotate(obfuscatedDop, id, ciphertext));
        }

        @Override
        public void onFailure(Throwable reason) {
          throw new IllegalStateException(reason.toString());
        }
      });
    } else {
      callback.apply(obfuscatedDop);
    }
  }

  /**
   * Decrypts DocOp
   *
   * @param waveId
   * @param dop
   * @param callback
   */
  public static void decrypt(String waveId, DocOp dop, final Function<DocOp, Void> callback) {

    int index = cipherIndex(dop);
    if (index < 0) {
      callback.apply(dop);
      return;
    }

    String ciphertext = dop.getAnnotationBoundary(0).getNewValue(index);

    crypto.getCipher(waveId).decrypt(ciphertext, new Callback<String, Throwable>() {
      @Override
      public void onSuccess(String plaintext) {
        Deobfuscator cursor = new Deobfuscator(plaintext);
        DocOp processedDop = cursor.getNewDop(dop);
        callback.apply(unannotate(processedDop));
      }

      @Override
      public void onFailure(Throwable reason) {
        throw new IllegalStateException(reason.toString());
      }
    });
  }

  /**
   * Returns the index of the change key that has a cipher/* tag of the first
   * component of a DocOp
   *
   * @param dop
   * @return
   */
  private static int cipherIndex(DocOp dop) {
    if (dop.getType(0) != DocOpComponentType.ANNOTATION_BOUNDARY) {
      return -1;
    }

    AnnotationBoundaryMap map = dop.getAnnotationBoundary(0);
    int i = 0;

    while (i < map.changeSize() && !map.getChangeKey(i).startsWith(CIPHER_TAG)) {
      i++;
    }

    if (i >= map.changeSize()) {
      return -1;
    }

    return i;
  }
}
