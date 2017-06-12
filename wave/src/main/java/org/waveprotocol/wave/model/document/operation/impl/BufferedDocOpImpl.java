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

import java.util.ArrayList;

import org.waveprotocol.wave.client.concurrencycontrol.StaticChannelBinder;
import org.waveprotocol.wave.model.document.operation.AnnotationBoundaryMap;
import org.waveprotocol.wave.model.document.operation.AnnotationBoundaryMapBuilder;
import org.waveprotocol.wave.model.document.operation.Attributes;
import org.waveprotocol.wave.model.document.operation.AttributesUpdate;
import org.waveprotocol.wave.model.document.operation.DocOp;
import org.waveprotocol.wave.model.document.operation.DocOpComponentType;
import org.waveprotocol.wave.model.document.operation.DocOpCursor;
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

  /**
   * @return true if the op is known to be well-formed.
   *   false implies nothing in particular.
   */
  public boolean isKnownToBeWellFormed() {
    return knownToBeWellFormed;
  }
  
  private static String ofuscate(String text) {
      char[] chars = text.toCharArray();
      for (int i=0; i < text.length(); i++) {
          chars[i] = '*';
      }
      return new String(chars);
  }
  
  public DocOp decrypt() {
	DocOpComponent[] components = new DocOpComponent[this.components.length];
	ArrayList<String> cipher = new ArrayList<String>();
	for (int i = 0; i < this.size(); i++) {
      if (this.getType(i) == DocOpComponentType.ANNOTATION_BOUNDARY) {
        AnnotationBoundaryMap boundary = this.getAnnotationBoundary(i);
        for (int j = 0; j < boundary.changeSize(); j++) {
          if (boundary.getChangeKey(j).equals("cipher")) {
            cipher.add(boundary.getNewValue(j));
          }
        }
        for (int j = 0; j < boundary.endSize(); j++) {
          if (boundary.getEndKey(j).equals("cipher")) {
            cipher.remove(cipher.size()-1);
          }
        }
      }
      if (this.getType(i) == DocOpComponentType.CHARACTERS && cipher.size() > 0) {
        components[i] = new OperationComponents.Characters(cipher.get(cipher.size()-1));
      } else {
        components[i] = this.components[i];
      }
	}
	return new BufferedDocOpImpl(components);
  }
  
  public DocOp encrypt() {
    AnnotationBoundaryMap boundary;
	int length = this.components.length;
	int j = 0;
	for (int i = 0; i < this.size(); i++) {
	  if (this.getType(i) == DocOpComponentType.CHARACTERS) {
        length += 2; // Each insert characters needs annotation start and annotation end components
	  }
	}
    DocOpComponent[] components = new DocOpComponent[length];
    for (int i = 0; i < this.size(); i++) {
      if (this.getType(i) == DocOpComponentType.CHARACTERS) {
   	    boundary = new AnnotationBoundaryMapBuilder().change("cipher", null, this.getCharactersString(i)).build();
        components[j++] = new AnnotationBoundary(boundary);
    	String ciphertext = BufferedDocOpImpl.ofuscate(this.getCharactersString(i));
        components[j++] = new OperationComponents.Characters(ciphertext);
        boundary = new AnnotationBoundaryMapBuilder().end("cipher").build();
        components[j++] = new AnnotationBoundary(boundary);
      /*} else if (this.getType(i) == DocOpComponentType.DELETE_CHARACTERS) {
        String ciphertext = BufferedDocOpImpl.applyCaesar(this.getDeleteCharactersString(i), shift);
        components[j++] = new OperationComponents.DeleteCharacters(ciphertext);*/
      } else {
        components[j++] = this.components[i];
      }
    }
    return new BufferedDocOpImpl(components);
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
