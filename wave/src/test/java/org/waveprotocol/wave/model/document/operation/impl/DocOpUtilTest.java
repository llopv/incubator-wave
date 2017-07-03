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

import junit.framework.TestCase;

import java.util.Arrays;

import org.waveprotocol.wave.model.document.operation.Attributes;
import org.waveprotocol.wave.model.document.operation.DocOp;
import org.waveprotocol.wave.model.document.operation.algorithm.Composer;
import org.waveprotocol.wave.model.document.operation.algorithm.DocOpInverter;
import org.waveprotocol.wave.model.operation.OperationException;

public class DocOpUtilTest extends TestCase {

  public static final DocOp TEST_DOC1 = new DocOpBuffer() {
    {
      // Check the 3 things we need to escape
      AnnotationBoundaryMapImpl link1 = AnnotationBoundaryMapImpl.builder().initializationValues("link", "12?\"\\3")
          .build();
      AnnotationBoundaryMapImpl link2 = AnnotationBoundaryMapImpl.builder().initializationValues("link", "1").build();
      AnnotationBoundaryMapImpl ann = AnnotationBoundaryMapImpl.builder().initializationValues("x", "3", "y", "3")
          .build();
      AnnotationBoundaryMapImpl linkNull = AnnotationBoundaryMapImpl.builder().initializationValues("link", null)
          .build();
      AnnotationBoundaryMapImpl change = AnnotationBoundaryMapImpl.builder()
          .initializationValues("xa", null, "xb", "5", "xc", "6", "z", "4", "zz", "7").initializationEnd("x").build();
      AnnotationBoundaryMapImpl finish = AnnotationBoundaryMapImpl.builder()
          .initializationEnd("link", "xa", "xb", "xc", "y", "z", "zz").build();
      elementStart("p", new AttributesImpl());
      characters("hi ");
      characters("there");
      elementStart("q", new AttributesImpl("a", "1"));
      // Check things we need to escape
      characters("<some>markup&");
      elementEnd();

      // Check things we need to escape
      elementStart("r", new AttributesImpl("a", "2", "b", "\\\"'"));
      elementEnd();

      elementStart("q", Attributes.EMPTY_MAP);
      annotationBoundary(link1);
      elementEnd();
      elementStart("q", Attributes.EMPTY_MAP);
      annotationBoundary(AnnotationBoundaryMapImpl.builder().build());
      elementEnd();
      elementStart("q", Attributes.EMPTY_MAP);
      annotationBoundary(link1);
      elementEnd();

      elementStart("r", Attributes.EMPTY_MAP);
      annotationBoundary(link1);
      elementEnd();
      elementStart("r", Attributes.EMPTY_MAP);
      annotationBoundary(link2);
      elementEnd();

      annotationBoundary(ann);
      characters("abc");
      annotationBoundary(linkNull);
      characters("def");
      annotationBoundary(change);
      characters("ghi");

      annotationBoundary(finish);

      elementEnd();
    }
  }.finish();

  public static final BufferedDocOpImpl SOME_TEXT = (BufferedDocOpImpl) new DocOpBuffer() {
    {
      characters("some text");
    }
  }.finish();

  public static final BufferedDocOpImpl MORE = (BufferedDocOpImpl) new DocOpBuffer() {
    {
      retain(4);
      characters(" more");
      retain(5);
    }
  }.finish();

  public static final BufferedDocOpImpl DELETE_SOME = (BufferedDocOpImpl) new DocOpBuffer() {
    {
      deleteCharacters("some ");
      retain(9);
    }
  }.finish();

  private BufferedDocOpImpl encryptAndCompose(DocOp[] rev) {
    for (int i = 0; i < rev.length; i++) {
      BufferedDocOpImpl op = (BufferedDocOpImpl) rev[i];
      rev[i] = op.encrypt(i);
    }
    return (BufferedDocOpImpl) Composer.compose(Arrays.asList(rev));
  }

  private DocOp[] encryptAndDecrypt(DocOp[] rev) {
    for (int i = 0; i < rev.length; i++) {
      BufferedDocOpImpl op = (BufferedDocOpImpl) rev[i];
      op = (BufferedDocOpImpl) op.encrypt(i);
      rev[i] = op.decrypt();
    }
    return rev;
  }

  private BufferedDocOpImpl encryptDecryptAndCompose(DocOp[] rev) {
    rev = encryptAndDecrypt(rev);
    return (BufferedDocOpImpl) Composer.compose(Arrays.asList(rev));
  }

  private String getXML(DocOp dop) {
    assert (DocOpValidator.isWellFormed(null, dop));
    return DocOpUtil.toXmlString(DocOpUtil.asInitialization(dop));
  }
  private void printXML(DocOp dop) {
    System.out.print(getXML(dop));
  }

  public void testGSOC1() throws OperationException {
    DocOp[] rev = { SOME_TEXT, MORE, DELETE_SOME };
    encryptDecryptAndCompose(rev);
    printXML(encryptDecryptAndCompose(rev));
  }

  public void testGSOC2() throws OperationException {
    DocOp[] rev = { SOME_TEXT, MORE };
    encryptAndCompose(rev).decryptSnapshot();
    getXML(encryptAndCompose(rev).decryptSnapshot());
  }

  public void testGSOC3() throws OperationException {
    DocOp op1 = new DocOpBuffer() {
      {
        AnnotationBoundaryMapImpl ann = AnnotationBoundaryMapImpl.builder().initializationValues("x", "3", "y", "3")
            .build();
        AnnotationBoundaryMapImpl finish = AnnotationBoundaryMapImpl.builder().initializationEnd("x", "y").build();
        annotationBoundary(ann);
        characters("xx");
        annotationBoundary(finish);
      }
    }.finish();
    DocOp op2 = new DocOpBuffer() {
      {
        retain(2);
        characters("x");
      }
    }.finish();
    Composer.compose(op1, op2);
    getXML(Composer.compose(op1, op2));
  }

  public void testGSOC4() throws OperationException {
    final DocOp THERE_IS = new DocOpBuffer() {
      {
        characters("There is ");
        retain(9);
      }
    }.finish();
    DocOp[] rev = { SOME_TEXT, THERE_IS };
    encryptAndCompose(rev).decryptSnapshot();
    getXML(encryptAndCompose(rev).decryptSnapshot());
  }

  public void testGSOC5() throws OperationException {
    DocOp[] rev = { SOME_TEXT, MORE };
    BufferedDocOpImpl doc = encryptDecryptAndCompose(rev);
    // DELETE_SOME.encrypt(2, doc);

    getXML(doc);
  }

  public void testGSOC6() throws OperationException {
    DocOp[] rev = { TEST_DOC1 };
    encryptAndDecrypt(rev);
    getXML(encryptAndDecrypt(rev)[0]);

  }

  public void testAnnotations() throws OperationException {
    final DocOp ANNOT = new DocOpBuffer() {
      {
        characters("a");
        annotationBoundary(AnnotationBoundaryMapImpl.builder().initializationValues("a", "1").build());
        characters("bc");
        annotationBoundary(AnnotationBoundaryMapImpl.builder().initializationEnd("a").build());
      }
    }.finish();
    final DocOp ANNOT2 = new DocOpBuffer() {
      {
        retain(2);
        annotationBoundary(AnnotationBoundaryMapImpl.builder().updateValues("a", "1", "2").build());
        characters("x");
        annotationBoundary(AnnotationBoundaryMapImpl.builder().initializationEnd("a").build());
        retain(1);
      }
    }.finish();

    getXML(Composer.compose(ANNOT, ANNOT2));
  }

  public void testDeletion() throws OperationException {
    final DocOp ANNOT = new DocOpBuffer() {
      {
        characters("a");
        annotationBoundary(AnnotationBoundaryMapImpl.builder().initializationValues("x", "1", "y", "1").build());
        characters("bc");
        annotationBoundary(AnnotationBoundaryMapImpl.builder().initializationEnd("x", "y").build());
      }
    }.finish();
    final DocOp DEL = new DocOpBuffer() {
      {
        retain(1);
        annotationBoundary(AnnotationBoundaryMapImpl.builder().updateValues("x", "1", "2", "y", "1", "2").build());
        deleteCharacters("b");
        annotationBoundary(AnnotationBoundaryMapImpl.builder().initializationEnd("x", "y").build());
        retain(1);
      }
    }.finish();
    DocOp[] rev = { ANNOT, DEL };
    getXML(encryptDecryptAndCompose(rev));
  }

  public void xtestDel() throws OperationException {
    final DocOp SOME_DEL_ANN = new DocOpBuffer() {
      {
        characters("some ");
        annotationBoundary(AnnotationBoundaryMapImpl.builder().initializationValues("x", "1").build());
        deleteCharacters("*****");
        annotationBoundary(AnnotationBoundaryMapImpl.builder().initializationEnd("x").build());
        retain(9);
      }
    }.finish();
    final DocOp DEL = new DocOpBuffer() {
      {
        retain(1);
        // annotationBoundary(AnnotationBoundaryMapImpl.builder().initializationValues("z",
        // "1").build());
        deleteCharacters("b");
        // annotationBoundary(AnnotationBoundaryMapImpl.builder().initializationEnd("z").build());
        retain(1);
      }
    }.finish();
    final DocOp DEL_A = new DocOpBuffer() {
      {
        retain(1);
        characters("b");
        annotationBoundary(AnnotationBoundaryMapImpl.builder().initializationValues("x", "1").build());
        deleteCharacters("*");
        annotationBoundary(AnnotationBoundaryMapImpl.builder().initializationEnd("x").build());
        retain(1);
      }
    }.finish();
    // System.out.println(Composer.compose(DELETE_SOME, SOME_DEL_ANN));
    // System.out.println(Composer.compose(DEL, DEL_A));
  }
  
  public void testInvertTwice() throws OperationException {
    final BufferedDocOpImpl X = (BufferedDocOpImpl) new DocOpBuffer() {
      {
        annotationBoundary(AnnotationBoundaryMapImpl.builder().updateValues("x", "1", "2", "y", "1", "2").build());
        deleteCharacters("b");
        annotationBoundary(AnnotationBoundaryMapImpl.builder().initializationEnd("x", "y").build());
      }
    }.finish();
    BufferedDocOpImpl withIns = (BufferedDocOpImpl) X.encrypt(0, false);
    withIns = (BufferedDocOpImpl) DocOpInverter.invert(withIns);
    BufferedDocOpImpl withDel = (BufferedDocOpImpl) withIns.encrypt(0, true);
    withDel = (BufferedDocOpImpl) DocOpInverter.invert(withDel);
    System.out.println(withDel.getAnnotationBoundary(0).getOldValue(0));
    withDel = (BufferedDocOpImpl) withDel.clone();
    System.out.println(DocOpInverter.invert(DocOpInverter.invert(X)).getAnnotationBoundary(0).getOldValue(1));
  }
  
  public void testDecryptSnapshot1() throws OperationException {
    DocOp[] rev = { SOME_TEXT, MORE, DELETE_SOME };
    printXML(encryptAndCompose(rev));
    //printXML(encryptAndCompose(rev).decryptSnapshot());
  }
}
