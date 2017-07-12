package org.waveprotocol.wave.model.document.operation.impl;

import static org.junit.Assert.*;

import org.junit.Test;
import org.waveprotocol.wave.model.document.operation.DocOp;

public class OperationCryptoTest {

  @Test
  public void testInsert() {
    DocOp dop = new DocOpBuilder().characters("hello").build();
    dop = OperationCrypto.encrypt(dop, 0);
    assertEquals(DocOpUtil.toConciseString(dop), "|| { \"cipher/0\": null -> \"hello\" }; ++\"*****\"; || { \"cipher/0\" }; ");
    dop = OperationCrypto.decrypt(dop);
    assertEquals(DocOpUtil.toConciseString(dop), "|| { \"cipher/0\": null -> \"hello\" }; ++\"hello\"; || { \"cipher/0\" }; ");
  }
  
  @Test
  public void testInsertDelete() {
    DocOpBuilder builder = new DocOpBuilder();
    DocOp dop = builder.characters("hello").retain(1).deleteCharacters("world").build();
    dop = OperationCrypto.encrypt(dop, 0);
    assertEquals(DocOpUtil.toConciseString(dop), "|| { \"cipher/0\": null -> \"hello\" }; ++\"*****\"; __1; --\"*****\"; || { \"cipher/0\" }; ");
    dop = OperationCrypto.decrypt(dop);
    assertEquals(DocOpUtil.toConciseString(dop), "|| { \"cipher/0\": null -> \"hello\" }; ++\"hello\"; __1; --\"*****\"; || { \"cipher/0\" }; ");
  }
}
