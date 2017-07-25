package org.waveprotocol.box.server.crypto;

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

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;

public class RecoverSnapshot {
  
  private interface Piece {
    public int length();
    public String getChars(List<String> ciphertexts);

  }
  
  private class TextPiece implements Piece {
    private int rev;
    private int len;
    private int offset = 0;
    
    public TextPiece (int rev, int len, int offset) {
      this.rev = rev;
      this.len = len;
      this.offset = offset;
    }
    
    public TextPiece(int rev, int len) {
      this.rev = rev;
      this.len = len;
    }

    public TextPiece substring(int beginIndex) {
      return new TextPiece(rev, len - beginIndex, offset + beginIndex);
    }
    
    public TextPiece substring(int beginIndex, int endIndex) {
      return new TextPiece(rev, endIndex - beginIndex, offset + beginIndex);
    }
    
    public int length() {
      return len;
    }
    
    public String getChars(List<String> ciphertexts) {
      return ciphertexts.get(rev).substring(offset, offset + len);
    }
    public String toString() {
      return getChars(ciphertexts);
    }

  }
  
  private class XmlPiece implements Piece {
    public int length() {
      return 1;
    }
    public String getChars(List<String> ciphertexts) {
      return "";
    }
    public String toString() {
      return "</>";
    }
  }
  
  private int registerChars(String chars) {
    ciphertexts.add(chars);
    return ciphertexts.size() - 1;
  }
  
  private List<String> ciphertexts = new ArrayList<String>();
  TreeMap<Integer, Piece> pieceTree = new TreeMap<Integer, Piece>();

  public void replay(DocOp[] dops) {
    for (DocOp dop : dops) {
      dop.apply(new DocOpCursor() {

        int cursor = 0;
        int rev;
        
        private void insert(Piece piece) {
          SortedMap<Integer, Piece> pre = pieceTree.headMap(cursor);
          if (!pre.isEmpty()) {
            int prevKey = pre.lastKey();
            Piece prev = pieceTree.get(prevKey);
            if (prev instanceof XmlPiece) {
              
            } else if (prevKey + prev.length() > cursor) {
              Map<Integer, TextPiece> s = split(prevKey, (TextPiece) prev, cursor);
              pieceTree.remove(prevKey);
              for (int key : s.keySet()) {
                pieceTree.put(key, s.get(key));
              }
            }
          }
          reindex(cursor, piece.length());
          pieceTree.put(cursor, piece);
          cursor += piece.length();
        }
        
        private void removePiece() {
          int len = pieceTree.get(cursor).length();
          pieceTree.remove(cursor);
          reindex(cursor, -len);
        }

        @Override
        public void annotationBoundary(AnnotationBoundaryMap map) {
          for (int i = 0; i < map.changeSize(); i++) {
            if (map.getChangeKey(i).startsWith("cipher/")) {
              rev = registerChars(map.getNewValue(i));
            }
          }
        }

        @Override
        public void characters(String chars) {
          insert(new TextPiece(rev, chars.length()));
        }

        @Override
        public void elementStart(String type, Attributes attrs) {
          insert(new XmlPiece());
        }

        @Override
        public void elementEnd() {
          insert(new XmlPiece());
        }

        @Override
        public void retain(int itemCount) {
          cursor += itemCount;
        }

        @Override
        public void deleteCharacters(String chars) {
          int from = pieceTree.floorKey(cursor);
          int to = cursor + chars.length();
          SortedMap<Integer, Piece> pieces = pieceTree.subMap(from, true, to, true);
          if (!pieces.isEmpty()) {
            int prevKey = pieces.firstKey();
            int postKey = pieces.lastKey();
            if (prevKey < cursor) {
              Map<Integer, TextPiece> s = split(prevKey, (TextPiece) pieceTree.get(prevKey), cursor);
              pieceTree.remove(prevKey);
              pieceTree.put(prevKey, s.get(prevKey));
            } else if (prevKey != postKey) {
              pieceTree.remove(prevKey);
            }
            List<Integer> piecesToRemove = new ArrayList<Integer>();
            for (int key : pieces.keySet()) {
              if (key != prevKey && key != postKey) {
                piecesToRemove.add(key);
              }
            }
            for (int piece : piecesToRemove) {
              pieces.remove(piece);
            }
            if (postKey < to) {
              Map<Integer, TextPiece> s = split(postKey, (TextPiece) pieceTree.get(postKey), to);
              pieceTree.remove(postKey);
              pieceTree.put(to, s.get(to));
            }
          }
          reindex(cursor, -chars.length());
        }

        @Override
        public void deleteElementStart(String type, Attributes attrs) {
          removePiece();
        }

        @Override
        public void deleteElementEnd() {
          removePiece();
        }

        @Override
        public void replaceAttributes(Attributes oldAttrs, Attributes newAttrs) {
        }

        @Override
        public void updateAttributes(AttributesUpdate attrUpdate) {
        }
      });
    }
  }

  private Map<Integer, TextPiece> split(int key, TextPiece piece, int p) {
    Map<Integer, TextPiece> s = new HashMap<Integer, TextPiece>();
    s.put(key, piece.substring(0, p - key));
    s.put(p, piece.substring(p - key));
    return s;
  }
  
  private void reindex(int from, int offset) {
    TreeMap<Integer, Piece> newTree = new TreeMap<Integer, Piece>();
    for (int key : pieceTree.keySet()) {
      int newKey = key;
      if (key >= from) {
        newKey += offset;
      }
      newTree.put(newKey, pieceTree.get(key));
    }
    pieceTree.clear();
    pieceTree.putAll(newTree);
  }
  
  private class Serialized {
    public List<String> ciphertexts;
    public Map<Integer, Map<String, Integer>> pieces;
  }
  
  public String toJSON() {
    Serialized s = new Serialized();
    s.ciphertexts = this.ciphertexts;
    s.pieces = new HashMap<Integer, Map<String, Integer>>();
    for (Entry<Integer, Piece> entry: pieceTree.entrySet()) {
      Map<String, Integer> pieceAttrs = new HashMap<String, Integer>();
      Piece piece = entry.getValue();
      if (piece instanceof XmlPiece) {
        
      } else {
        TextPiece textPiece = (TextPiece) piece;
        pieceAttrs.put("rev", textPiece.rev);
        pieceAttrs.put("len", textPiece.len);
        pieceAttrs.put("offset", textPiece.offset);
      }
      s.pieces.put(entry.getKey(), pieceAttrs);
    }
    GsonBuilder builder = new GsonBuilder();
    builder.setPrettyPrinting().serializeNulls();
    Gson gson = builder.create();
    return gson.toJson(s);
  }
  
  public RecoverSnapshot fromJSON(String json) {
    GsonBuilder builder = new GsonBuilder();
    builder.setPrettyPrinting().serializeNulls();
    Gson gson = builder.create();
    Serialized s = gson.fromJson(json, Serialized.class);
    this.ciphertexts = s.ciphertexts;
    this.pieceTree = new TreeMap<Integer, Piece>();
    for (Entry<Integer, Map<String, Integer>> entry : s.pieces.entrySet()) {
      Piece piece;
      Map<String, Integer> pieceAttrs = entry.getValue();
      if (pieceAttrs.containsKey("rev") && pieceAttrs.containsKey("len") && pieceAttrs.containsKey("offset")) {
        piece = new TextPiece(pieceAttrs.get("rev"), pieceAttrs.get("len"), pieceAttrs.get("offset"));
      } else {
        piece = new XmlPiece();
      }
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
