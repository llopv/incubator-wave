package org.waveprotocol.box.webclient.client.jsinterop;

import com.google.gwt.typedarrays.shared.ArrayBuffer;

import jsinterop.annotations.JsType;

@JsType(isNative = true)
public class SubtleCrypto {

	public native Promise<ArrayBuffer, Object> encrypt(AlgorithmIdentifier algorithm, Object key, Object data);

	public native Promise<ArrayBuffer, Object> decrypt(AlgorithmIdentifier algorithm, Object key, Object data);

	public native Promise<CryptoKey, Object> generateKey(AlgorithmIdentifier algorithm, boolean extractable, String[] keyUsages);
	
	public native Promise<CryptoKey, Object> importKey(String format, JsonWebKeyImpl keyData, AlgorithmIdentifier algorithm, boolean extractable, String[] keyUsages);
	
	public native Promise<JsonWebKey, Object> exportKey(String format, CryptoKey key);

}
