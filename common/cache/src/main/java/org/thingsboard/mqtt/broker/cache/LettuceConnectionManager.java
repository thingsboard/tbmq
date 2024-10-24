/**
 * Copyright © 2016-2024 The Thingsboard Authors
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.thingsboard.mqtt.broker.cache;


import io.lettuce.core.RedisFuture;
import io.lettuce.core.ScriptOutputType;

public interface LettuceConnectionManager {

    String scriptLoad(String script);

    RedisFuture<String> scriptLoadAsync(String script);

    <T> RedisFuture<T> evalShaAsync(String sha, ScriptOutputType outputType, String[] keys, String... values);

    <T> RedisFuture<T> evalShaAsync(String sha, ScriptOutputType outputType, String... keys);

    <T> RedisFuture<T> evalAsync(String script, ScriptOutputType outputType, String[] keys, String... values);

    <T> RedisFuture<T> evalAsync(String script, ScriptOutputType outputType, String... keys);

    RedisFuture<String> getAsync(String key);

    RedisFuture<Long> delAsync(String key);

    RedisFuture<String> setAsync(String key, String value);

}
