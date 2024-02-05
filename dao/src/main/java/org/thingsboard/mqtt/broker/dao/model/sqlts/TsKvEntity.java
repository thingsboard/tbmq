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
package org.thingsboard.mqtt.broker.dao.model.sqlts;

import lombok.Data;
import lombok.EqualsAndHashCode;

import javax.persistence.Entity;
import javax.persistence.IdClass;
import javax.persistence.Table;

@EqualsAndHashCode(callSuper = true)
@Data
@Entity
@Table(name = "ts_kv")
@IdClass(TsKvCompositeKey.class)
public final class TsKvEntity extends AbstractTsKvEntity {

    public TsKvEntity() {
    }

    public TsKvEntity(Long longValueCount) {
        if (!isAllNull(longValueCount)) {
            this.longValue = longValueCount;
        }
    }

    public TsKvEntity(Long longValue, Long longCountValue, String aggType) {
        if (!isAllNull(longValue, longCountValue)) {
            switch (aggType) {
                case AVG:
                    double sum = 0.0;
                    if (longValue != null) {
                        sum += longValue;
                    }
                    if (longCountValue > 0) {
                        this.doubleValue = sum / longCountValue;
                    } else {
                        this.doubleValue = 0.0;
                    }
                    break;
                case SUM:
                    this.longValue = longValue;
                    break;
                case MIN:
                case MAX:
                    if (longCountValue > 0) {
                        this.longValue = longValue;
                    }
                    break;
            }
        }
    }

    @Override
    public boolean isNotEmpty() {
        return longValue != null || doubleValue != null;
    }
}
