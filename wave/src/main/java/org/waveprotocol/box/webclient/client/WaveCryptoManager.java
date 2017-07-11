package org.waveprotocol.box.webclient.client;

import java.util.HashMap;
import java.util.Map;

import org.waveprotocol.box.webclient.client.jsinterop.AlgorithmIdentifier;
import org.waveprotocol.box.webclient.client.jsinterop.Crypto;
import org.waveprotocol.box.webclient.client.jsinterop.CryptoKey;
import org.waveprotocol.box.webclient.client.jsinterop.JsonWebKey;
import org.waveprotocol.box.webclient.client.jsinterop.JsonWebKeyImpl;
import org.waveprotocol.box.webclient.client.jsinterop.NativeWindow;
import org.waveprotocol.wave.model.id.WaveId;

import com.google.gwt.typedarrays.client.JsUtils;
import com.google.gwt.typedarrays.client.Uint8ArrayNative;
import com.google.gwt.typedarrays.shared.ArrayBuffer;
import com.google.gwt.typedarrays.shared.Uint8Array;

import jsinterop.annotations.JsType;

@JsType(namespace = "WaveCrypto")
public class WaveCryptoManager {
	
	private static final String CRYPTO_KEY_FORMAT = "jwk";
	private static final String CRYPTO_JSON_ALGORITHM = "A256GCM";
	private static final String CRYPTO_ALGORITHM_NAME = "AES-GCM";
	private static final String[] CRYPTO_KEY_USAGES = new String[] {"encrypt", "decrypt"};
	
	
	private static WaveCryptoManager instance = new WaveCryptoManager();
	
	public static WaveCryptoManager get() {
		return instance;
	}
	
	@JsType
	public interface Cipher {
		
		public void encrypt(String plaintext, String additionalData, Callback<String, Object> callback);
		
		public void decrypt(String ciphertext, Callback<String, Object> callback);
		
	}
	
	@JsType(isNative = true)
	public interface Callback<S, R> {
		
		public void onSuccess(S result);
		
		public void onFailure(R reason);
		
	}
	
	public static String arrayBufferToBase64(ArrayBuffer buffer) {
		
		Uint8Array bytes = Uint8ArrayNative.create(buffer);
		
		String binaryStr = "";
		for (int i = 0; i < bytes.byteLength(); i++) {
			binaryStr += String.valueOf((char) bytes.get(i));
		}
		
		return NativeWindow.btoa(binaryStr);
	}
	
	public static ArrayBuffer base64ToArrayBuffer(String s) {
		
		String binaryStr = NativeWindow.atob(s);		
		Uint8Array bytes = Uint8ArrayNative.create(binaryStr.length());
		
		for (int i = 0; i < binaryStr.length(); i++) {
			bytes.set(i, binaryStr.charAt(i));
		}
		
		return bytes.buffer();
	}
	
	
	public static final WaveId DEFAULT_WAVE_ID = WaveId.of("local.net", "XXXXXX");
	
	private final Map<WaveId, CryptoKey> keysRegistry = new HashMap<WaveId, CryptoKey>();
	
	public void generateKey(WaveId waveId, final Callback<String, Object> callback) {
		AlgorithmIdentifier algorithm = new AlgorithmIdentifier(CRYPTO_ALGORITHM_NAME, 256);

		Crypto.subtle.generateKey(algorithm, true, CRYPTO_KEY_USAGES).then(
		(CryptoKey cryptoKey) -> {
			keysRegistry.put(waveId, cryptoKey);
			Crypto.subtle.exportKey(CRYPTO_KEY_FORMAT, cryptoKey).then(
					(JsonWebKey key) ->  {
						callback.onSuccess(key.k);
					}, 
					(Object reason) -> {
						callback.onFailure(reason);
					});
		}, 
		(Object reason) -> {
			callback.onFailure(reason);
		});
		
		
	}
	
	public void registerKey(WaveId waveId, String key, final Callback<String, Object> callback) {

		JsonWebKeyImpl keyData = new JsonWebKeyImpl("oct", key, CRYPTO_JSON_ALGORITHM, true);
		AlgorithmIdentifier algorithm = new AlgorithmIdentifier(CRYPTO_ALGORITHM_NAME, 256);
		
		Crypto.subtle.importKey(CRYPTO_KEY_FORMAT, keyData, algorithm, false, CRYPTO_KEY_USAGES).then(
		(CryptoKey cryptoKey) -> {
			keysRegistry.put(waveId, cryptoKey);
			callback.onSuccess(key);
		}, 
		(Object reason) -> {
			callback.onFailure(reason);
		}); 

		
	}
	
	public Cipher getCihper(final WaveId waveId) {
		return new Cipher() {

			
			final Object cryptoKey = keysRegistry.get(waveId);
			
			@Override
			public void encrypt(String plaintext,  String additionalDataStr, Callback<String, Object> callback) {
				
				ArrayBuffer data = JsUtils.arrayBufferFromString(plaintext);
		
				ArrayBuffer iv = Crypto.getRandomValues(Uint8ArrayNative.create(12));
				ArrayBuffer additionalData = JsUtils.arrayBufferFromString(additionalDataStr);
				
				AlgorithmIdentifier algorithm = new AlgorithmIdentifier(CRYPTO_ALGORITHM_NAME, iv, additionalData);  
				
				Crypto.subtle.encrypt(algorithm, cryptoKey, data).then(
				(ArrayBuffer buffer) -> {
					
					String ciphertext = arrayBufferToBase64(iv) + ";" + arrayBufferToBase64(buffer) + ";" + arrayBufferToBase64(additionalData);					
					callback.onSuccess(ciphertext);
		
				}, 
				(Object reason) -> {
					
					callback.onFailure(reason);
				});

			}
			
			@Override
			public void decrypt(String ciphertext, Callback<String, Object> callback) {
				
				String[] cipherparts = ciphertext.split(";", -1);
				ArrayBuffer iv = base64ToArrayBuffer(cipherparts[0]);
				ArrayBuffer data =  base64ToArrayBuffer(cipherparts[1]);
				ArrayBuffer additionalData =  base64ToArrayBuffer(cipherparts[2]);
				
				
				AlgorithmIdentifier algorithm = new AlgorithmIdentifier(CRYPTO_ALGORITHM_NAME, iv, additionalData);  

				Crypto.subtle.decrypt(algorithm, cryptoKey, data).then(
					(ArrayBuffer buffer) -> {	
						callback.onSuccess(JsUtils.stringFromArrayBuffer(buffer));
					},
					(Object reason) -> {
						callback.onFailure(reason);
					}
				);
				
				
			}
			
		};
	}
	
}
