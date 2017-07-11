package org.waveprotocol.box.webclient.client.jsinterop;

import jsinterop.annotations.JsMethod;
import jsinterop.annotations.JsPackage;
import jsinterop.annotations.JsType;
import com.google.gwt.typedarrays.shared.ArrayBuffer;

public class NativeWindow {
  @JsMethod(namespace = JsPackage.GLOBAL)
  public static native String btoa(String o);

  @JsMethod(namespace = JsPackage.GLOBAL)
  public static native String atob(String o);

  @JsType(isNative = true, namespace = JsPackage.GLOBAL)
  public static class TextEncoder {
    @JsMethod
    public native ArrayBuffer encode(String text);
  }

  @JsType(isNative = true, namespace = JsPackage.GLOBAL)
  public static class TextDecoder {
    @JsMethod
    public native String decode(ArrayBuffer buffer);
  }

}
