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
import org.waveprotocol.wave.model.id.ModernIdSerialiser;
import org.waveprotocol.wave.model.id.WaveletId;
import org.waveprotocol.wave.model.operation.wave.BlipContentOperation;
import org.waveprotocol.wave.model.operation.wave.WaveletBlipOperation;
import org.waveprotocol.wave.model.operation.wave.WaveletOperation;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.stream.JsonReader;
import com.google.gson.stream.JsonWriter;

public class RecoverSnapshot {

  private List<String> keys = new ArrayList<String>();
  private Map<Integer, String> ciphertexts = new HashMap<Integer, String>();
  TreeMap<Integer, Piece> pieceTree = new TreeMap<Integer, Piece>();

  private class Piece {
    private int rev;
    private int len;
    private int offset = 0;

    public Piece(int rev, int len, int offset) {
      this.rev = rev;
      this.len = len;
      this.offset = offset;
    }

    public Piece substring(int beginIndex) {
      return new Piece(rev, len - beginIndex, offset + beginIndex);
    }

    public Piece substring(int beginIndex, int endIndex) {
      return new Piece(rev, endIndex - beginIndex, offset + beginIndex);
    }

    public String getChars(Map<Integer, String> ciphertexts) {
      return ciphertexts.get(rev).substring(offset, offset + len);
    }

    @Override
    public String toString() {
      return getChars(ciphertexts);
    }

  }

  private class RecoverSnapshotJson {
    public Map<Integer, String> ciphertexts;
    public Map<Integer, Map<String, Integer>> pieces;
  }

  private int registerChars(String string, String chars) {
    if (keys.indexOf(string) < 0) {
      keys.add(string);
    }
    int rev = keys.indexOf(string);
    ciphertexts.put(rev, chars);
    return rev;
  }


  public void replay(WaveletOperation op) {
    if (op instanceof WaveletBlipOperation) {
      WaveletBlipOperation wop = (WaveletBlipOperation) op;
      if (wop.getBlipOp() instanceof BlipContentOperation) {
        BlipContentOperation bop = (BlipContentOperation) wop.getBlipOp();
        replay(bop.getContentOp());
      }
    }
  }

  public void replay(DocOp dop) {
    dop.apply(new DocOpCursor() {

      int cursor = 0;
      int offset = 0;
      int rev;

      private void reindex(int len) {
        TreeMap<Integer, Piece> newTree = new TreeMap<Integer, Piece>();
        for (int key : pieceTree.keySet()) {
          int newKey = key;
          if (key >= cursor) {
            newKey += len;
          }
          newTree.put(newKey, pieceTree.get(key));
        }
        pieceTree.clear();
        pieceTree.putAll(newTree);
      }

      private void expand(int len) {
        SortedMap<Integer, Piece> pre = pieceTree.headMap(cursor);
        if (!pre.isEmpty()) {
          int prevKey = pre.lastKey();
          Piece prev = pieceTree.get(prevKey);
          if (prevKey + prev.len > cursor) {
            Map<Integer, Piece> s = split(prevKey, cursor);
            pieceTree.remove(prevKey);
            for (int key : s.keySet()) {
              pieceTree.put(key, s.get(key));
            }
          }
        }
        reindex(len);
      }

      private Map<Integer, Piece> split(int key, int p) {
        Piece piece = pieceTree.get(key);
        Map<Integer, Piece> s = new HashMap<Integer, Piece>();
        s.put(key, piece.substring(0, p - key));
        s.put(p, piece.substring(p - key));
        return s;
      }

      @Override
      public void annotationBoundary(AnnotationBoundaryMap map) {
        for (int i = 0; i < map.changeSize(); i++) {
          if (map.getChangeKey(i).startsWith("cipher/")) {
            rev = registerChars(map.getChangeKey(i), map.getNewValue(i));
          }
        }
      }

      @Override
      public void characters(String chars) {
        Piece piece = new Piece(rev, chars.length(), offset);
        int len = piece.len;
        expand(len);
        pieceTree.put(cursor, piece);
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
        int firstPiece = pieceTree.floorKey(cursor);
        int to = cursor + chars.length();
        int lastPiece;

        if (firstPiece < cursor) {
          Map<Integer, Piece> s = split(firstPiece, cursor);
          pieceTree.remove(firstPiece);
          pieceTree.put(firstPiece, s.get(firstPiece));
          pieceTree.put(cursor, s.get(cursor));
          firstPiece = cursor;
        }

        lastPiece = pieceTree.floorKey(to);

        // Remove [firstPiece, lastPiece)
        pieceTree.subMap(firstPiece, lastPiece).clear();

        if (lastPiece >= cursor) {
          Map<Integer, Piece> s = split(lastPiece, to);
          pieceTree.remove(lastPiece);
          if (s.get(to).len > 0) {
            pieceTree.put(to, s.get(to));
          }
        }
        reindex(-chars.length());
      }

      @Override
      public void deleteElementStart(String type, Attributes attrs) {
        reindex(-1);
      }

      @Override
      public void deleteElementEnd() {
        reindex(-1);
      }

      @Override
      public void replaceAttributes(Attributes oldAttrs, Attributes newAttrs) {
      }

      @Override
      public void updateAttributes(AttributesUpdate attrUpdate) {
      }
    });
  }

  public Map<Integer, String> getCiphertexts() {
    Map<Integer, String> ciphertexts = new HashMap<Integer, String>();
    for (Piece value : pieceTree.values()) {
      ciphertexts.put(value.rev, this.ciphertexts.get(value.rev));
    }
    return ciphertexts;
  }

  public void toJson(WaveletId waveletId, JsonWriter writer) {
    try {
      writer.beginObject();
      writer.name("waveletId").value(ModernIdSerialiser.INSTANCE.serialiseWaveletId(waveletId));
      writer.name("ciphertexts");
      writer.beginObject();
      for (Entry<Integer, String> c : getCiphertexts().entrySet()) {
        writer.name(String.valueOf(c.getKey()));
        writer.value(c.getValue());
      }
      writer.endObject();
      writer.name("pieces");
      writer.beginObject();
      for (Entry<Integer, Piece> entry : pieceTree.entrySet()) {
        writer.name(String.valueOf(entry.getKey()));
        Piece piece = entry.getValue();
        writer.beginObject();
        writer.name("rev").value(piece.rev);
        writer.name("len").value(piece.len);
        writer.name("offset").value(piece.offset);
        writer.endObject();
      }
      writer.endObject();
      writer.endObject();
    } catch (IOException e) {
      throw new Error(e);
    }
  }

  public RecoverSnapshot fromJson(JsonReader json) {
    GsonBuilder builder = new GsonBuilder();
    builder.serializeNulls();
    Gson gson = builder.create();
    RecoverSnapshotJson s = gson.fromJson(json, RecoverSnapshotJson.class);
    this.ciphertexts = s.ciphertexts;
    this.pieceTree = new TreeMap<Integer, Piece>();
    for (Entry<Integer, Map<String, Integer>> entry : s.pieces.entrySet()) {
      Map<String, Integer> pieceAttrs = entry.getValue();
      Piece piece = new Piece(pieceAttrs.get("rev"), pieceAttrs.get("len"), pieceAttrs.get("offset"));
      pieceTree.put(entry.getKey(), piece);
    }
    return this;
  }

  public String reconstitute() {
    String content = "";
    for (Piece value : pieceTree.values()) {
      content += value.getChars(ciphertexts);
    }
    return content;
  }
}
