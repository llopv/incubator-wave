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

import static org.junit.Assert.*;

import java.util.Map;

import org.junit.Test;
import org.waveprotocol.box.webclient.client.WaveCryptoManager;
import org.waveprotocol.wave.model.document.operation.DocOp;

public class OperationCryptoTest {

  private class WaveCryptoManagerMock extends WaveCryptoManager {
    public Cipher getCipher(String waveId) {
      return new Cipher() {
        @Override
        public void encrypt(String plaintext, String additionalData, Callback<String, Throwable> callback) {
          callback.onSuccess(plaintext);
        }

        @Override
        public void decrypt(String ciphertext, Callback<String, Throwable> callback) {
          callback.onSuccess(ciphertext);
        }

        @Override
        public void decryptAll(Map<Integer, String> ciphertexts, Callback<Map<Integer, String>, Throwable> callback) {
          callback.onSuccess(ciphertexts);
        }
      };
    }
  }

  private void encryptAndDecrypt(DocOp dop, String expectedCiphertext, String expectedPlaintext) {
    OperationCrypto.crypto = new WaveCryptoManagerMock();
    OperationCrypto.encrypt("XXX", dop, "0", (DocOp encrypted) -> {
      assertEquals(expectedCiphertext, DocOpUtil.toConciseString(encrypted));
      try {
        OperationCrypto.decrypt("XXX", encrypted, (DocOp decrypted) -> {
          assertEquals(expectedPlaintext, DocOpUtil.toConciseString(decrypted));
          return null;
        });
      } catch (Exception e) {
        // TODO Auto-generated catch block
        e.printStackTrace();
      }
      return null;
    });
  }

  @Test
  public void testInsert() {
    DocOp dop = new DocOpBuilder().characters("hello").build();
    encryptAndDecrypt(dop, "|| { \"cipher/0\": null -> \"hello\" }; ++\"*****\"; || { \"cipher/0\" }; ",
        "++\"hello\"; ");
  }

  @Test
  public void testDelete() {
    DocOp dop = new DocOpBuilder().deleteCharacters("hello").build();
    encryptAndDecrypt(dop, "--\"*****\"; ", "--\"*****\"; ");
  }

  @Test
  public void testInsertDelete() {
    DocOpBuilder builder = new DocOpBuilder();
    DocOp dop = builder.characters("hello").retain(1).deleteCharacters("world").build();
    encryptAndDecrypt(dop,
        "|| { \"cipher/0\": null -> \"hello\" }; ++\"*****\"; __1; --\"*****\"; || { \"cipher/0\" }; ",
        "++\"hello\"; __1; --\"*****\"; ");
  }
}
