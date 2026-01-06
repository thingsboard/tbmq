/**
 * Copyright © 2016-2025 The Thingsboard Authors
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
package org.thingsboard.mqtt.broker.common.data.sync.ie.importing.csv;

import lombok.Getter;

@Getter
public enum BulkImportColumnType {
    NAME,
    DESCRIPTION,
    CREDENTIALS_VALUE,
    CREDENTIALS_TYPE,
    CLIENT_TYPE;

    private String key;
    private String defaultValue;
    private boolean isKv = false;

    BulkImportColumnType() {
    }

    BulkImportColumnType(String key) {
        this.key = key;
    }

    BulkImportColumnType(String key, String defaultValue) {
        this.key = key;
        this.defaultValue = defaultValue;
    }

    BulkImportColumnType(boolean isKv) {
        this.isKv = isKv;
    }

    BulkImportColumnType(String key, boolean isKv) {
        this.key = key;
        this.isKv = isKv;
    }
}
