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

/**
 * One step of the store schema: brings a store at {@link #fromVersion()} to {@code fromVersion() + 1}. The runner bumps
 * the stored version after the step returns, so a crash between the step's writes and the bump runs the step again on
 * the next start: a step must be safe to rerun.
 */
public interface StoreMigrationStep {

    int fromVersion();

    void apply(MigrationContext ctx);

}
