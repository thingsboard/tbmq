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

import { ChangeDetectorRef, Component, OnInit } from '@angular/core';
import { KafkaTable, KafkaTableTranslationMap } from '@shared/models/kafka.model';
import { KafkaTopicsTableConfig } from '@home/components/kafka-tables/kafka-topics-table-config';
import { KafkaService } from '@core/http/kafka.service';
import { KafkaConsumerGroupsTableConfig } from '@home/components/kafka-tables/kafka-consumer-groups-table-config';

@Component({
  selector: 'tb-kafka-tables-tab-group',
  templateUrl: './kafka-tables-tab-group.component.html',
  styleUrls: ['./kafka-tables-tab-group.component.scss']
})
export class KafkaTablesTabGroupComponent implements OnInit {

  public kafkaTopicsTableConfig: KafkaTopicsTableConfig;
  public kafkaConsumerGroupsTableConfig: KafkaConsumerGroupsTableConfig;
  public selectedTab: KafkaTable;
  public readonly KafkaTable = KafkaTable;
  public readonly kafkaTableTranslationMap = KafkaTableTranslationMap;

  constructor(private kafkaService: KafkaService,
              private cd: ChangeDetectorRef) {
  }

  ngOnInit() {
    this.selectedTab = KafkaTable.TOPICS;
    this.kafkaTopicsTableConfig = new KafkaTopicsTableConfig(this.kafkaService);
    this.kafkaConsumerGroupsTableConfig = new KafkaConsumerGroupsTableConfig(this.kafkaService);
  }

  select(table: KafkaTable) {
    this.selectedTab = table;
    this.cd.detectChanges();
  }
}