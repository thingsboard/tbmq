///
/// Copyright © 2016-2026 The Thingsboard Authors
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

import { Component, Input, OnInit, input, viewChild } from '@angular/core';
import { EntitiesTableComponent } from '@home/components/entity/entities-table.component';
import { KafkaService } from '@core/http/kafka.service';
import { KafkaConsumerGroupsTableConfig } from '@home/components/kafka-tables/kafka-consumer-groups-table-config';
import { TranslateService } from '@ngx-translate/core';
import { DialogService } from '@core/services/dialog.service';

@Component({
    selector: 'tb-kafka-consumer-groups-table',
    templateUrl: './kafka-consumer-groups-table.component.html',
    imports: [EntitiesTableComponent]
})
export class KafkaConsumerGroupsTableComponent implements OnInit {

  readonly detailsMode = input<boolean>();

  activeValue = false;
  dirtyValue = false;
  entityIdValue: string;

  @Input()
  set active(active: boolean) {
    if (this.activeValue !== active) {
      this.activeValue = active;
      if (this.activeValue && this.dirtyValue) {
        this.dirtyValue = false;
        this.entitiesTable().updateData();
      }
    }
  }

  @Input()
  set entityId(entityId: string) {
    this.entityIdValue = entityId;
    if (this.kafkaConsumerGroupsTableConfig && this.kafkaConsumerGroupsTableConfig.entityId !== entityId) {
      this.kafkaConsumerGroupsTableConfig.entityId = entityId;
      this.entitiesTable().resetSortAndFilter(this.activeValue);
      if (!this.activeValue) {
        this.dirtyValue = true;
      }
    }
  }

  readonly entitiesTable = viewChild(EntitiesTableComponent);

  kafkaConsumerGroupsTableConfig: KafkaConsumerGroupsTableConfig;

  constructor(private kafkaService: KafkaService,
              private dialogService: DialogService,
              private translate: TranslateService) {
  }

  ngOnInit(): void {
    this.dirtyValue = !this.activeValue;
    this.kafkaConsumerGroupsTableConfig = new KafkaConsumerGroupsTableConfig(
      this.kafkaService,
      this.translate,
      this.dialogService,
      this.entityIdValue
    );
  }

}
