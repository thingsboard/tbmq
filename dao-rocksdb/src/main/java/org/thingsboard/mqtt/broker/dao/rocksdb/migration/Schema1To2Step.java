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
package org.thingsboard.mqtt.broker.dao.rocksdb.migration;

import org.springframework.stereotype.Component;

/**
 * Schema 1 to 2: adds the per-install JWT signing key. The column families of schema 2 are created on every open, and
 * the management data is seeded at startup because a schema-1 store has no seed marker; the broker state is untouched.
 */
@Component
public class Schema1To2Step implements StoreMigrationStep {

    @Override
    public int fromVersion() {
        return 1;
    }

    @Override
    public void apply(MigrationContext ctx) {
        ctx.ensureJwtSigningKey();
    }

}
