/**
 * ThingsBoard, Inc. ("COMPANY") CONFIDENTIAL
 *
 * Copyright © 2016-2026 ThingsBoard, Inc. All Rights Reserved.
 *
 * NOTICE: All information contained herein is, and remains
 * the property of ThingsBoard, Inc. and its suppliers,
 * if any.  The intellectual and technical concepts contained
 * herein are proprietary to ThingsBoard, Inc.
 * and its suppliers and may be covered by U.S. and Foreign Patents,
 * patents in process, and are protected by trade secret or copyright law.
 *
 * Dissemination of this information or reproduction of this material is strictly forbidden
 * unless prior written permission is obtained from COMPANY.
 *
 * Access to the source code contained herein is hereby forbidden to anyone except current COMPANY employees,
 * managers or contractors who have executed Confidentiality and Non-disclosure agreements
 * explicitly covering such access.
 *
 * The copyright notice above does not evidence any actual or intended publication
 * or disclosure  of  this source code, which includes
 * information that is confidential and/or proprietary, and is a trade secret, of  COMPANY.
 * ANY REPRODUCTION, MODIFICATION, DISTRIBUTION, PUBLIC  PERFORMANCE,
 * OR PUBLIC DISPLAY OF OR THROUGH USE  OF THIS  SOURCE CODE  WITHOUT
 * THE EXPRESS WRITTEN CONSENT OF COMPANY IS STRICTLY PROHIBITED,
 * AND IN VIOLATION OF APPLICABLE LAWS AND INTERNATIONAL TREATIES.
 * THE RECEIPT OR POSSESSION OF THIS SOURCE CODE AND/OR RELATED INFORMATION
 * DOES NOT CONVEY OR IMPLY ANY RIGHTS TO REPRODUCE, DISCLOSE OR DISTRIBUTE ITS CONTENTS,
 * OR TO MANUFACTURE, USE, OR SELL ANYTHING THAT IT  MAY DESCRIBE, IN WHOLE OR IN PART.
 */
package org.thingsboard.mqtt.broker.controller;

import com.fasterxml.jackson.databind.JsonNode;
import org.junit.Before;
import org.junit.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.thingsboard.mqtt.broker.common.data.kv.BasicTsKvEntry;
import org.thingsboard.mqtt.broker.common.data.kv.LongDataEntry;
import org.thingsboard.mqtt.broker.dao.DaoSqlTest;
import org.thingsboard.mqtt.broker.dao.timeseries.TimeseriesService;

import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.thingsboard.mqtt.broker.common.data.BrokerConstants.CPU_COUNT;
import static org.thingsboard.mqtt.broker.common.data.BrokerConstants.CPU_USAGE;
import static org.thingsboard.mqtt.broker.common.data.BrokerConstants.MEMORY_USAGE;

@DaoSqlTest
public class TimeseriesControllerTest extends AbstractControllerTest {

    private static final String BATCH_URL = "/api/timeseries/latest/batch";
    private static final String BROKER_ID = "ts-batch-broker";
    private static final String EXECUTOR_ID = "TBMQ_INTEGRATION_EXECUTOR:ts-batch-broker";

    @Autowired
    private TimeseriesService timeseriesService;

    private long ts;

    @Before
    public void beforeTest() throws Exception {
        ts = System.currentTimeMillis();
        timeseriesService.save(BROKER_ID, List.of(
                new BasicTsKvEntry(ts, new LongDataEntry(CPU_USAGE, 11L)),
                new BasicTsKvEntry(ts, new LongDataEntry(MEMORY_USAGE, 22L)))).get();
        timeseriesService.saveLatest(EXECUTOR_ID, List.of(
                new BasicTsKvEntry(ts, new LongDataEntry(CPU_USAGE, 77L)),
                new BasicTsKvEntry(ts, new LongDataEntry(CPU_COUNT, 4L)))).get();
        loginSysAdmin();
    }

    @Test
    public void givenSeveralEntities_whenGetLatestBatchWithKeys_thenEachEntityGetsTheLatestShape() throws Exception {
        JsonNode response = doGetAsync(BATCH_URL + "?entityIds={ids}&keys={keys}", JsonNode.class,
                BROKER_ID + "," + EXECUTOR_ID, CPU_USAGE + "," + MEMORY_USAGE);

        assertThat(response.fieldNames()).toIterable().containsExactly(BROKER_ID, EXECUTOR_ID);
        assertThat(response.at("/" + BROKER_ID + "/" + CPU_USAGE + "/0/value").asLong()).isEqualTo(11L);
        assertThat(response.at("/" + BROKER_ID + "/" + CPU_USAGE + "/0/ts").asLong()).isEqualTo(ts);
        assertThat(response.at("/" + BROKER_ID + "/" + MEMORY_USAGE + "/0/value").asLong()).isEqualTo(22L);
        JsonNode executor = response.get(EXECUTOR_ID);
        assertThat(executor.at("/" + CPU_USAGE + "/0/value").asLong()).isEqualTo(77L);
        assertThat(executor.at("/" + CPU_USAGE + "/0/value").isNumber()).isTrue();
        assertThat(executor.has(MEMORY_USAGE)).isFalse();
        assertThat(executor.has(CPU_COUNT)).isFalse();
    }

    @Test
    public void givenNoKeys_whenGetLatestBatch_thenEveryLatestKeyIsReturned() throws Exception {
        JsonNode response = doGetAsync(BATCH_URL + "?entityIds={ids}", JsonNode.class, EXECUTOR_ID);

        assertThat(response.get(EXECUTOR_ID).fieldNames()).toIterable().containsExactlyInAnyOrder(CPU_USAGE, CPU_COUNT);
    }

    @Test
    public void givenNonStrictDataTypes_whenGetLatestBatch_thenValuesAreStrings() throws Exception {
        JsonNode response = doGetAsync(BATCH_URL + "?entityIds={ids}&keys={keys}&useStrictDataTypes=false", JsonNode.class,
                BROKER_ID, CPU_USAGE);

        assertThat(response.at("/" + BROKER_ID + "/" + CPU_USAGE + "/0/value").isTextual()).isTrue();
        assertThat(response.at("/" + BROKER_ID + "/" + CPU_USAGE + "/0/value").asText()).isEqualTo("11");
    }

    @Test
    public void givenUnknownEntity_whenGetLatestBatchWithoutKeys_thenItMapsToAnEmptyObject() throws Exception {
        JsonNode response = doGetAsync(BATCH_URL + "?entityIds={ids}", JsonNode.class, "ts-batch-unknown," + BROKER_ID);

        assertThat(response.get("ts-batch-unknown").isEmpty()).isTrue();
        assertThat(response.get(BROKER_ID).has(CPU_USAGE)).isTrue();
    }

    @Test
    public void givenBlankAndRepeatedIds_whenGetLatestBatch_thenEachIdIsAnsweredOnce() throws Exception {
        JsonNode response = doGetAsync(BATCH_URL + "?entityIds={ids}&keys={keys}", JsonNode.class,
                BROKER_ID + ",," + BROKER_ID, CPU_USAGE);

        assertThat(response.fieldNames()).toIterable().containsExactly(BROKER_ID);
        assertThat(doGetAsync(BATCH_URL + "?entityIds={ids}", JsonNode.class, ",").isEmpty()).isTrue();
    }

    @Test
    public void givenTooManyIds_whenGetLatestBatch_thenRejected() throws Exception {
        String ids = IntStream.rangeClosed(0, TimeseriesController.MAX_BATCH_ENTITY_IDS)
                .mapToObj(i -> "ts-batch-" + i)
                .collect(Collectors.joining(","));

        doGet(BATCH_URL + "?entityIds=" + ids).andExpect(status().isBadRequest());
    }

}
