package org.waveprotocol.box.webclient.client.jsinterop;

import jsinterop.annotations.JsMethod;
import jsinterop.annotations.JsPackage;

public class NativeWindow {

	@JsMethod(namespace = JsPackage.GLOBAL)
	public static native String btoa(String o);
	
	@JsMethod(namespace = JsPackage.GLOBAL)
	public static native String atob(String o);
	
}
