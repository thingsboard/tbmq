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
package org.thingsboard.mqtt.broker.dao.sqlts;

import com.google.common.util.concurrent.Futures;
import com.google.common.util.concurrent.ListenableFuture;
import lombok.extern.slf4j.Slf4j;
import org.springframework.util.CollectionUtils;
import org.thingsboard.mqtt.broker.common.data.BrokerConstants;
import org.thingsboard.mqtt.broker.common.data.kv.TsKvEntry;
import org.thingsboard.mqtt.broker.dao.DaoUtil;
import org.thingsboard.mqtt.broker.dao.JpaAbstractDaoListeningExecutorService;
import org.thingsboard.mqtt.broker.dao.model.sqlts.AbstractTsKvEntity;

import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

@Slf4j
public abstract class BaseAbstractSqlTimeseriesDao extends JpaAbstractDaoListeningExecutorService {

    protected ListenableFuture<List<TsKvEntry>> getTsKvEntriesFuture(ListenableFuture<List<Optional<? extends AbstractTsKvEntity>>> future,
                                                                     String order) {
        return Futures.transform(future, results -> {
            if (CollectionUtils.isEmpty(results)) {
                return null;
            }
            return DaoUtil.convertDataList(collectWithOrder(results, order));
        }, service);
    }

    private List<? extends AbstractTsKvEntity> collectWithOrder(List<Optional<? extends AbstractTsKvEntity>> results,
                                                                String order) {
        List<? extends AbstractTsKvEntity> data = collectData(results);
        if (BrokerConstants.DESC_ORDER.equals(order)) {
            Collections.reverse(data);
        }
        return data;
    }

    private List<? extends AbstractTsKvEntity> collectData(List<Optional<? extends AbstractTsKvEntity>> results) {
        return results
                .stream()
                .filter(Optional::isPresent)
                .map(Optional::get)
                .collect(Collectors.toList());
    }

}
