package org.waveprotocol.box.webclient.client.jsinterop;

import jsinterop.annotations.JsType;

@JsType(isNative = true)
public class JsonWebKey {

  public String kty;

  public String k;

  public String alg;

  public boolean ext;

}
