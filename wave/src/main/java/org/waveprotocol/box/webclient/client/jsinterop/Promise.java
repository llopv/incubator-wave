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

import jsinterop.annotations.JsConstructor;
import jsinterop.annotations.JsFunction;
import jsinterop.annotations.JsPackage;
import jsinterop.annotations.JsType;

/**
 * JsInterop interface for ES6 Promises
 *
 * @see <a href="https://tc39.github.io/ecma262/#sec-promise-objects">ES6 Promises Specs</a>
 *
 * @param <S> Type of onResolved param
 * @param <R> Type of onRejected param
 */
@JsType(isNative = true, namespace = JsPackage.GLOBAL)
public class Promise<S, R> {

  @JsFunction
  public interface FunctionParam<T> {
    void exec(T o);
  }

  @JsFunction
  public interface ConstructorParam<S, R> {
    void exec(FunctionParam<S> resolve, FunctionParam<R> reject);
  }

  @JsConstructor
  public Promise(ConstructorParam<S, R> parameters) {
  }

  public native Promise<S, R> then(FunctionParam<S> onResolved, FunctionParam<R> onRejected);

}
