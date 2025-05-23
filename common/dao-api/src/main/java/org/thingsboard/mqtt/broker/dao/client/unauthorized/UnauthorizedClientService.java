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
package org.thingsboard.mqtt.broker.dao.client.unauthorized;

import com.google.common.util.concurrent.ListenableFuture;
import org.thingsboard.mqtt.broker.common.data.UnauthorizedClient;
import org.thingsboard.mqtt.broker.common.data.client.unauthorized.UnauthorizedClientQuery;
import org.thingsboard.mqtt.broker.common.data.page.PageData;
import org.thingsboard.mqtt.broker.common.data.page.PageLink;

import java.util.Optional;

public interface UnauthorizedClientService {

    ListenableFuture<Void> save(UnauthorizedClient unauthorizedClient);

    ListenableFuture<Void> remove(UnauthorizedClient unauthorizedClient);

    void deleteUnauthorizedClient(String clientId);

    void deleteAllUnauthorizedClients();

    Optional<UnauthorizedClient> findUnauthorizedClient(String clientId);

    PageData<UnauthorizedClient> findUnauthorizedClients(UnauthorizedClientQuery query);

    PageData<UnauthorizedClient> findUnauthorizedClients(PageLink pageLink);

    void cleanupUnauthorizedClients(long ttl);
}
