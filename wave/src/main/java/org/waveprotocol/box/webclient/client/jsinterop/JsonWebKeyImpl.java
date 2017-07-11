package org.waveprotocol.box.webclient.client.jsinterop;

import jsinterop.annotations.JsIgnore;
import jsinterop.annotations.JsType;

@JsType
public class JsonWebKeyImpl {

	public String kty;
	
	public String k;
	
	public String alg;
	
	public boolean ext;
	
	@JsIgnore
	public JsonWebKeyImpl(String kty, String k, String alg, boolean ext) {
		this.kty = kty;
		this.k = k;
		this.alg = alg;
		this.ext = ext;
	}
				
}
