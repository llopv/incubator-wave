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

package org.waveprotocol.box.server.crypto;

import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.SortedMap;
import java.util.TreeMap;

import org.waveprotocol.wave.model.document.operation.AnnotationBoundaryMap;
import org.waveprotocol.wave.model.document.operation.Attributes;
import org.waveprotocol.wave.model.document.operation.AttributesUpdate;
import org.waveprotocol.wave.model.document.operation.DocOp;
import org.waveprotocol.wave.model.document.operation.DocOpCursor;
import org.waveprotocol.wave.model.operation.wave.BlipContentOperation;
import org.waveprotocol.wave.model.operation.wave.WaveletBlipOperation;
import org.waveprotocol.wave.model.operation.wave.WaveletOperation;

import com.google.gson.Gson;
import com.google.gson.JsonElement;
import com.google.gson.JsonParser;
import com.google.gson.stream.JsonReader;
import com.google.gson.stream.JsonWriter;
import com.google.gwt.thirdparty.guava.common.base.Preconditions;

/**
 * Allows to replay WaveletOperations in order to know which pieces of the
 * resulting snapshot correspond to which ciphertext.
 *
 * @author llop@protonmail.com (David Llop)
 */
public class RecoverSnapshot {

  private HashMap<String, PieceTree> trees = new HashMap<String, PieceTree>();

  /**
   * Represents a piece of the original plaintext. It is a black boxed string:
   * its contents are unknown, but it stores the length and offset with respect
   * to the original string.
   */
  private class Piece {
    private int opId;
    private int len;
    private int offset = 0;

    public Piece(int cipherId, int len, int offset) {
      this.opId = cipherId;
      this.len = len;
      this.offset = offset;
    }

    public Piece substring(int beginIndex) {
      return new Piece(opId, len - beginIndex, offset + beginIndex);
    }

    public Piece substring(int beginIndex, int endIndex) {
      return new Piece(opId, endIndex - beginIndex, offset + beginIndex);
    }

    public String reconstitute(Map<Integer, String> texts) {
      return texts.get(opId).substring(offset, offset + len);
    }
  }

  /**
   * Represents the state of a document. Monitors which part of it corresponds
   * to which ciphertext.
   */
  private class PieceTree {

    /**
     * Stores cipher/* annotation ids.
     */
    private List<String> opIds = new ArrayList<String>();
    /**
     * Maps opIds with ciphertexts.
     */
    private Map<Integer, String> ciphertexts = new HashMap<Integer, String>();
    /**
     * Self-balanced search tree of pieces.
     */
    TreeMap<Integer, Piece> pieces = new TreeMap<Integer, Piece>();

    private int registerCiphertext(String string, String chars) {
      if (opIds.indexOf(string) < 0) {
        opIds.add(string);
      }
      int opId = opIds.indexOf(string);
      ciphertexts.put(opId, chars);
      return opId;
    }

    public void replay(DocOp dop) {
      dop.apply(new DocOpCursor() {

        /**
         * Current cursor position.
         */
        int cursor = 0;
        /**
         * Current cipher/* annotation id.
         */
        int opId;
        /**
         * Last inserted piece offset.
         */
        int offset = 0;

        /**
         * From the current cursor position, it updates the position of all
         * pieces onwards by an offset. If the cursor is in the middle of a
         * piece, the piece is split.
         *
         * @param len
         */
        private void expand(int len) {
          SortedMap<Integer, Piece> pre = pieces.headMap(cursor);
          if (!pre.isEmpty()) {
            int prevKey = pre.lastKey();
            Piece prev = pieces.get(prevKey);
            if (prevKey + prev.len > cursor) {
              Map<Integer, Piece> s = split(prevKey, cursor);
              pieces.remove(prevKey);
              for (int key : s.keySet()) {
                pieces.put(key, s.get(key));
              }
            }
          }

          List<Integer> keys = new ArrayList<Integer>(pieces.keySet());
          if (len > 0) {
            java.util.Collections.sort(keys, java.util.Collections.reverseOrder());
          } else {
            java.util.Collections.sort(keys);
          }
          for (int key : keys) {
            if (key >= cursor) {
              Piece p = pieces.get(key);
              pieces.remove(key);
              pieces.put(key + len, p);
            }
          }
        }

        /**
         * Splits a piece in two
         * 
         * @param key
         *          Piece to split
         * @param p
         *          Cutting position
         * @return A map with two pieces
         */
        private Map<Integer, Piece> split(int key, int p) {
          Preconditions.checkArgument(key <= p);
          Piece piece = pieces.get(key);
          Map<Integer, Piece> s = new HashMap<Integer, Piece>();
          s.put(key, piece.substring(0, p - key));
          s.put(p, piece.substring(p - key));
          return s;
        }

        @Override
        public void annotationBoundary(AnnotationBoundaryMap map) {
          // Extracts the ciphertext from the annotation and registers it
          for (int i = 0; i < map.changeSize(); i++) {
            if (map.getChangeKey(i).startsWith("cipher/")) {
              opId = registerCiphertext(map.getChangeKey(i), map.getNewValue(i));
            }
          }
        }

        @Override
        public void characters(String chars) {
          // Inserts a new piece in the tree
          Piece piece = new Piece(opId, chars.length(), offset);
          int len = piece.len;
          expand(len);
          pieces.put(cursor, piece);
          cursor += len;
          offset += len;
        }

        @Override
        public void elementStart(String type, Attributes attrs) {
          expand(1);
          cursor += 1;
        }

        @Override
        public void elementEnd() {
          expand(1);
          cursor += 1;
        }

        @Override
        public void retain(int itemCount) {
          cursor += itemCount;
        }

        @Override
        public void deleteCharacters(String chars) {
          int firstPiece = pieces.floorKey(cursor);
          int to = cursor + chars.length();
          int lastPiece;

          // Split the first piece if needed
          if (firstPiece < cursor) {
            Map<Integer, Piece> s = split(firstPiece, cursor);
            pieces.remove(firstPiece);
            pieces.put(firstPiece, s.get(firstPiece));
            pieces.put(cursor, s.get(cursor));
            firstPiece = cursor;
          }

          lastPiece = pieces.floorKey(to);

          // Remove from firstPiece (included) to lastPiece (not included)
          pieces.subMap(firstPiece, lastPiece).clear();

          // Split last piece if needed
          if (lastPiece >= cursor) {
            Map<Integer, Piece> s = split(lastPiece, to);
            pieces.remove(lastPiece);
            if (s.get(to).len > 0) {
              pieces.put(to, s.get(to));
            }
          }
          expand(-chars.length());
        }

        @Override
        public void deleteElementStart(String type, Attributes attrs) {
          expand(-1);
        }

        @Override
        public void deleteElementEnd() {
          expand(-1);
        }

        @Override
        public void replaceAttributes(Attributes oldAttrs, Attributes newAttrs) {
        }

        @Override
        public void updateAttributes(AttributesUpdate attrUpdate) {
        }
      });
    }

    /**
     * Returns the ciphertexts of this blip in the form of index -> ciphertext.
     *
     * @return
     */
    public Map<Integer, String> getCiphertexts() {
      Map<Integer, String> ciphertexts = new HashMap<Integer, String>();
      for (Piece value : pieces.values()) {
        ciphertexts.put(value.opId, this.ciphertexts.get(value.opId));
      }
      return ciphertexts;
    }

    /**
     * Reconstitutes the content of a tree with the given plaintexts
     *
     * @param texts
     * @return
     */
    public String reconstitute(Map<Integer, String> texts) {
      String content = "";
      for (Piece value : pieces.values()) {
        content += value.reconstitute(texts);
      }
      return content;
    }
  }

  /**
   * Maps how PieceTrees are represented in JSON.
   */
  private class Json {
    public Map<Integer, String> ciphertexts;
    public Map<Integer, Map<String, Integer>> pieces;
  }

  /**
   * Processes a WaveletOperation by creating, deleting or modifying pieces in
   * the tree.
   *
   * @param op
   *          Operation
   */
  public void replay(WaveletOperation op) {
    if (op instanceof WaveletBlipOperation) {
      WaveletBlipOperation wop = (WaveletBlipOperation) op;
      if (wop.getBlipOp() instanceof BlipContentOperation) {
        String blipId = wop.getBlipId();
        BlipContentOperation bop = (BlipContentOperation) wop.getBlipOp();
        if (!trees.containsKey(blipId)) {
          trees.put(blipId, new PieceTree());
        }
        trees.get(blipId).replay(bop.getContentOp());
      }
    }
  }

  /**
   * Reconstitutes the contents of all the trees with the given plaintexts.
   *
   * @param texts
   * @return mapping between blip ids and blip contents
   */
  public Map<String, String> reconstitute(Map<String, Map<Integer, String>> texts) {
    Map<String, String> map = new HashMap<String, String>();
    for (Entry<String, PieceTree> s : trees.entrySet()) {
      String blipId = s.getKey();
      map.put(blipId, s.getValue().reconstitute(texts.get(blipId)));
    }
    return map;
  }

  /**
   * Returns the ciphertexts of all the blips in the form of blipId -> (index ->
   * ciphertext).
   *
   * @return
   */
  public Map<String, Map<Integer, String>> getCiphertexts() {
    Map<String, Map<Integer, String>> map = new HashMap<String, Map<Integer, String>>();
    for (Entry<String, PieceTree> s : trees.entrySet()) {
      String blipId = s.getKey();
      map.put(blipId, s.getValue().getCiphertexts());
    }
    return map;
  }

  public void toJson(JsonWriter writer) {
    try {
      writer.beginObject();
      for (Entry<String, PieceTree> s : trees.entrySet()) {
        String blipId = s.getKey();
        writer.name(blipId);
        writer.beginObject();
        writer.name("ciphertexts");
        writer.beginObject();
        for (Entry<Integer, String> c : s.getValue().getCiphertexts().entrySet()) {
          writer.name(String.valueOf(c.getKey()));
          writer.value(c.getValue());
        }
        writer.endObject();
        writer.name("pieces");
        writer.beginObject();
        for (Entry<Integer, Piece> entry : s.getValue().pieces.entrySet()) {
          writer.name(String.valueOf(entry.getKey()));
          Piece piece = entry.getValue();
          writer.beginObject();
          writer.name("opId").value(piece.opId);
          writer.name("len").value(piece.len);
          writer.name("offset").value(piece.offset);
          writer.endObject();
        }
        writer.endObject();
        writer.endObject();
      }
      writer.endObject();
    } catch (IOException e) {
      throw new Error(e);
    }
  }

  public RecoverSnapshot fromJson(JsonReader json) {
    Gson gson = new Gson();
    JsonParser parser = new JsonParser();
    trees = new HashMap<String, PieceTree>();

    for (Entry<String, JsonElement> blip : parser.parse(json).getAsJsonObject().entrySet()) {
      String blipId = blip.getKey();
      Json jsonDoc = gson.fromJson(blip.getValue(), Json.class);
      PieceTree tree = new PieceTree();
      tree.ciphertexts = jsonDoc.ciphertexts;
      for (Entry<Integer, Map<String, Integer>> entry : jsonDoc.pieces.entrySet()) {
        Map<String, Integer> attrs = entry.getValue();
        Piece piece = new Piece(attrs.get("opId"), attrs.get("len"), attrs.get("offset"));
        tree.pieces.put(entry.getKey(), piece);
      }
      trees.put(blipId, tree);
    }
    return this;
  }
}
