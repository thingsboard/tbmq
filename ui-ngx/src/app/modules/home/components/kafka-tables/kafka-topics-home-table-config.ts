///
/// Copyright © 2016-2025 The Thingsboard Authors
///
/// Licensed under the Apache License, Version 2.0 (the "License");
/// you may not use this file except in compliance with the License.
/// You may obtain a copy of the License at
///
///     http://www.apache.org/licenses/LICENSE-2.0
///
/// Unless required by applicable law or agreed to in writing, software
/// distributed under the License is distributed on an "AS IS" BASIS,
/// WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
/// See the License for the specific language governing permissions and
/// limitations under the License.
///

import { EntityTableColumn, EntityTableConfig, formatBytes } from '@home/models/entity/entities-table-config.models';
import { TimePageLink } from '@shared/models/page/page-link';
import { Observable } from 'rxjs';
import { PageData } from '@shared/models/page/page-data';
import { KafkaTopic, KafkaTopicsTooltipMap } from '@shared/models/kafka.model';
import { KafkaService } from '@core/http/kafka.service';
import { EntityType, entityTypeResources, entityTypeTranslations } from '@shared/models/entity-type.models';
import { TranslateService } from '@ngx-translate/core';

export class KafkaTopicsHomeTableConfig extends EntityTableConfig<KafkaTopic, TimePageLink> {

  constructor(private kafkaService: KafkaService,
              private translate: TranslateService,
              public entityId: string = null) {
    super();
    this.entityType = EntityType.KAFKA_TOPIC;
    this.entityTranslations = entityTypeTranslations.get(EntityType.KAFKA_TOPIC);
    this.entityResources = entityTypeResources.get(EntityType.KAFKA_TOPIC);
    this.tableTitle = this.translate.instant('kafka.topics');
    this.entityComponent = null;
    this.detailsPanelEnabled = false;
    this.selectionEnabled = false;
    this.addEnabled = false;
    this.entitiesDeleteEnabled = false;

    this.entitiesFetchFunction = pageLink => this.fetchKafkaTopics(pageLink);

    this.columns.push(
      new EntityTableColumn<KafkaTopic>('name', 'kafka.name', '70%', undefined, undefined,
        undefined, undefined, (entity) => this.showKafkaTopicTooltip(entity)),
      new EntityTableColumn<KafkaTopic>('partitions', 'kafka.partitions', '10%',
        entity => entity.partitions.toString(),
        () => ({color: 'rgba(0,0,0,0.54)'})),
      new EntityTableColumn<KafkaTopic>('replicationFactor', 'kafka.replicas', '10%',
        entity => entity.replicationFactor.toString(),
        () => ({color: 'rgba(0,0,0,0.54)'})),
      new EntityTableColumn<KafkaTopic>('size', 'kafka.size', '10%',
          entity => formatBytes(entity.size),
        () => ({color: 'rgba(0,0,0,0.54)'}))
    );
  }

  private fetchKafkaTopics(pageLink: TimePageLink): Observable<PageData<KafkaTopic>> {
    return this.kafkaService.getKafkaTopics(pageLink);
  }

  private showKafkaTopicTooltip(entity: KafkaTopic) {
    if (entity?.name) {
      const rowName = entity.name;
      for (let key in KafkaTopicsTooltipMap) {
        if (rowName.includes('tbmq.msg.app')) {
          if (rowName.includes('tbmq.msg.app.shared')) {
            return KafkaTopicsTooltipMap['tbmq.msg.app.shared'];
          } else {
            return KafkaTopicsTooltipMap['tbmq.msg.app'];
          }
        }
        if (rowName.includes('tbmq.client.session')) {
          if (rowName.includes('tbmq.client.session.event.response')) {
            return KafkaTopicsTooltipMap['tbmq.client.session.event.response'];
          } else if (rowName.includes('tbmq.client.session.event.request')) {
            return KafkaTopicsTooltipMap['tbmq.client.session.event.request'];
          } else {
            return KafkaTopicsTooltipMap['tbmq.client.session'];
          }
        }
        if (rowName.includes(key)) {
          return KafkaTopicsTooltipMap[key];
        }
      }
    }
    return undefined;
  }
}
