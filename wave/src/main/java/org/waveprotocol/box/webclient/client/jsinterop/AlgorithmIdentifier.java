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

import jsinterop.annotations.JsIgnore;
import jsinterop.annotations.JsType;

/**
 * JsInterop interface for AlgorithmIdentifier (WebCrypto API)
 *
 * @see <a href="https://www.w3.org/TR/WebCryptoAPI/#dfn-AlgorithmIdentifier">AlgorithmIdentifier (WebCrypto API)</a>
 */
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
