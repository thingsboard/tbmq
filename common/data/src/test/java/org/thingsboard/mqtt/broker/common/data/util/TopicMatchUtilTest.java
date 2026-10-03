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
package org.thingsboard.mqtt.broker.common.data.util;

import org.junit.jupiter.api.Test;
import org.thingsboard.mqtt.broker.common.data.mqtt.dropped.MatchMode;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class TopicMatchUtilTest {

    @Test
    void modes() {
        assertTrue(TopicMatchUtil.matches(MatchMode.CONTAINS, "Sensors/A", "sens"));
        assertTrue(TopicMatchUtil.matches(null, "Sensors/A", "sens"));
        assertTrue(TopicMatchUtil.matches(MatchMode.EXACT, "a/b", "a/b"));
        assertFalse(TopicMatchUtil.matches(MatchMode.EXACT, "a/b", "A/b"));
        assertTrue(TopicMatchUtil.matches(MatchMode.PREFIX, "a/b", "a"));
        assertTrue(TopicMatchUtil.matches(MatchMode.PREFIX, "a", "a"));
        assertFalse(TopicMatchUtil.matches(MatchMode.PREFIX, "ab/c", "a"));
        assertTrue(TopicMatchUtil.matches(MatchMode.EXACT, "x", null));
    }

    @Test
    void emptyQueryIsAFilterWhenModeIsExplicit() {
        assertTrue(TopicMatchUtil.matches(MatchMode.PREFIX, "/x", ""));
        assertTrue(TopicMatchUtil.matches(MatchMode.PREFIX, "", ""));
        assertFalse(TopicMatchUtil.matches(MatchMode.PREFIX, "x", ""));
        assertTrue(TopicMatchUtil.matches(MatchMode.EXACT, "", ""));
        assertFalse(TopicMatchUtil.matches(MatchMode.EXACT, "x", ""));
        assertTrue(TopicMatchUtil.matches(null, "x", ""));
    }
}
