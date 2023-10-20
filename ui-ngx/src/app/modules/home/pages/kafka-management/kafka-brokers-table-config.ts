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

import { EntityTableColumn, EntityTableConfig, formatBytes } from '@home/models/entity/entities-table-config.models';
import { TimePageLink } from '@shared/models/page/page-link';
import { Observable } from 'rxjs';
import { PageData } from '@shared/models/page/page-data';
import { KafkaBroker } from '@shared/models/kafka.model';
import { KafkaService } from '@core/http/kafka.service';
import { EntityType } from '@shared/models/entity-type.models';
import { TranslateService } from '@ngx-translate/core';

export class KafkaBrokersTableConfig extends EntityTableConfig<KafkaBroker, TimePageLink> {

  constructor(private kafkaService: KafkaService,
              private translate: TranslateService,
              public entityId: string = null) {
    super();
    this.entityType = EntityType.KAFKA_BROKER;
    this.tableTitle = this.translate.instant('kafka.brokers')
    this.entityComponent = null;
    this.detailsPanelEnabled = false;
    this.selectionEnabled = false;
    this.addEnabled = false;
    this.entitiesDeleteEnabled = false;
    this.searchEnabled = false;
    this.entityTranslations = {
      noEntities: 'kafka.no-kafka-brokers-text',
      search: 'kafka.brokers-search'
    };

    this.entitiesFetchFunction = pageLink => this.fetchKafkaBrokers(pageLink);

    this.columns.push(
      new EntityTableColumn<KafkaBroker>('address', 'kafka.address', '80%'),
      new EntityTableColumn<KafkaBroker>('brokerSize', 'kafka.size', '20%',
        entity => formatBytes(entity.brokerSize),
        () => ({color: 'rgba(0,0,0,0.54)'}))
    );
  }

  private fetchKafkaBrokers(pageLink: TimePageLink): Observable<PageData<KafkaBroker>> {
    return this.kafkaService.getKafkaBrokers(pageLink);
  }
}
