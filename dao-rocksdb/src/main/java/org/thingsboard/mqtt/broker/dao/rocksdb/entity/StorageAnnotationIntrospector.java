/**
 * Copyright © 2016-2026 The Thingsboard Authors
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
package org.thingsboard.mqtt.broker.dao.rocksdb.entity;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.introspect.Annotated;
import com.fasterxml.jackson.databind.introspect.JacksonAnnotationIntrospector;

/**
 * The introspector of the storage mapper: the Jackson annotations of the domain classes apply as they do for REST,
 * except that no property is read-only or write-only. A secret that the REST mapper never writes, or never reads,
 * is stored all the same.
 */
class StorageAnnotationIntrospector extends JacksonAnnotationIntrospector {

    @Override
    public JsonProperty.Access findPropertyAccess(Annotated annotated) {
        return JsonProperty.Access.AUTO;
    }

}
