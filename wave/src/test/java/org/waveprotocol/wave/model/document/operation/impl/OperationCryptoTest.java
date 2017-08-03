package org.waveprotocol.wave.model.document.operation.impl;

import static org.junit.Assert.*;

import org.junit.Test;
import org.waveprotocol.box.webclient.client.WaveCryptoManager;
import org.waveprotocol.wave.model.document.operation.DocOp;

public class OperationCryptoTest {

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

  private void encryptAndDecrypt(DocOp dop, String expectedCiphertext, String expectedPlaintext) {
    OperationCrypto.crypto = new WaveCryptoManagerMock();
    OperationCrypto.encrypt("XXX", dop, 0, (DocOp encrypted) -> {
      assertEquals(expectedCiphertext, DocOpUtil.toConciseString(encrypted));
      OperationCrypto.decrypt("XXX", encrypted, (DocOp decrypted) -> {
        assertEquals(expectedPlaintext, DocOpUtil.toConciseString(decrypted));
        return null;
      });
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
