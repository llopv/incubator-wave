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

import static org.junit.Assert.*;

import java.io.StringReader;
import java.io.StringWriter;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Function;

import org.junit.Test;
import org.waveprotocol.box.webclient.client.WaveCryptoManager;
import org.waveprotocol.wave.model.document.operation.impl.AttributesImpl;
import org.waveprotocol.wave.model.document.operation.AnnotationBoundaryMap;
import org.waveprotocol.wave.model.document.operation.AnnotationBoundaryMapBuilder;
import org.waveprotocol.wave.model.document.operation.DocOp;
import org.waveprotocol.wave.model.document.operation.impl.DocOpBuilder;
import org.waveprotocol.wave.model.document.operation.impl.OperationCrypto;
import org.waveprotocol.wave.model.id.WaveletId;

import com.google.gson.stream.JsonReader;
import com.google.gson.stream.JsonWriter;

import org.waveprotocol.box.server.crypto.RecoverSnapshot;

public class RecoverSnapshotTest {

  private class WaveCryptoManagerMock extends WaveCryptoManager {
    public Cipher getCipher(String waveId) {
      return new Cipher() {
        @Override
        public void encrypt(String plaintext, String additionalData, Callback<String, Object> callback) {
          callback.onSuccess(plaintext);
        }

        @Override
        public void decrypt(String ciphertext, Callback<String, Object> callback) {
          callback.onSuccess(ciphertext);
        }
      };
    }
  }

  private void encryptAll(DocOp[] dops, Function<DocOp[], Void> callback) {

    List<DocOp> encryptedDops = new ArrayList<DocOp>();

    OperationCrypto.crypto = new WaveCryptoManagerMock();

    int i = 0;
    for (DocOp dop : dops) {
      OperationCrypto.encrypt("", dop, String.valueOf(i++), new Function<DocOp, Void>() {

        @Override
        public Void apply(DocOp dop) {
          encryptedDops.add(dop);
          if (encryptedDops.size() >= dops.length) {
            callback.apply(encryptedDops.toArray(new DocOp[encryptedDops.size()]));
          }
          return null;
        }

      });
    }
  }

  void assertOp(String expected, DocOp[] dops) {
    encryptAll(dops, new Function<DocOp[], Void>() {
      @Override
      public Void apply(DocOp[] dops) {
        RecoverSnapshot snapshot = new RecoverSnapshot();
        for (DocOp dop : dops) {
          snapshot.replay(dop);
        }
        StringWriter s = new StringWriter();
        JsonWriter writer = new JsonWriter(s);

        snapshot.toJson(WaveletId.of("local.net", "ew+123"), writer);
        String json = s.toString();

        JsonReader reader = new JsonReader(new StringReader(json));
        snapshot = new RecoverSnapshot().fromJson(reader);
        assertEquals(expected, snapshot.reconstitute());
        return null;
      }
    });
  }

  @Test
  public void test1() {
    DocOp[] dops = new DocOp[] { new DocOpBuilder().characters("some text").build(),
        new DocOpBuilder().retain(4).characters(" more").retain(5).build(),
        new DocOpBuilder().retain(2).deleteCharacters("me more te").retain(2).build() };
    assertOp("soxt", dops);
  }

  @Test
  public void test2() {
    DocOp[] dops = new DocOp[] { new DocOpBuilder().characters("some text").build(),
        new DocOpBuilder().retain(4).characters(" more").retain(5).build(),
        new DocOpBuilder().retain(7).characters("a").retain(7).build() };
    assertOp("some moare text", dops);
  }

  @Test
  public void test3() {
    DocOp[] dops = new DocOp[] { new DocOpBuilder().characters("some text").build(),
        new DocOpBuilder().retain(4).characters(" more").retain(5).build(),
        new DocOpBuilder().retain(4).deleteCharacters(" more").retain(5).build() };
    assertOp("some text", dops);
  }

  @Test
  public void test4() {
    DocOp[] dops = new DocOp[] { new DocOpBuilder().characters("some text").build(),
        new DocOpBuilder().retain(4).characters(" more").retain(5).build(),
        new DocOpBuilder().retain(2).deleteCharacters("me more").retain(5).build() };
    assertOp("so text", dops);
  }

  @Test
  public void test5() {
    DocOp[] dops = new DocOp[] { new DocOpBuilder().characters("some text").build(),
        new DocOpBuilder().retain(4).characters(" more").retain(5).build(),
        new DocOpBuilder().deleteCharacters("some mo").retain(7).build() };
    assertOp("re text", dops);
  }

  @Test
  public void testXml1() {
    DocOp[] dops = new DocOp[] {
        new DocOpBuilder().characters("some te").elementStart("b", new AttributesImpl()).characters("xt").elementEnd()
            .build(),
        new DocOpBuilder().retain(4).elementStart("b", new AttributesImpl()).characters(" more").elementEnd().retain(5)
            .build(),
        new DocOpBuilder().retain(2).deleteCharacters("me").deleteElementStart("b", new AttributesImpl())
            .deleteCharacters(" more").deleteElementEnd().deleteCharacters(" te").retain(2).build() };
    assertOp("soxt", dops);
  }

  @Test
  public void testXml2() {
    DocOp[] dops = new DocOp[] {
        new DocOpBuilder().characters("some text").build(), new DocOpBuilder().retain(4)
            .elementStart("b", new AttributesImpl()).characters(" more").elementEnd().retain(5).build(),
        new DocOpBuilder().retain(8).characters("a").retain(8).build() };
    assertOp("some moare text", dops);
  }

  @Test
  public void testXml3() {
    DocOp[] dops = new DocOp[] {
        new DocOpBuilder().characters("some text").build(), new DocOpBuilder().retain(4)
            .elementStart("b", new AttributesImpl()).characters(" more").elementEnd().retain(5).build(),
        new DocOpBuilder().retain(5).deleteCharacters(" more").retain(6).build() };
    assertOp("some text", dops);
  }

  @Test
  public void testXml4() {
    DocOp[] dops = new DocOp[] { new DocOpBuilder().characters("some text").build(),
        new DocOpBuilder().retain(4).elementStart("b", new AttributesImpl()).characters(" more").elementEnd().retain(5)
            .build(),
        new DocOpBuilder().retain(2).deleteCharacters("me").retain(1).deleteCharacters(" more").retain(6).build() };
    assertOp("so text", dops);
  }

  @Test
  public void testXml5() {
    DocOp[] dops = new DocOp[] {
        new DocOpBuilder().characters("some text").build(), new DocOpBuilder().retain(4)
            .elementStart("b", new AttributesImpl()).characters(" more").elementEnd().retain(5).build(),
        new DocOpBuilder().deleteCharacters("some").retain(1).deleteCharacters(" mo").retain(8).build() };
    assertOp("re text", dops);
  }

  @Test
  public void testDel1() {
    AnnotationBoundaryMap map = new AnnotationBoundaryMapBuilder().change("style/bold", null, "true").build();
    AnnotationBoundaryMap map2 = new AnnotationBoundaryMapBuilder().end("style/bold").build();
    DocOp[] dops = new DocOp[] { new DocOpBuilder().characters("some ").annotationBoundary(map).characters("more ")
        .annotationBoundary(map2).characters("text").build(),
        new DocOpBuilder().deleteCharacters("so").retain(12).build() };
    assertOp("me more text", dops);
  }

  @Test
  public void testDel2() {
    AnnotationBoundaryMap map = new AnnotationBoundaryMapBuilder().change("style/bold", null, "true").build();
    AnnotationBoundaryMap map2 = new AnnotationBoundaryMapBuilder().end("style/bold").build();
    DocOp[] dops = new DocOp[] { new DocOpBuilder().characters("some ").annotationBoundary(map).characters("more ")
        .annotationBoundary(map2).characters("text").build(),
        new DocOpBuilder().deleteCharacters("some ").retain(9).build() };
    assertOp("more text", dops);
  }

  @Test
  public void testDel3() {
    AnnotationBoundaryMap map = new AnnotationBoundaryMapBuilder().change("style/bold", null, "true").build();
    AnnotationBoundaryMap map2 = new AnnotationBoundaryMapBuilder().end("style/bold").build();
    DocOp[] dops = new DocOp[] { new DocOpBuilder().characters("some ").annotationBoundary(map).characters("more ")
        .annotationBoundary(map2).characters("text").build(),
        new DocOpBuilder().deleteCharacters("some mo").retain(7).build() };
    assertOp("re text", dops);
  }

  @Test
  public void testDel4() {
    AnnotationBoundaryMap map = new AnnotationBoundaryMapBuilder().change("style/bold", null, "true").build();
    AnnotationBoundaryMap map2 = new AnnotationBoundaryMapBuilder().end("style/bold").build();
    DocOp[] dops = new DocOp[] { new DocOpBuilder().characters("some ").annotationBoundary(map).characters("more ")
        .annotationBoundary(map2).characters("text").build(),
        new DocOpBuilder().deleteCharacters("some more ").retain(4).build() };
    assertOp("text", dops);
  }

  @Test
  public void testDel5() {
    AnnotationBoundaryMap map = new AnnotationBoundaryMapBuilder().change("style/bold", null, "true").build();
    AnnotationBoundaryMap map2 = new AnnotationBoundaryMapBuilder().end("style/bold").build();
    DocOp[] dops = new DocOp[] { new DocOpBuilder().characters("some ").annotationBoundary(map).characters("more ")
        .annotationBoundary(map2).characters("text").build(),
        new DocOpBuilder().retain(2).deleteCharacters("me mo").retain(7).build() };
    assertOp("sore text", dops);
  }

  @Test
  public void testDel6() {
    AnnotationBoundaryMap map = new AnnotationBoundaryMapBuilder().change("style/bold", null, "true").build();
    AnnotationBoundaryMap map2 = new AnnotationBoundaryMapBuilder().end("style/bold").build();
    DocOp[] dops = new DocOp[] {
        new DocOpBuilder().characters("some ").annotationBoundary(map).characters("more ").annotationBoundary(map2)
            .characters("text").build(),
        new DocOpBuilder().retain(2).deleteCharacters("me more te").retain(2).build() };
    assertOp("soxt", dops);
  }

  @Test
  public void testDel7() {
    AnnotationBoundaryMap map = new AnnotationBoundaryMapBuilder().change("style/bold", null, "true").build();
    AnnotationBoundaryMap map2 = new AnnotationBoundaryMapBuilder().end("style/bold").build();
    DocOp[] dops = new DocOp[] { new DocOpBuilder().characters("some ").annotationBoundary(map).characters("more ")
        .annotationBoundary(map2).characters("text").build(),
        new DocOpBuilder().retain(7).deleteCharacters("re text").build() };
    assertOp("some mo", dops);
  }

  @Test
  public void testDel8() {
    AnnotationBoundaryMap map = new AnnotationBoundaryMapBuilder().change("style/bold", null, "true").build();
    AnnotationBoundaryMap map2 = new AnnotationBoundaryMapBuilder().end("style/bold").build();
    DocOp[] dops = new DocOp[] { new DocOpBuilder().characters("some ").annotationBoundary(map).characters("more ")
        .annotationBoundary(map2).characters("text").build(),
        new DocOpBuilder().retain(12).deleteCharacters("xt").build() };
    assertOp("some more te", dops);
  }

  @Test
  public void testDel9() {
    AnnotationBoundaryMap map = new AnnotationBoundaryMapBuilder().change("style/bold", null, "true").build();
    AnnotationBoundaryMap map2 = new AnnotationBoundaryMapBuilder().end("style/bold").build();
    DocOp[] dops = new DocOp[] { new DocOpBuilder().characters("some ").annotationBoundary(map).characters("more ")
        .annotationBoundary(map2).characters("text").build(),
        new DocOpBuilder().retain(12).deleteCharacters("x").retain(1).build() };
    assertOp("some more tet", dops);
  }

  @Test
  public void testXMLDel1() {
    DocOp[] dops = new DocOp[] { new DocOpBuilder().characters("some ").elementStart("b", new AttributesImpl())
        .characters("more ").elementEnd().characters("text").build(),
        new DocOpBuilder().deleteCharacters("so").retain(14).build() };
    assertOp("me more text", dops);
  }

  @Test
  public void testXMLDel2() {
    DocOp[] dops = new DocOp[] { new DocOpBuilder().characters("some ").elementStart("b", new AttributesImpl())
        .characters("more ").elementEnd().characters("text").build(),
        new DocOpBuilder().deleteCharacters("some ").retain(11).build() };
    assertOp("more text", dops);
  }

  @Test
  public void testXMLDel3() {
    DocOp[] dops = new DocOp[] {
        new DocOpBuilder().characters("some ").elementStart("b", new AttributesImpl()).characters("more ").elementEnd()
            .characters("text").build(),
        new DocOpBuilder().deleteCharacters("some ").retain(1).deleteCharacters("mo").retain(8).build() };
    assertOp("re text", dops);
  }

  @Test
  public void testXMLDel4() {

    DocOp[] dops = new DocOp[] {
        new DocOpBuilder().characters("some ").elementStart("b", new AttributesImpl()).characters("more ").elementEnd()
            .characters("text").build(),
        new DocOpBuilder().deleteCharacters("some ").retain(1).deleteCharacters("more ").retain(5).build() };
    assertOp("text", dops);
  }

  @Test
  public void testXMLDel5() {
    DocOp[] dops = new DocOp[] {
        new DocOpBuilder().characters("some ").elementStart("b", new AttributesImpl()).characters("more ").elementEnd()
            .characters("text").build(),
        new DocOpBuilder().retain(2).deleteCharacters("me ").retain(1).deleteCharacters("mo").retain(8).build() };
    assertOp("sore text", dops);
  }

  @Test
  public void testXMLDel6() {

    DocOp[] dops = new DocOp[] {
        new DocOpBuilder().characters("some ").elementStart("b", new AttributesImpl()).characters("more ").elementEnd()
            .characters("text").build(),
        new DocOpBuilder().retain(2).deleteCharacters("me ").retain(1).deleteCharacters("more ").retain(1)
            .deleteCharacters("te").retain(2).build() };
    assertOp("soxt", dops);
  }

  @Test
  public void testXMLDel7() {

    DocOp[] dops = new DocOp[] {
        new DocOpBuilder().characters("some ").elementStart("b", new AttributesImpl()).characters("more ").elementEnd()
            .characters("text").build(),
        new DocOpBuilder().retain(8).deleteCharacters("re ").retain(1).deleteCharacters("text").build() };
    assertOp("some mo", dops);
  }

  @Test
  public void testXMLDel8() {
    DocOp[] dops = new DocOp[] { new DocOpBuilder().characters("some ").elementStart("b", new AttributesImpl())
        .characters("more ").elementEnd().characters("text").build(),
        new DocOpBuilder().retain(14).deleteCharacters("xt").build() };
    assertOp("some more te", dops);
  }

  @Test
  public void testXMLDel9() {
    DocOp[] dops = new DocOp[] { new DocOpBuilder().characters("some ").elementStart("b", new AttributesImpl())
        .characters("more ").elementEnd().characters("text").build(),
        new DocOpBuilder().retain(14).deleteCharacters("x").retain(1).build() };
    assertOp("some more tet", dops);
  }

  // @Test
  public void testAnot1() {
    AnnotationBoundaryMap map = new AnnotationBoundaryMapBuilder().change("style/bold", null, "true").build();
    AnnotationBoundaryMap map2 = new AnnotationBoundaryMapBuilder().end("style/bold").build();
    DocOp[] dops = new DocOp[] {
        new DocOpBuilder().annotationBoundary(map).characters("some te").elementStart("b", new AttributesImpl())
            .characters("xt").elementEnd().annotationBoundary(map2).build(),
        new DocOpBuilder().retain(4).elementStart("b", new AttributesImpl()).characters(" more").elementEnd().retain(5)
            .build(),
        new DocOpBuilder().retain(2).deleteCharacters("me").deleteElementStart("b", new AttributesImpl())
            .deleteCharacters(" more").deleteElementEnd().deleteCharacters(" te").retain(2).build() };
    assertOp("soxt", dops);
  }

}
