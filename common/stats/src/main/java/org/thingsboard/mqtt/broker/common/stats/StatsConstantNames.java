/**
 * Copyright © 2016-2020 The Thingsboard Authors
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
package org.thingsboard.mqtt.broker.common.stats;

public class StatsConstantNames {
    public static final String TOTAL_MSGS = "totalMsgs";
    public static final String SUCCESSFUL_MSGS = "successfulMsgs";
    public static final String FAILED_MSGS = "failedMsgs";
    public static final String TIMEOUT_MSGS = "timeoutMsgs";

    public static final String TMP_TIMEOUT = "tmpTimeout";
    public static final String TMP_FAILED = "tmpFailed";

    public static final String SUCCESSFUL_ITERATIONS = "successfulIterations";
    public static final String FAILED_ITERATIONS = "failedIterations";


    public static final String STATS_NAME_TAG = "statsName";
}
