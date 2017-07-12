package org.waveprotocol.wave.model.document.operation.impl;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Function;

import org.waveprotocol.wave.model.document.operation.AnnotationBoundaryMap;
import org.waveprotocol.wave.model.document.operation.AnnotationBoundaryMapBuilder;
import org.waveprotocol.wave.model.document.operation.Attributes;
import org.waveprotocol.wave.model.document.operation.AttributesUpdate;
import org.waveprotocol.wave.model.document.operation.DocOp;
import org.waveprotocol.wave.model.document.operation.DocOpCursor;
import org.waveprotocol.wave.model.document.operation.algorithm.DocOpCollector;
import org.waveprotocol.wave.model.operation.wave.BlipContentOperation;
import org.waveprotocol.wave.model.operation.wave.WaveletBlipOperation;
import org.waveprotocol.wave.model.operation.wave.WaveletOperation;

public class OperationCrypto {

  private class CryptoCursor implements DocOpCursor {

    protected DocOpBuilder builder = new DocOpBuilder();
    protected String encryptBuffer = "";
    protected String decryptBuffer = "";
    protected long rev;
    protected int count = 0;

    public CryptoCursor() {
    }

    public CryptoCursor(long rev) {
      this.rev = rev;
    }

    private DocOp annotate(long rev, int count) {
      String annotTag = "cipher/" + String.valueOf(rev);
      AnnotationBoundaryMap boundary = new AnnotationBoundaryMapBuilder().change(annotTag, null, encryptBuffer).build();
      AnnotationBoundaryMap end = new AnnotationBoundaryMapBuilder().end(annotTag).build();
      return new DocOpBuilder().annotationBoundary(boundary).retain(count).annotationBoundary(end).build();
    }

    public DocOp getDocOp() {
      DocOp dop = builder.build();
      if (encryptBuffer.isEmpty()) {
        return dop;
      } else {
        DocOpCollector collector = new DocOpCollector();
        collector.add(dop);
        collector.add(annotate(rev, count));
        return collector.composeAll();
      }
    }

    @Override
    public void annotationBoundary(AnnotationBoundaryMap map) {
      List<String> endKeys = new ArrayList<String>();
      List<String> changeKeys = new ArrayList<String>();
      List<String> changeOldValues = new ArrayList<String>();
      List<String> changeNewValues = new ArrayList<String>();
      for (int i = 0; i < map.endSize(); ++i) {
        String key = map.getEndKey(i);
        endKeys.add(key);
      }
      for (int i = 0; i < map.changeSize(); ++i) {
        String key = map.getChangeKey(i);
        String oldValue = map.getOldValue(i);
        String newValue = map.getNewValue(i);
        changeKeys.add(key);
        changeOldValues.add(oldValue);
        changeNewValues.add(newValue);
      }
      builder.annotationBoundary(
          new AnnotationBoundaryMapImpl(endKeys.toArray(new String[0]), changeKeys.toArray(new String[0]),
              changeOldValues.toArray(new String[0]), changeNewValues.toArray(new String[0])));
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

  public OperationCrypto() {
  }

  public WaveletOperation encrypt(WaveletOperation op, long rev) {
    return cipherWrapper(op, (DocOp dop) -> encrypt(dop, rev));
  }

  public WaveletOperation decrypt(WaveletOperation op) {
    return cipherWrapper(op, (DocOp dop) -> decrypt(dop));
  }

  private WaveletOperation cipherWrapper(WaveletOperation op, Function<DocOp, DocOp> function) {
    if (op instanceof WaveletBlipOperation) {
      WaveletBlipOperation wop = (WaveletBlipOperation) op;
      if (wop.getBlipOp() instanceof BlipContentOperation) {
        BlipContentOperation bop = (BlipContentOperation) wop.getBlipOp();
        DocOp dop = bop.getContentOp();
        dop = function.apply(dop);
        bop = new BlipContentOperation(bop.getContext(), dop);
        op = new WaveletBlipOperation(wop.getBlipId(), bop);
      }
    }
    return op;
  }

  private static String ofuscate(String text) {
    char[] chars = text.toCharArray();
    for (int i = 0; i < text.length(); i++) {
      chars[i] = '*';
    }
    return new String(chars);
  }

  public DocOp encrypt(DocOp dop, long rev) {
    CryptoCursor cursor = new CryptoCursor(rev) {
      @Override
      public void characters(String chars) {
        encryptBuffer += chars;
        builder.characters(ofuscate(chars));
        count += chars.length();
      }

      @Override
      public void deleteCharacters(String chars) {
        builder.deleteCharacters(ofuscate(chars));
      }
    };
    dop.apply(cursor);
    return cursor.getDocOp();
  }

  public DocOp decrypt(DocOp dop) {
    CryptoCursor cursor = new CryptoCursor() {
      @Override
      public void annotationBoundary(AnnotationBoundaryMap map) {
        for (int j = 0; j < map.changeSize(); j++) {
          if (map.getChangeKey(j).startsWith("cipher/")) {
            decryptBuffer += map.getNewValue(j);
          }
        }
        builder.annotationBoundary(map);
      }

      @Override
      public void characters(String chars) {
        String text = decryptBuffer.substring(0, chars.length());
        builder.characters(text);
        encryptBuffer = decryptBuffer.substring(chars.length());
        count += chars.length();
      }
    };
    dop.apply(cursor);
    return cursor.getDocOp();
  }
}
