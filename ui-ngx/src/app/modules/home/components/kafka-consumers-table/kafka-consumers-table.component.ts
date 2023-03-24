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

import { Component } from '@angular/core';
import { EntityColumn, EntityTableColumn } from '@home/models/entity/entities-table-config.models';
import { DomSanitizer } from '@angular/platform-browser';
import { KafkaService } from '@core/http/kafka.service';
import { KafkaConsumerGroup } from '@shared/models/kafka.model';
import { KafkaTableComponent } from '@home/components/entity/kafka-table.component';

@Component({
  selector: 'tb-kafka-consumers-table',
  templateUrl: './kafka-consumers-table.component.html',
  styleUrls: ['./kafka-consumers-table.component.scss']
})
export class KafkaConsumersTableComponent extends KafkaTableComponent<KafkaConsumerGroup> {

  fetchEntities$ = () => this.kafkaService.getKafkaConsumerGroups(this.pageLink);

  constructor(private kafkaService: KafkaService,
              protected domSanitizer: DomSanitizer) {
    super(domSanitizer);
  }

  getColumns() {
    const columns: Array<EntityColumn<KafkaConsumerGroup>> = [];
    columns.push(
      new EntityTableColumn<KafkaConsumerGroup>('groupId', 'kafka.id', '70%'),
      new EntityTableColumn<KafkaConsumerGroup>('state', 'kafka.state', '10%'),
      new EntityTableColumn<KafkaConsumerGroup>('members', 'kafka.members', '10%'),
      new EntityTableColumn<KafkaConsumerGroup>('lag', 'kafka.lag', '10%', entity => entity.lag)
    );
    return columns;
  }
}
