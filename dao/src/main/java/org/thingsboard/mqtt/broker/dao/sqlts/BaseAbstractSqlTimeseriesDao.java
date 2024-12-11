/**
 * Copyright © 2016-2024 The Thingsboard Authors
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

import com.google.common.base.Function;
import com.google.common.util.concurrent.Futures;
import com.google.common.util.concurrent.ListenableFuture;
import jakarta.annotation.Nullable;
import lombok.extern.slf4j.Slf4j;
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
        return Futures.transform(future, new Function<>() {
            @Nullable
            @Override
            public List<TsKvEntry> apply(@Nullable List<Optional<? extends AbstractTsKvEntity>> results) {
                if (results == null || results.isEmpty()) {
                    return null;
                }
                List<? extends AbstractTsKvEntity> data = results.stream().filter(Optional::isPresent).map(Optional::get).collect(Collectors.toList());
                if (order.equals("DESC")) {
                    Collections.reverse(data);
                }
                return DaoUtil.convertDataList(data);
            }
        }, service);
    }

}
