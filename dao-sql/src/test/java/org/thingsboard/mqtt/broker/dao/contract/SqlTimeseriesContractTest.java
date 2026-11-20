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
package org.thingsboard.mqtt.broker.dao.contract;

import org.junit.After;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.support.AnnotationConfigContextLoader;
import org.thingsboard.mqtt.broker.dao.DaoSqlTest;
import org.thingsboard.mqtt.broker.dao.sqlts.sql.JpaSqlTimeseriesDao;

@DaoSqlTest
@ContextConfiguration(classes = SqlContractTestConfig.class, loader = AnnotationConfigContextLoader.class)
public class SqlTimeseriesContractTest extends AbstractTimeseriesContractTest {

    @Autowired
    private JpaSqlTimeseriesDao jpaSqlTimeseriesDao;

    // A clean-up drops the partitions older than the TTL, which the DAO still has cached: a later save into one of them
    // would find no partition. Forget them, as the SQL time series integration tests do.
    @After
    public void clearPartitionsCache() {
        jpaSqlTimeseriesDao.clearPartitionsCache();
    }

}
