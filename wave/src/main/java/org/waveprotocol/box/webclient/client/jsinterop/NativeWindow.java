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

import jsinterop.annotations.JsMethod;
import jsinterop.annotations.JsPackage;
import jsinterop.annotations.JsType;
import com.google.gwt.typedarrays.shared.ArrayBuffer;

/**
 * JsInterop interface to access window.* functions
 */
public class NativeWindow {
  @JsMethod(namespace = JsPackage.GLOBAL)
  public static native String btoa(String o);

  @JsMethod(namespace = JsPackage.GLOBAL)
  public static native String atob(String o);

  /**
   * @see https://www.w3.org/TR/encoding/#textencoder
   */
  @JsType(isNative = true, namespace = JsPackage.GLOBAL)
  public static class TextEncoder {
    @JsMethod
    public native ArrayBuffer encode(String text);
  }

  /**
   * @see https://www.w3.org/TR/encoding/#textdecoder
   */
  @JsType(isNative = true, namespace = JsPackage.GLOBAL)
  public static class TextDecoder {
    @JsMethod
    public native String decode(ArrayBuffer buffer);
  }

}
