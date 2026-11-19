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
package org.thingsboard.mqtt.broker.service.install;

/**
 * Marker of the bean named {@code managementDataInitializer}. Beans that read management data (settings, auth
 * providers) in {@code @PostConstruct} depend on it, so the data is there before they are created. Creating the bean
 * writes the data where the edition needs it: the cluster install step owns it there, so that bean does nothing.
 */
public interface ManagementDataInitializer {
}
