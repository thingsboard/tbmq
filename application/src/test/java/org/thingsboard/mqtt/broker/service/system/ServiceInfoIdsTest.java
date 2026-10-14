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
package org.thingsboard.mqtt.broker.service.system;

import org.junit.jupiter.api.Test;
import org.thingsboard.mqtt.broker.service.system.ServiceInfoIds.RegistryEntry;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.thingsboard.mqtt.broker.common.data.BrokerConstants.UNKNOWN;
import static org.thingsboard.mqtt.broker.common.data.queue.ServiceType.TBMQ;
import static org.thingsboard.mqtt.broker.common.data.queue.ServiceType.TBMQ_INTEGRATION_EXECUTOR;

class ServiceInfoIdsTest {

    @Test
    void givenServiceTypes_whenRegistryField_thenBrokersStayBareAndExecutorsArePrefixed() {
        assertEquals("node1", ServiceInfoIds.registryField("node1", TBMQ.name()));
        assertEquals("TBMQ_INTEGRATION_EXECUTOR:node1", ServiceInfoIds.registryField("node1", TBMQ_INTEGRATION_EXECUTOR.name()));
    }

    @Test
    void givenServiceTypes_whenTimeseriesEntityId_thenItFollowsTheRegistryField() {
        assertEquals("node1", ServiceInfoIds.timeseriesEntityId("node1", TBMQ.name()));
        assertEquals("TBMQ_INTEGRATION_EXECUTOR:node1", ServiceInfoIds.timeseriesEntityId("node1", TBMQ_INTEGRATION_EXECUTOR.name()));
    }

    @Test
    void givenRegistryFields_whenParsed_thenTypedAndBareFieldsAreUnderstood() {
        assertEquals(new RegistryEntry("node1", TBMQ.name()), ServiceInfoIds.parseRegistryField("node1", TBMQ.name()));
        assertEquals(new RegistryEntry("node1", TBMQ_INTEGRATION_EXECUTOR.name()),
                ServiceInfoIds.parseRegistryField("TBMQ_INTEGRATION_EXECUTOR:node1", TBMQ_INTEGRATION_EXECUTOR.name()));
        assertEquals(new RegistryEntry("host:1883", TBMQ.name()), ServiceInfoIds.parseRegistryField("host:1883", TBMQ.name()));
        assertEquals(new RegistryEntry("node1", UNKNOWN), ServiceInfoIds.parseRegistryField("node1", null));
    }

    @Test
    void givenRegistryField_whenParsedBack_thenItRoundTrips() {
        for (String type : new String[]{TBMQ.name(), TBMQ_INTEGRATION_EXECUTOR.name()}) {
            assertEquals(new RegistryEntry("a:b", type), ServiceInfoIds.parseRegistryField(ServiceInfoIds.registryField("a:b", type), type));
        }
    }
}
