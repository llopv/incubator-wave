package org.waveprotocol.box.server.crypto;

import static org.junit.Assert.*;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Function;

import org.junit.Test;
import org.waveprotocol.box.webclient.client.WaveCryptoManager.Callback;
import org.waveprotocol.box.webclient.client.WaveCryptoManager.Cipher;
import org.waveprotocol.wave.model.document.operation.impl.AttributesImpl;
import org.waveprotocol.wave.model.document.operation.DocOp;
import org.waveprotocol.wave.model.document.operation.impl.DocOpBuilder;
import org.waveprotocol.wave.model.document.operation.impl.OperationCrypto;
import org.waveprotocol.box.server.crypto.RecoverSnapshot;

public class RecoverSnapshotTest {
  

  
  private void encryptAll(DocOp[] dops, Function<DocOp[], Void> callback) {
    
    List<DocOp> encryptedDops = new ArrayList<DocOp>();
    
    
    OperationCrypto opCrypto = OperationCrypto.create(new Cipher() {

      @Override
      public void encrypt(String plaintext, String additionalData, Callback<String, Object> callback) {
        callback.onSuccess(plaintext);
      }

      @Override
      public void decrypt(String ciphertext, Callback<String, Object> callback) {
        callback.onSuccess(ciphertext);
      }
      
    });
    
    int i = 0;
    for (DocOp dop : dops) {
      opCrypto.encrypt(dop, i++, new Function<DocOp, Void>() {

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
    encryptAll (dops, new Function<DocOp[], Void>() {
      @Override
      public Void apply(DocOp[] dops) {
        RecoverSnapshot snapshot = new RecoverSnapshot();
        snapshot.replay(dops);
        
        snapshot = new RecoverSnapshot().fromJSON(snapshot.toJSON());
        
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
    DocOp[] dops = new DocOp[] { new DocOpBuilder().characters("some text").build(),
        new DocOpBuilder().retain(4).elementStart("b", new AttributesImpl()).characters(" more").elementEnd().retain(5).build(),
        new DocOpBuilder().retain(2).deleteCharacters("me").deleteElementStart("b", new AttributesImpl()).deleteCharacters(" more").deleteElementEnd().deleteCharacters(" te").retain(2).build() };
    assertOp("soxt", dops);
  }

  @Test
  public void testXml2() {
    DocOp[] dops = new DocOp[] { new DocOpBuilder().characters("some text").build(),
        new DocOpBuilder().retain(4).elementStart("b", new AttributesImpl()).characters(" more").elementEnd().retain(5).build(),
        new DocOpBuilder().retain(8).characters("a").retain(8).build() };
    assertOp("some moare text", dops);
  }

  @Test
  public void testXml3() {
    DocOp[] dops = new DocOp[] { new DocOpBuilder().characters("some text").build(),
        new DocOpBuilder().retain(4).elementStart("b", new AttributesImpl()).characters(" more").elementEnd().retain(5).build(),
        new DocOpBuilder().retain(5).deleteCharacters(" more").retain(6).build() };
    assertOp("some text", dops);
  }

  @Test
  public void testXml4() {
    DocOp[] dops = new DocOp[] { new DocOpBuilder().characters("some text").build(),
        new DocOpBuilder().retain(4).elementStart("b", new AttributesImpl()).characters(" more").elementEnd().retain(5).build(),
        new DocOpBuilder().retain(2).deleteCharacters("me").retain(1).deleteCharacters(" more").retain(6).build() };
    assertOp("so text", dops);
  }

  @Test
  public void testXml5() {
    DocOp[] dops = new DocOp[] { new DocOpBuilder().characters("some text").build(),
        new DocOpBuilder().retain(4).elementStart("b", new AttributesImpl()).characters(" more").elementEnd().retain(5).build(),
        new DocOpBuilder().deleteCharacters("some").retain(1).deleteCharacters(" mo").retain(8).build() };
    assertOp("re text", dops);
  }
}
