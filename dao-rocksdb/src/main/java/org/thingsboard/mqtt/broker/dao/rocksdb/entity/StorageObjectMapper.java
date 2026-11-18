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

import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.databind.json.JsonMapper;
import org.thingsboard.mqtt.broker.dao.store.StoreException;

import java.io.IOException;

/**
 * Writes and reads the value of an entity: one format byte (1), then the JSON of the domain object. Its own mapper,
 * never the REST one: a REST-only setting must not change what is stored. Reads ignore unknown properties, so an added
 * field needs no migration; a renamed or removed one does. The format is pinned by golden files, see
 * {@code StorageObjectMapperTest}.
 */
public final class StorageObjectMapper {

    static final byte FORMAT_JSON = 1;

    private static final ObjectMapper MAPPER = JsonMapper.builder()
            .disable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES)
            .enable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS)
            .annotationIntrospector(new StorageAnnotationIntrospector())
            .build();

    private StorageObjectMapper() {
    }

    public static byte[] write(Object entity) {
        try {
            byte[] json = MAPPER.writeValueAsBytes(entity);
            byte[] value = new byte[json.length + 1];
            value[0] = FORMAT_JSON;
            System.arraycopy(json, 0, value, 1, json.length);
            return value;
        } catch (IOException e) {
            throw new StoreException("Cannot write a " + entity.getClass().getSimpleName() + " to the store: " + e.getMessage(), e);
        }
    }

    public static <T> T read(byte[] value, Class<T> type) {
        if (value == null || value.length == 0) {
            throw new StoreException("Cannot read a " + type.getSimpleName() + " from the store: the value is "
                    + (value == null ? "missing" : "empty"));
        }
        if (value[0] != FORMAT_JSON) {
            throw new StoreException("Cannot read a " + type.getSimpleName() + " from the store: unknown format byte " + value[0]);
        }
        try {
            return MAPPER.readValue(value, 1, value.length - 1, type);
        } catch (IOException e) {
            throw new StoreException("Cannot read a " + type.getSimpleName() + " from the store: " + e.getMessage(), e);
        }
    }

}
