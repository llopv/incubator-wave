package org.waveprotocol.wave.model.document.operation.impl;

import java.util.function.Function;

import org.waveprotocol.box.webclient.client.WaveCryptoManager.Callback;
import org.waveprotocol.box.webclient.client.WaveCryptoManager.Cipher;
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

public class OperationCrypto {

  private class CryptoCursor implements DocOpCursor {

    protected DocOpBuilder builder = new DocOpBuilder();
    protected String result = "";
    protected long rev;
    protected int count = 0;
    protected Cipher cipher;

    public CryptoCursor(Cipher cipher) {
      this.cipher = cipher;
    }

    public CryptoCursor(long rev, Cipher cipher) {
      this.cipher = cipher;
      this.rev = rev;
    }

    public DocOp getDocOp() {
      return builder.build();
    }

    public String getCollectedText() {
      return result;
    }

    public int getCount() {
      return count;
    }

    @Override
    public void annotationBoundary(AnnotationBoundaryMap map) {
      builder.annotationBoundary(map);
    }

    @Override
    public void characters(String chars) {
      builder.characters(chars);
      count += chars.length();
    }

    @Override
    public void elementStart(String type, Attributes attrs) {
      builder.elementStart(type, attrs);
      count += 1;
    }

    @Override
    public void elementEnd() {
      builder.elementEnd();
      count += 1;
    }

    @Override
    public void retain(int itemCount) {
      builder.retain(itemCount);
      count += itemCount;
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

  private Cipher cipher;

  public static OperationCrypto create(Cipher cipher) {
    return new OperationCrypto(cipher);
  }

  private OperationCrypto(Cipher cipher) {
    this.cipher = cipher;
  }

  public void encrypt(WaveletOperation op, long rev, final Function<WaveletOperation, Void> callback) {
    CipherWrapper wrapper = new CipherWrapper();
    DocOp dop = wrapper.wop2Dop(op);
    if (dop == null) {
      callback.apply(op);
    } else {
      encrypt(dop, rev, (DocOp encryptedDop) -> callback.apply(wrapper.dop2Wop(encryptedDop)));
    }
  }

  public void decrypt(WaveletOperation op, final Function<WaveletOperation, Void> callback) {
    CipherWrapper wrapper = new CipherWrapper();
    DocOp dop = wrapper.wop2Dop(op);
    if (dop == null) {
      callback.apply(op);
    } else {
      decrypt(dop, (DocOp decryptedDop) -> callback.apply(wrapper.dop2Wop(decryptedDop)));
    }
  }

  private class CipherWrapper {
    private WaveletOperationContext context;
    private String blipId;

    public CipherWrapper() {
    }

    public DocOp wop2Dop(WaveletOperation op) {
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

    public WaveletOperation dop2Wop(DocOp dop) {
      BlipOperation bop = new BlipContentOperation(context, dop);
      return new WaveletBlipOperation(blipId, bop);
    }
  }

  private static String ofuscate(String text) {
    char[] chars = text.toCharArray();
    for (int i = 0; i < text.length(); i++) {
      chars[i] = '*';
    }
    return new String(chars);
  }

  private DocOp annotate(long rev, int count, String text) {
    String annotTag = "cipher/" + String.valueOf(rev);
    AnnotationBoundaryMap boundary = new AnnotationBoundaryMapBuilder().change(annotTag, null, text).build();
    AnnotationBoundaryMap end = new AnnotationBoundaryMapBuilder().end(annotTag).build();
    return new DocOpBuilder().annotationBoundary(boundary).retain(count).annotationBoundary(end).build();
  }

  public void encrypt(DocOp dop, long rev, final Function<DocOp, Void> callback) {
    CryptoCursor cursor = new CryptoCursor(rev, cipher) {
      @Override
      public void characters(String chars) {
        result += chars;
        builder.characters(ofuscate(chars));
        count += chars.length();
      }

      @Override
      public void deleteCharacters(String chars) {
        builder.deleteCharacters(ofuscate(chars));
      }
    };
    dop.apply(cursor);
    dop = cursor.getDocOp();
    String text = cursor.getCollectedText();
    int count = cursor.getCount();
    if (!text.isEmpty()) {
      DocOpCollector collector = new DocOpCollector();
      collector.add(dop);
      cipher.encrypt(text, "", new Callback<String, Object>() {
        @Override
        public void onSuccess(String result) {
          collector.add(annotate(rev, count, result));
          callback.apply(collector.composeAll());
        }

        @Override
        public void onFailure(Object reason) {

        }
      });
    } else {
      callback.apply(dop);
    }
  }

  public void decrypt(DocOp dop, final Function<DocOp, Void> callback) {

    if (dop.getType(0) != DocOpComponentType.ANNOTATION_BOUNDARY) {
      callback.apply(dop);
      return;
    }

    AnnotationBoundaryMap map = dop.getAnnotationBoundary(0);
    int i = 0;

    while (i < map.changeSize() && !map.getChangeKey(i).startsWith("cipher/")) {
      i++;
    }
    if (i >= map.changeSize()) {
      callback.apply(dop);
      return;
    }

    String ciphertext = map.getNewValue(i);

    cipher.decrypt(ciphertext, new Callback<String, Object>() {
      @Override
      public void onSuccess(String decrypted) {

        CryptoCursor cursor = new CryptoCursor(cipher) {

          String text = decrypted;

          @Override
          public void characters(String chars) {
            String chunk = text.substring(0, chars.length());
            builder.characters(chunk);
            text = text.substring(chars.length());
            count += chars.length();
          }
        };
        dop.apply(cursor);
        callback.apply(cursor.getDocOp());
      }

      @Override
      public void onFailure(Object reason) {

      }
    });
  }
}
