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
package org.thingsboard.mqtt.broker.service.testing.integration;

import com.fasterxml.jackson.databind.node.TextNode;
import lombok.extern.slf4j.Slf4j;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootContextLoader;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.junit4.SpringRunner;
import org.thingsboard.mqtt.broker.common.stats.StatsType;
import org.thingsboard.mqtt.broker.service.stats.DefaultRestPublishStats;
import org.thingsboard.mqtt.broker.dao.DaoSqlTest;
import org.thingsboard.mqtt.broker.dto.PayloadEncoding;
import org.thingsboard.mqtt.broker.dto.RestPublishRequest;
import org.thingsboard.mqtt.broker.exception.TbRateLimitsException;
import org.thingsboard.mqtt.broker.service.mqtt.publish.RestPublishOutcome;
import org.thingsboard.mqtt.broker.service.mqtt.publish.RestPublishService;

import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * REST publishes are charged against the same total incoming throughput quota as client PUBLISH packets. The
 * controller's mapping of {@link TbRateLimitsException} to 429 is covered by {@code MqttPublishControllerTest};
 * here the service is driven directly against a drained shared bucket.
 */
@Slf4j
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
@ContextConfiguration(classes = TotalThroughputQuotaRestPublishIntegrationTestCase.class, loader = SpringBootContextLoader.class)
@TestPropertySource(properties = {
        "mqtt.rate-limits.total.config=5:600",
        "mqtt.rate-limits.total.block-size=1"
})
@DaoSqlTest
@RunWith(SpringRunner.class)
public class TotalThroughputQuotaRestPublishIntegrationTestCase extends AbstractTotalThroughputQuotaIntegrationTest {

    @Autowired
    private RestPublishService restPublishService;

    @Before
    public void emptySharedBucket() {
        ensureSharedBucketEmpty();
    }

    @Test
    public void givenQuotaExhausted_whenRestPublish_thenRateLimitsExceptionAndCountersMove() throws Exception {
        double droppedBefore = droppedMsgs();
        double quotaExceededBefore = restPublishCount(RestPublishOutcome.QUOTA_EXCEEDED);
        double acceptedBefore = restPublishCount(RestPublishOutcome.ACCEPTED);

        // the shared bucket is empty, so only the node's warm-up block and its credit can pass: a few publishes are
        // accepted and then the quota refuses. Unpaced on purpose - the loop must outrun the local pool
        boolean refused = false;
        int accepted = 0;
        for (int i = 0; i < 50 && !refused; i++) {
            RestPublishRequest request = new RestPublishRequest();
            request.setTopic("quota/rest/publish");
            request.setPayload(new TextNode("data_" + i));
            request.setPayloadEncoding(PayloadEncoding.TEXT);
            try {
                restPublishService.publish(request).get(AWAIT_TIMEOUT_SEC, TimeUnit.SECONDS);
                accepted++;
            } catch (TbRateLimitsException e) {
                refused = true;
            }
        }

        assertThat(refused).as("the drained quota must refuse a REST publish").isTrue();
        // one drop for the refusal, plus one per accepted publish: nobody subscribes to the topic, so the publish
        // consumer counts each of them as dropped too, asynchronously
        awaitDroppedMsgsAtLeast("refusal and no-subscriber drops counted", droppedBefore + 1 + accepted);
        assertThat(droppedMsgs()).isEqualTo(droppedBefore + 1 + accepted);
        assertThat(restPublishCount(RestPublishOutcome.QUOTA_EXCEEDED)).isEqualTo(quotaExceededBefore + 1);
        assertThat(restPublishCount(RestPublishOutcome.ACCEPTED)).isEqualTo(acceptedBefore + accepted);
    }

    private double restPublishCount(RestPublishOutcome outcome) {
        return meterRegistry.get(StatsType.REST_PUBLISH_MSGS.getPrintName()).tag(DefaultRestPublishStats.RESULT_TAG, outcome.getTagValue()).counter().count();
    }

}
