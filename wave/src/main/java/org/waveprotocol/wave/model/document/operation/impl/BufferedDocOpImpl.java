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

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Arrays;

import org.waveprotocol.wave.client.concurrencycontrol.StaticChannelBinder;
import org.waveprotocol.wave.model.document.operation.AnnotationBoundaryMap;
import org.waveprotocol.wave.model.document.operation.AnnotationBoundaryMapBuilder;
import org.waveprotocol.wave.model.document.operation.Attributes;
import org.waveprotocol.wave.model.document.operation.AttributesUpdate;
import org.waveprotocol.wave.model.document.operation.DocOp;
import org.waveprotocol.wave.model.document.operation.DocOpComponentType;
import org.waveprotocol.wave.model.document.operation.DocOpCursor;
import org.waveprotocol.wave.model.document.operation.algorithm.Composer;
import org.waveprotocol.wave.model.document.operation.algorithm.DocOpInverter;
import org.waveprotocol.wave.model.document.operation.automaton.DocOpAutomaton.ViolationCollector;
import org.waveprotocol.wave.model.document.operation.impl.OperationComponents.AnnotationBoundary;
import org.waveprotocol.wave.model.document.operation.impl.OperationComponents.Characters;
import org.waveprotocol.wave.model.document.operation.impl.OperationComponents.DeleteCharacters;
import org.waveprotocol.wave.model.document.operation.impl.OperationComponents.DeleteElementStart;
import org.waveprotocol.wave.model.document.operation.impl.OperationComponents.DocOpComponent;
import org.waveprotocol.wave.model.document.operation.impl.OperationComponents.ElementStart;
import org.waveprotocol.wave.model.document.operation.impl.OperationComponents.ReplaceAttributes;
import org.waveprotocol.wave.model.document.operation.impl.OperationComponents.Retain;
import org.waveprotocol.wave.model.document.operation.impl.OperationComponents.UpdateAttributes;
import org.waveprotocol.wave.model.document.util.DocOpScrub;
import org.waveprotocol.wave.model.operation.OperationException;
import org.waveprotocol.wave.model.util.Preconditions;

import com.google.gwt.core.client.GWT;

/**
 * Package-private. Use one of the following to construct a buffered doc op:
 * <ul>
 * <li>{@link DocOpBuilder}</li>
 * <li>{@link DocInitializationBuilder}</li>
 * <li>{@link DocOpBuffer}</li>
 * <li>{@link DocInitializationBuffer}</li>
 * </ul>
 */
public final class BufferedDocOpImpl implements DocOp {

  private boolean knownToBeWellFormed = false;

  /**
   * Creates a new buffered doc op, checking that it is well-formed.
   *
   * @param components
   *          op components
   */
  static BufferedDocOpImpl create(DocOpComponent[] components) {
    BufferedDocOpImpl op = createUnchecked(components);
    checkWellformedness(op);
    assert op.knownToBeWellFormed;
    return op;
  }

  /**
   * Creates a new buffered doc op without checking for well-formedness.
   *
   * @param components
   *          op components
   */
  static BufferedDocOpImpl createUnchecked(DocOpComponent[] components) {
    return new BufferedDocOpImpl(components);
  }

  /**
   * Checks that a buffered doc op is well-formed.
   *
   * @param value
   *          op to check
   * @throws IllegalStateException
   *           if the op is ill-formed
   */
  private static void checkWellformedness(DocOp value) {
    if (!DocOpValidator.isWellFormed(null, value)) {
      // Check again, collecting violations this time.
      ViolationCollector v = new ViolationCollector();
      DocOpValidator.isWellFormed(v, value);
      Preconditions.illegalState("Attempt to build ill-formed operation (" + v + "): " + value);
    }
  }

  private final DocOpComponent[] components;

  private BufferedDocOpImpl(DocOpComponent[] components) {
    this.components = components;
  }

  @Override
  public int size() {
    return components.length;
  }

  @Override
  public DocOpComponentType getType(int i) {
    return components[i].getType();
  }

  @Override
  public void applyComponent(int i, DocOpCursor cursor) {
    components[i].apply(cursor);
  }

  @Override
  public void apply(DocOpCursor cursor) {
    for (DocOpComponent component : components) {
      component.apply(cursor);
    }
  }

  @Override
  public String getCharactersString(int i) {
    check(i, DocOpComponentType.CHARACTERS);
    return ((Characters) components[i]).string;
  }

  @Override
  public String getDeleteCharactersString(int i) {
    check(i, DocOpComponentType.DELETE_CHARACTERS);
    return ((DeleteCharacters) components[i]).string;
  }

  @Override
  public Attributes getReplaceAttributesNewAttributes(int i) {
    check(i, DocOpComponentType.REPLACE_ATTRIBUTES);
    return ((ReplaceAttributes) components[i]).newAttrs;
  }

  @Override
  public Attributes getReplaceAttributesOldAttributes(int i) {
    check(i, DocOpComponentType.REPLACE_ATTRIBUTES);
    return ((ReplaceAttributes) components[i]).oldAttrs;
  }

  @Override
  public int getRetainItemCount(int i) {
    check(i, DocOpComponentType.RETAIN);
    return ((Retain) components[i]).itemCount;
  }

  @Override
  public AnnotationBoundaryMap getAnnotationBoundary(int i) {
    check(i, DocOpComponentType.ANNOTATION_BOUNDARY);
    return ((AnnotationBoundary) components[i]).boundary;
  }

  @Override
  public Attributes getDeleteElementStartAttributes(int i) {
    check(i, DocOpComponentType.DELETE_ELEMENT_START);
    return ((DeleteElementStart) components[i]).attrs;
  }

  @Override
  public String getDeleteElementStartTag(int i) {
    check(i, DocOpComponentType.DELETE_ELEMENT_START);
    return ((DeleteElementStart) components[i]).type;
  }

  @Override
  public Attributes getElementStartAttributes(int i) {
    check(i, DocOpComponentType.ELEMENT_START);
    return ((ElementStart) components[i]).attrs;
  }

  @Override
  public String getElementStartTag(int i) {
    check(i, DocOpComponentType.ELEMENT_START);
    return ((ElementStart) components[i]).type;
  }

  @Override
  public AttributesUpdate getUpdateAttributesUpdate(int i) {
    check(i, DocOpComponentType.UPDATE_ATTRIBUTES);
    return ((UpdateAttributes) components[i]).update;
  }

  /**
   * @return true if the op is known to be well-formed. false implies nothing in
   *         particular.
   */
  public boolean isKnownToBeWellFormed() {
    return knownToBeWellFormed;
  }

  private static String ofuscate(String text) {
    char[] chars = text.toCharArray();
    for (int i = 0; i < text.length(); i++) {
      chars[i] = '*';
    }
    return new String(chars);
  }

  private int cursorInc(int i) {
    DocOpComponentType type = this.getType(i);
    if (type == DocOpComponentType.RETAIN) {
      return this.getRetainItemCount(i);
    } else if (type == DocOpComponentType.CHARACTERS) {
      return this.getCharactersString(i).length();
    } else if (type == DocOpComponentType.ELEMENT_START || type == DocOpComponentType.ELEMENT_END) {
      return 1;
    } else {
      return 0;
    }
  }

  public DocOp decryptSnapshot() throws OperationException {
    DocOpComponent[] components = new DocOpComponent[this.components.length];
    ArrayDeque<String> insertions = new ArrayDeque<String>();
    for (int i = 0; i < this.size(); i++) {
      if (this.getType(i) == DocOpComponentType.ANNOTATION_BOUNDARY) {
        AnnotationBoundaryMap boundary = this.getAnnotationBoundary(i);
        for (int j = 0; j < boundary.endSize(); j++) {
          if (boundary.getEndKey(j).startsWith("cipher/add/")) {
            if (!insertions.pop().equals("")) {
              throw new OperationException();
            }
          }
        }
        for (int j = 0; j < boundary.changeSize(); j++) {
          if (boundary.getChangeKey(j).startsWith("cipher/add/")) {
            insertions.push(boundary.getNewValue(j));
          }
        }

      }
      if (this.getType(i) == DocOpComponentType.CHARACTERS) {
        String text = insertions.peek().substring(0, this.getCharactersString(i).length());
        components[i] = new OperationComponents.Characters(text);
        insertions.push(insertions.pop().substring(text.length()));
      } else {
        components[i] = this.components[i];
      }
    }
    return new BufferedDocOpImpl(components);
  }

  public DocOp decrypt() {
    DocOpComponent[] components = new DocOpComponent[this.components.length];
    ArrayList<String> insertions = new ArrayList<String>();
    ArrayList<String> deletions = new ArrayList<String>();
    for (int i = 0; i < this.size(); i++) {
      if (this.getType(i) == DocOpComponentType.ANNOTATION_BOUNDARY) {
        AnnotationBoundaryMap boundary = this.getAnnotationBoundary(i);
        for (int j = 0; j < boundary.changeSize(); j++) {
          if (boundary.getChangeKey(j).startsWith("cipher/add/")) {
            insertions.add(boundary.getNewValue(j));
          }
          if (boundary.getChangeKey(j).startsWith("cipher/del/")) {
            deletions.add(boundary.getNewValue(j));
          }
        }
        /*for (int j = 0; j < boundary.endSize(); j++) {
          if (boundary.getEndKey(j).startsWith("cipher/add/")) {
            insertions.remove(insertions.size() - 1);
          }
          if (boundary.getEndKey(j).startsWith("cipher/del/")) {
            insertions.remove(deletions.size() - 1);
          }
        }*/
        
      }
      if (this.getType(i) == DocOpComponentType.CHARACTERS && insertions.size() > 0) {
        components[i] = new OperationComponents.Characters(insertions.get(insertions.size() - 1));
      } else if (this.getType(i) == DocOpComponentType.DELETE_CHARACTERS && deletions.size() > 0) {
        components[i] = new OperationComponents.DeleteCharacters(deletions.get(deletions.size() - 1));
      } else {
        components[i] = this.components[i];
      }
    }
    return new BufferedDocOpImpl(components);
  }

  /*public DocOp encrypt(long rev) {
    String revStr = String.valueOf(rev);
    AnnotationBoundary lastAnnotation = null;
    boolean omitNextBoundary = false;

    ArrayList<DocOpComponent> components = new ArrayList<DocOpComponent>();
    for (int i = 0; i < this.size(); i++) {
      boolean inserting = this.getType(i) == DocOpComponentType.CHARACTERS;
      boolean deleting = this.getType(i) == DocOpComponentType.DELETE_CHARACTERS;
      if (inserting || deleting) {

        String annotationTag = inserting ? "cipher/add/" : "cipher/del/";
        String text = inserting ? this.getCharactersString(i) : this.getDeleteCharactersString(i);

        AnnotationBoundaryMapBuilder builder = new AnnotationBoundaryMapBuilder();
        if (lastAnnotation != null) {
          lastAnnotation = (AnnotationBoundary) components.get(components.size() - 1);
          builder = builder.boundary(lastAnnotation.boundary);
          components.remove(components.size() - 1);
        }
        AnnotationBoundaryMap boundary = builder.change(annotationTag + revStr, null, text).build();
        components.add(new AnnotationBoundary(boundary));

        String ofusc = BufferedDocOpImpl.ofuscate(text);

        if (inserting) {
          components.add(new OperationComponents.Characters(ofusc));
        } else {
          components.add(new OperationComponents.DeleteCharacters(ofusc));
        }

        builder = new AnnotationBoundaryMapBuilder();
        if (i + 1 < this.size() && this.getType(i + 1) == DocOpComponentType.ANNOTATION_BOUNDARY) {
          builder = builder.boundary(this.getAnnotationBoundary(i + 1));
          omitNextBoundary = true;
        }
        boundary = builder.end(annotationTag + revStr).build();
        components.add(new AnnotationBoundary(boundary));
      } else if (omitNextBoundary) {
        omitNextBoundary = false;
        lastAnnotation = null;
      } else {
        components.add(this.components[i]);
        lastAnnotation = null;
      }
    }
    return new BufferedDocOpImpl(components.toArray(new DocOpComponent[0]));
  }*/
  
  public DocOp encrypt(long rev) {
    BufferedDocOpImpl withIns = (BufferedDocOpImpl) encrypt(rev, false);
    withIns = (BufferedDocOpImpl) DocOpInverter.invert(withIns);
    DocOp withDel = withIns.encrypt(rev, true);
    withDel = DocOpInverter.invert(withDel);
    return withDel;
  }

  private DocOp encrypt(long rev, boolean del) {
    String revStr = String.valueOf(rev);
    ArrayList<DocOpComponent> insertOpComponents = new ArrayList<DocOpComponent>();
    //ArrayList<DocOpComponent> deleteOpComponents = new ArrayList<DocOpComponent>();
    int insertCursor = 0;
    //int deleteCursor = 0;
    for (int i = 0; i < this.size(); i++) {
      boolean inserting = this.getType(i) == DocOpComponentType.CHARACTERS;
      //boolean deleting = this.getType(i) == DocOpComponentType.DELETE_CHARACTERS;
      if (inserting) {
        String annotationTag = del ? "cipher/del/": "cipher/add/";
        String text = this.getCharactersString(i);
        String ofusc = BufferedDocOpImpl.ofuscate(text);
        if (insertCursor > 0) {
          insertOpComponents.add(new Retain(insertCursor));
        }
        String oldValue = !del? null : text;
        String newValue = !del? text : null;
        AnnotationBoundaryMap boundary = new AnnotationBoundaryMapBuilder().change(annotationTag + revStr, oldValue, newValue).build();
        insertOpComponents.add(new AnnotationBoundary(boundary));
        
        insertOpComponents.add(new DeleteCharacters(text));
        insertOpComponents.add(new Characters(ofusc));
        
        boundary = new AnnotationBoundaryMapBuilder().end(annotationTag + revStr).build();
        insertOpComponents.add(new AnnotationBoundary(boundary));
        insertCursor = 0;
      /*} else if (deleting) {
        String annotationTag = "cipher/del/";
        String text = this.getDeleteCharactersString(i);
        String ofusc = BufferedDocOpImpl.ofuscate(text);
        if (deleteCursor > 0) {
          deleteOpComponents.add(new Retain(deleteCursor));
        }
        
        deleteOpComponents.add(new Characters(text));

        AnnotationBoundaryMap boundary = new AnnotationBoundaryMapBuilder().change(annotationTag + revStr, null, text).build();
        deleteOpComponents.add(new AnnotationBoundary(boundary));
        
        deleteOpComponents.add(new DeleteCharacters(ofusc));
        
        boundary = new AnnotationBoundaryMapBuilder().end(annotationTag + revStr).build();
        deleteOpComponents.add(new AnnotationBoundary(boundary));
        deleteCursor = 0;*/
      }
      insertCursor += !inserting ? cursorInc(i) : 0;
      //deleteCursor += !deleting ? cursorInc(i) : 0;
    }
    if (insertCursor > 0) {
      insertOpComponents.add(new Retain(insertCursor));
    }
    /*if (deleteCursor > 0) {
      deleteOpComponents.add(new Retain(deleteCursor));
    }*/
    //DocOp deleteOp = new BufferedDocOpImpl(deleteOpComponents.toArray(new DocOpComponent[0]));
    DocOp insertOp = new BufferedDocOpImpl(insertOpComponents.toArray(new DocOpComponent[0]));
    
    try {
      //deleteOp = Composer.compose(this, deleteOp);
      return Composer.compose(this, insertOp);
    } catch (OperationException e) {
      // TODO Auto-generated catch block
      e.printStackTrace();
    }
    return null;
  }

  /**
   * Should only be called by the validator. Caches the knowledge of
   * well-formedness.
   */
  void markWellFormed() {
    knownToBeWellFormed = true;
  }

  private void check(int i, DocOpComponentType expectedType) {
    DocOpComponentType actualType = components[i].getType();
    if (actualType != expectedType) {
      Preconditions
          .illegalArgument("Component " + i + " is not of type ' " + expectedType + "', it is '" + actualType + "'");
    }
  }

  @Override
  public String toString() {
    return "Buffered@" + Integer.toHexString(System.identityHashCode(this)) + "["
        + DocOpUtil.toConciseString(DocOpScrub.maybeScrub(this)) + "]";
  }
}
