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
package org.thingsboard.mqtt.broker.standalone.application;

import org.junit.jupiter.api.Test;
import org.thingsboard.mqtt.broker.common.data.ApplicationSharedSubscription;
import org.thingsboard.mqtt.broker.common.data.page.PageData;
import org.thingsboard.mqtt.broker.common.data.page.PageLink;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class NoOpApplicationSharedSubscriptionServiceTest {

    private final NoOpApplicationSharedSubscriptionService service = new NoOpApplicationSharedSubscriptionService();

    @Test
    void findReturnsNullAndWritesAreRefused() {
        assertThat(service.findSharedSubscriptionByTopic("a/#")).isNull();
        assertThatThrownBy(() -> service.saveSharedSubscription(new ApplicationSharedSubscription()))
                .isInstanceOf(UnsupportedOperationException.class)
                .hasMessage("Application shared subscriptions are not supported in TBMQ Lite");
        assertThatThrownBy(() -> service.deleteSharedSubscription(UUID.randomUUID()))
                .isInstanceOf(UnsupportedOperationException.class)
                .hasMessage("Application shared subscriptions are not supported in TBMQ Lite");
    }

    @Test
    void readsFindNothing() throws Exception {
        assertThat(service.findSharedSubscriptionByTopicAsync("a/#").get()).isNull();
        assertThat(service.getSharedSubscriptionById(UUID.randomUUID())).isEmpty();
        PageData<ApplicationSharedSubscription> page = service.getSharedSubscriptions(new PageLink(10));
        assertThat(page.getData()).isEmpty();
        assertThat(page.getTotalElements()).isZero();
        assertThat(page.hasNext()).isFalse();
    }

}
