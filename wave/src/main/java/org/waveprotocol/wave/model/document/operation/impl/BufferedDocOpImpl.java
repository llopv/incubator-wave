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

import org.waveprotocol.wave.model.document.operation.AnnotationBoundaryMap;
import org.waveprotocol.wave.model.document.operation.AnnotationBoundaryMapBuilder;
import org.waveprotocol.wave.model.document.operation.Attributes;
import org.waveprotocol.wave.model.document.operation.AttributesUpdate;
import org.waveprotocol.wave.model.document.operation.DocOp;
import org.waveprotocol.wave.model.document.operation.DocOpComponentType;
import org.waveprotocol.wave.model.document.operation.DocOpCursor;
import org.waveprotocol.wave.model.document.operation.algorithm.DocOpCollector;
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
import org.waveprotocol.wave.model.util.Preconditions;


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
   * @param components op components
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
   * @param components op components
   */
  static BufferedDocOpImpl createUnchecked(DocOpComponent[] components) {
    return new BufferedDocOpImpl(components);
  }

  /**
   * Checks that a buffered doc op is well-formed.
   *
   * @param value op to check
   * @throws IllegalStateException if the op is ill-formed
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

  public DocOp encrypt(long rev) {
    DocOpComponent[] components = new DocOpComponent[this.components.length];
    String buffer = "";
    int count = 0;
    boolean annotationNeeded = false;
    for (int i = 0; i < this.size(); i++) {
      boolean inserting = this.getType(i) == DocOpComponentType.CHARACTERS;
      boolean deleting = this.getType(i) == DocOpComponentType.DELETE_CHARACTERS;
      if (inserting || deleting) {
        String text = inserting ? this.getCharactersString(i) : this.getDeleteCharactersString(i);
        String ofusc = BufferedDocOpImpl.ofuscate(text);
        buffer += inserting ? text : "";
        if (inserting) {
          annotationNeeded = true;
          components[i] = new OperationComponents.Characters(ofusc);
        } else {
          components[i] = new OperationComponents.DeleteCharacters(ofusc);
        }
      } else {
        components[i] = this.components[i];
      }
      count += cursorIncrement(i);
    }
    
    if (annotationNeeded) {
      String annotTag = "cipher/" + String.valueOf(rev);
      DocOpComponent[] annotation = new DocOpComponent[3];
      AnnotationBoundaryMap boundary = new AnnotationBoundaryMapBuilder().change(annotTag, null, buffer).build();
      annotation[0] = new AnnotationBoundary(boundary);
      annotation[1] = new Retain(count);
      boundary = new AnnotationBoundaryMapBuilder().end(annotTag).build();
      annotation[2] = new AnnotationBoundary(boundary);
      
      DocOpCollector collector = new DocOpCollector();
      collector.add(new BufferedDocOpImpl(components));
      collector.add(new BufferedDocOpImpl(annotation));
      return collector.composeAll();
    } else {
      return new BufferedDocOpImpl(components);
    }
  }
  
  public DocOp decrypt() {
    DocOpComponent[] components = new DocOpComponent[this.components.length];
    String buffer = "";
    for (int i = 0; i < this.size(); i++) {
      if (this.getType(i) == DocOpComponentType.ANNOTATION_BOUNDARY) {
        AnnotationBoundaryMap boundary = this.getAnnotationBoundary(i);
        for (int j = 0; j < boundary.changeSize(); j++) {
          if (boundary.getChangeKey(j).startsWith("cipher/")) {
            buffer += boundary.getNewValue(j);
          }
        }
      }
      if (this.getType(i) == DocOpComponentType.CHARACTERS && buffer.length() > 0) {
        String text = buffer.substring(0, this.getCharactersString(i).length());
        components[i] = new OperationComponents.Characters(text);
        buffer = buffer.substring(this.getCharactersString(i).length());
      } else {
        components[i] = this.components[i];
      }
    }
    return new BufferedDocOpImpl(components);
  }
  
  private static String ofuscate(String text) {
    char[] chars = text.toCharArray();
    for (int i = 0; i < text.length(); i++) {
      chars[i] = '*';
    }
    return new String(chars);
  }

  private int cursorIncrement(int i) {
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

  /**
   * @return true if the op is known to be well-formed.
   *   false implies nothing in particular.
   */
  public boolean isKnownToBeWellFormed() {
    return knownToBeWellFormed;
  }

  /**
   * Should only be called by the validator.
   * Caches the knowledge of well-formedness.
   */
  void markWellFormed() {
    knownToBeWellFormed = true;
  }

  private void check(int i, DocOpComponentType expectedType) {
    DocOpComponentType actualType = components[i].getType();
    if (actualType != expectedType) {
      Preconditions.illegalArgument(
          "Component " + i + " is not of type ' " + expectedType + "', it is '" + actualType + "'");
    }
  }

  @Override
  public String toString() {
    return "Buffered@" + Integer.toHexString(System.identityHashCode(this)) +
        "[" + DocOpUtil.toConciseString(DocOpScrub.maybeScrub(this)) + "]";
  }
}
