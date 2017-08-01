package org.waveprotocol.box.webclient.client.jsinterop;

import jsinterop.annotations.JsIgnore;
import jsinterop.annotations.JsType;

@JsType(namespace = "WaveCrypto")
public class AlgorithmIdentifier {

  public String name;
  public Object iv;
  public Object additionalData;
  public int length;

  @JsIgnore
  public AlgorithmIdentifier(String name, int length) {
    super();
    this.name = name;
    this.length = length;
  }

  @JsIgnore
  public AlgorithmIdentifier(String name, Object iv, Object additionalData) {
    super();
    this.name = name;
    this.iv = iv;
    this.additionalData = additionalData;
  }

}
