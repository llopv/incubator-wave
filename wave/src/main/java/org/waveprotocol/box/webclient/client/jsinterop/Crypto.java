package org.waveprotocol.box.webclient.client.jsinterop;

import jsinterop.annotations.JsType;

import com.google.gwt.typedarrays.shared.ArrayBuffer;

import jsinterop.annotations.JsPackage;

@JsType(isNative = true, name = "crypto", namespace = JsPackage.GLOBAL)
public class Crypto {

  public static SubtleCrypto subtle;

  public static native ArrayBuffer getRandomValues(Object array);

}
