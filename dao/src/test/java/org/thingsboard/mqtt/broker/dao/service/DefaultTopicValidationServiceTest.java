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
package org.thingsboard.mqtt.broker.dao.service;

import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.thingsboard.mqtt.broker.dao.exception.DataValidationException;

import java.nio.charset.StandardCharsets;
import java.util.Random;

public class DefaultTopicValidationServiceTest {

    private DefaultTopicValidationService topicValidationService;

    @Before
    public void init() {
        this.topicValidationService = new DefaultTopicValidationService();
        this.topicValidationService.setMaxSegmentsCount(10_000);
    }

    @Test(expected = DataValidationException.class)
    public void test$InTopic() {
        topicValidationService.validateTopic("$/test/#");
    }

    @Test(expected = DataValidationException.class)
    public void testMultiWildcardsInTopic() {
        topicValidationService.validateTopic("1/test/#");
    }

    @Test(expected = DataValidationException.class)
    public void testSingleWildcardsInTopic() {
        topicValidationService.validateTopic("1/test/+/1");
    }

    @Test(expected = DataValidationException.class)
    public void testMultiWildcardNotInTheEnd() {
        topicValidationService.validateTopicFilter("1/test/#/1");
    }

    @Test(expected = DataValidationException.class)
    public void testMultiWildcardNotAfterSeparator() {
        topicValidationService.validateTopicFilter("1/test/1#");
    }

    @Test(expected = DataValidationException.class)
    public void testSingleWildcardNotWholeLevel_1() {
        topicValidationService.validateTopicFilter("1/test/1+/123");
    }

    @Test(expected = DataValidationException.class)
    public void testSingleWildcardNotWholeLevel_2() {
        topicValidationService.validateTopicFilter("1/+test/123");
    }

    @Test(expected = DataValidationException.class)
    public void testEmptyTopic() {
        topicValidationService.validateTopic("");
    }

    @Test(expected = DataValidationException.class)
    public void testEmptyFilter() {
        topicValidationService.validateTopicFilter("");
    }

    @Test(expected = DataValidationException.class)
    public void testNullCharTopic() {
        topicValidationService.validateTopic("1/test\u0000/1");
    }

    @Test(expected = DataValidationException.class)
    public void testNullCharFilter() {
        topicValidationService.validateTopicFilter("1/test\u0000/1");
    }

    @Test(expected = DataValidationException.class)
    public void testLargeTopic() {
        String largeTopic = generateLargeTopic();
        topicValidationService.validateTopic(largeTopic);
    }

    @Test(expected = DataValidationException.class)
    public void testLargeFilter() {
        String largeTopic = generateLargeTopic();
        topicValidationService.validateTopicFilter(largeTopic);
    }

    private String generateLargeTopic() {
        Random r = new Random();
        StringBuilder builder = new StringBuilder(DefaultTopicValidationService.MAX_SIZE_BYTES + 1);
        for (int i = 0; i < DefaultTopicValidationService.MAX_SIZE_BYTES + 1; i++) {
            if (i % 10 == 0) {
                builder.append('/');
            } else {
                builder.append((char) (r.nextInt(26) + 'a'));
            }
        }
        return builder.toString();
    }

    @Test(expected = DataValidationException.class)
    public void testTooManySegmentsTopic() {
        int maxSegmentsSize = 10;
        topicValidationService.setMaxSegmentsCount(maxSegmentsSize);
        String topic = generateManySegmentsTopic(maxSegmentsSize);
        topicValidationService.validateTopic(topic);
    }

    @Test(expected = DataValidationException.class)
    public void testTooManySegmentsFilter() {
        int maxSegmentsSize = 10;
        topicValidationService.setMaxSegmentsCount(maxSegmentsSize);
        String topic = generateManySegmentsTopic(maxSegmentsSize);
        topicValidationService.validateTopicFilter(topic);
    }

    private String generateManySegmentsTopic(int maxSegmentsSize) {
        Random r = new Random();
        StringBuilder builder = new StringBuilder();
        for (int i = 0; i < maxSegmentsSize + 1; i++) {
            builder.append((char) (r.nextInt(26) + 'a'));
            builder.append('/');
        }
        return builder.toString();
    }

    @Test
    public void testValidFilterSize() {
        String largeTopic = generateLargeTopic();
        String normalTopic = largeTopic.substring(0, DefaultTopicValidationService.MAX_SIZE_BYTES);
        Assert.assertTrue(normalTopic.getBytes(StandardCharsets.UTF_8).length <= DefaultTopicValidationService.MAX_SIZE_BYTES);
        topicValidationService.validateTopicFilter(normalTopic);
    }

    @Test
    public void testValidTopics() {
        topicValidationService.validateTopicFilter("1/test/42/dse1/557f");
    }

    @Test
    public void testValidFilters() {
        topicValidationService.validateTopicFilter("+/test/+/dse1/#");
    }

}
