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

import { ChangeDetectionStrategy, Component } from '@angular/core';
import { EntityColumn, EntityTableColumn } from '@home/models/entity/entities-table-config.models';
import { DomSanitizer } from '@angular/platform-browser';
import { KafkaService } from '@core/http/kafka.service';
import { KafkaTopic } from '@shared/models/kafka.model';
import { KafkaTableComponent } from '@home/components/entity/kafka-table.component';

@Component({
  selector: 'tb-kafka-topics-table',
  templateUrl: './kafka-topics-table.component.html',
  styleUrls: ['./kafka-topics-table.component.scss'],
  changeDetection: ChangeDetectionStrategy.OnPush
})
export class KafkaTopicsTableComponent extends KafkaTableComponent<KafkaTopic> {

  fetchEntities$ = () => this.kafkaService.getKafkaTopics(this.pageLink);

  constructor(private kafkaService: KafkaService,
              protected domSanitizer: DomSanitizer) {
    super(domSanitizer);
  }

  getColumns() {
    const columns: Array<EntityColumn<KafkaTopic>> = [];
    columns.push(
      new EntityTableColumn<KafkaTopic>('name', 'kafka.name', '70%'),
      new EntityTableColumn<KafkaTopic>('partitions', 'kafka.partitions', '10%'),
      new EntityTableColumn<KafkaTopic>('replicationFactor', 'kafka.replicas', '10%'),
      new EntityTableColumn<KafkaTopic>('size', 'kafka.size', '10%', entity => {
        return this.formatBytes(entity.size);
      })
    );
    return columns;
  }
}
