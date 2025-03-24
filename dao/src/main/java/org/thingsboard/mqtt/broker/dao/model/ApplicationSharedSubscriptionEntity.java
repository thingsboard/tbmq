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
package org.thingsboard.mqtt.broker.dao.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import lombok.Data;
import lombok.EqualsAndHashCode;
import org.thingsboard.mqtt.broker.common.data.ApplicationSharedSubscription;

import java.util.Collections;
import java.util.Map;

@Data
@EqualsAndHashCode(callSuper = true)
@Entity
@Table(name = ModelConstants.APPLICATION_SHARED_SUBSCRIPTION_COLUMN_FAMILY_NAME)
public class ApplicationSharedSubscriptionEntity extends BaseSqlEntity<ApplicationSharedSubscription> implements BaseEntity<ApplicationSharedSubscription> {

    public static final Map<String, String> appSharedSubscriptionColumnMap = Collections.singletonMap("topicFilter", "topic");

    @Column(name = ModelConstants.APPLICATION_SHARED_SUBSCRIPTION_TOPIC_PROPERTY, unique = true)
    private String topic;

    @Column(name = ModelConstants.APPLICATION_SHARED_SUBSCRIPTION_NAME_PROPERTY)
    private String name;

    @Column(name = ModelConstants.APPLICATION_SHARED_SUBSCRIPTION_PARTITIONS_PROPERTY)
    private Integer partitions;

    public ApplicationSharedSubscriptionEntity() {
    }

    public ApplicationSharedSubscriptionEntity(ApplicationSharedSubscription applicationSharedSubscription) {
        if (applicationSharedSubscription.getId() != null) {
            this.setId(applicationSharedSubscription.getId());
        }
        this.setCreatedTime(applicationSharedSubscription.getCreatedTime());
        this.name = applicationSharedSubscription.getName();
        this.topic = applicationSharedSubscription.getTopicFilter();
        this.partitions = applicationSharedSubscription.getPartitions();
    }

    @Override
    public ApplicationSharedSubscription toData() {
        ApplicationSharedSubscription applicationSharedSubscription = new ApplicationSharedSubscription();
        applicationSharedSubscription.setId(id);
        applicationSharedSubscription.setCreatedTime(createdTime);
        applicationSharedSubscription.setTopicFilter(topic);
        applicationSharedSubscription.setName(name);
        applicationSharedSubscription.setPartitions(partitions);
        return applicationSharedSubscription;
    }

}
