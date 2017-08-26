/**
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements.  See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership.  The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License.  You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied.  See the License for the
 * specific language governing permissions and limitations
 * under the License.
 */

package org.waveprotocol.box.webclient.client.jsinterop;

import com.google.gwt.typedarrays.shared.ArrayBuffer;

import jsinterop.annotations.JsType;

/**
 * JsInterop interface for window.crypto.subtle
 *
 * @see <a href="https://www.w3.org/TR/WebCryptoAPI/#dfn-SubtleCrypto">SubtleCrypto (WebCrypto API)</a>
 */
@JsType(isNative = true)
public class SubtleCrypto {

  public native Promise<ArrayBuffer, Throwable> encrypt(AlgorithmIdentifier algorithm, Object key, Object data);

  public native Promise<ArrayBuffer, Throwable> decrypt(AlgorithmIdentifier algorithm, Object key, Object data);

  public native Promise<Object, Throwable> generateKey(AlgorithmIdentifier algorithm, boolean extractable,
      String[] keyUsages);

  public native Promise<Object, Throwable> importKey(String format, JsonWebKeyImpl keyData, AlgorithmIdentifier algorithm,
      boolean extractable, String[] keyUsages);

  public native Promise<JsonWebKey, Throwable> exportKey(String format, Object cryptoKey);

}
