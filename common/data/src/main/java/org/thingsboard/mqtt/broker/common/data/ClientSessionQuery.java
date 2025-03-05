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
package org.thingsboard.mqtt.broker.common.data;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import org.thingsboard.mqtt.broker.common.data.client.session.SubscriptionOperation;
import org.thingsboard.mqtt.broker.common.data.page.TimePageLink;

import java.util.List;
import java.util.Set;

@Data
@Builder
@AllArgsConstructor
public class ClientSessionQuery {

    private TimePageLink pageLink;
    private List<ConnectionState> connectedStatusList;
    private List<ClientType> clientTypeList;
    private List<Boolean> cleanStartList;
    private Set<String> nodeIdSet;
    private Integer subscriptions;
    private SubscriptionOperation operation;
    private String clientIpAddress;

}
