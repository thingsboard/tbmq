///
/// Copyright © 2016-2023 The Thingsboard Authors
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

import { EntityTableColumn, EntityTableConfig } from '@home/models/entity/entities-table-config.models';
import { TimePageLink } from '@shared/models/page/page-link';
import { Observable } from 'rxjs';
import { PageData } from '@shared/models/page/page-data';
import { KafkaTopic } from '@shared/models/kafka.model';
import { KafkaService } from '@core/http/kafka.service';
import { formatBytes } from '@home/components/entity/entities-table-home.component';

export class KafkaTopicsTableConfig extends EntityTableConfig<KafkaTopic, TimePageLink> {

  constructor(private kafkaService: KafkaService,
              public entityId: string = null) {
    super();
    this.detailsPanelEnabled = false;
    this.selectionEnabled = false;
    this.addEnabled = false;
    this.entitiesDeleteEnabled = false;
    this.entityTranslations = {
      noEntities: 'kafka.no-kafka-topic-text',
      search: 'kafka.topics-search'
    };

    this.entitiesFetchFunction = pageLink => this.fetchKafkaTopics(pageLink);

    this.columns.push(
      new EntityTableColumn<KafkaTopic>('name', 'kafka.name', '70%'),
      new EntityTableColumn<KafkaTopic>('partitions', 'kafka.partitions', '10%',
        undefined, () => ({color: 'rgba(0,0,0,0.54)'})),
      new EntityTableColumn<KafkaTopic>('replicationFactor', 'kafka.replicas', '10%',
        undefined, () => ({color: 'rgba(0,0,0,0.54)'})),
      new EntityTableColumn<KafkaTopic>('size', 'kafka.size', '10%',
          entity => formatBytes(entity.size),
        () => ({color: 'rgba(0,0,0,0.54)'})
      )
    );
  }

  private fetchKafkaTopics(pageLink: TimePageLink): Observable<PageData<KafkaTopic>> {
    return this.kafkaService.getKafkaTopics(pageLink);
  }
}
