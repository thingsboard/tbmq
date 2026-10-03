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

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.thingsboard.mqtt.broker.common.data.exception.ThingsboardException;
import org.thingsboard.mqtt.broker.common.data.page.PageData;
import org.thingsboard.mqtt.broker.dto.TopicTreeNodeDto;
import org.thingsboard.mqtt.broker.service.topictree.TopicTreeService;
import org.thingsboard.mqtt.broker.service.topictree.TopicTreeSnapshotBuilderTest;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class TopicTreeControllerTest {

    TopicTreeService service = mock(TopicTreeService.class);
    TopicTreeController controller;

    @BeforeEach
    void setUp() {
        when(service.isEnabled()).thenReturn(true);
        when(service.getMaxPageSize()).thenReturn(2);
        when(service.getFilterMinLength()).thenReturn(2);
        when(service.getSnapshot()).thenReturn(new TopicTreeSnapshotBuilderTest().fixture());
        controller = new TopicTreeController(service, null);
    }

    @Test
    void childrenOmitsUnrequestedColumnsAndClampsPageSize() throws Exception {
        PageData<TopicTreeNodeDto> p = controller.doChildren(null, "", 100, 0, null, null);
        assertThat(p.getData()).hasSize(2);
        assertThat(p.getData().get(0).getClientsTotal()).isNull();
        assertThat(controller.doChildren(null, "clients", 2, 0, null, null).getData().get(0).getClientsTotal()).isNotNull();
    }

    @Test
    void unknownPathIs404() {
        assertThatThrownBy(() -> controller.doChildren("no/such", "", 10, 0, null, null))
                .isInstanceOf(ThingsboardException.class);
    }

    @Test
    void shortSearchIs400() {
        assertThatThrownBy(() -> controller.doSearch("a", "", 10, 0, null, null)).isInstanceOf(ThingsboardException.class);
    }

    @Test
    void disabledIs404() {
        when(service.isEnabled()).thenReturn(false);
        assertThatThrownBy(() -> controller.doChildren(null, "", 10, 0, null, null)).isInstanceOf(ThingsboardException.class);
    }
}
