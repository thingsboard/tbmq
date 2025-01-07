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

import { Component } from '@angular/core';
import { EntityColumn, EntityTableColumn, formatBytes } from '@home/models/entity/entities-table-config.models';
import { DomSanitizer } from '@angular/platform-browser';
import { KafkaService } from '@core/http/kafka.service';
import { KafkaBroker } from '@shared/models/kafka.model';
import { HomePageTitleType } from '@shared/models/home-page.model';
import { EntitiesTableHomeNoPagination } from '@home/components/entity/entities-table-no-pagination.component';
import { FlexModule } from '@angular/flex-layout/flex';
import { CardTitleButtonComponent } from '@shared/components/button/card-title-button.component';
import { MatTable, MatColumnDef, MatHeaderCellDef, MatHeaderCell, MatCellDef, MatCell, MatHeaderRowDef, MatHeaderRow, MatRowDef, MatRow } from '@angular/material/table';
import { NgFor, NgStyle } from '@angular/common';
import { ExtendedModule } from '@angular/flex-layout/extended';
import { TranslateModule } from '@ngx-translate/core';

@Component({
    selector: 'tb-kafka-brokers-table',
    templateUrl: './kafka-brokers-home-table.component.html',
    styleUrls: ['./kafka-brokers-home-table.component.scss'],
    standalone: true,
    imports: [FlexModule, CardTitleButtonComponent, MatTable, NgFor, MatColumnDef, MatHeaderCellDef, MatHeaderCell, MatCellDef, MatCell, NgStyle, ExtendedModule, MatHeaderRowDef, MatHeaderRow, MatRowDef, MatRow, TranslateModule]
})
export class KafkaBrokersHomeTableComponent extends EntitiesTableHomeNoPagination<KafkaBroker> {

  cardType = HomePageTitleType.KAFKA_BROKERS;
  fetchEntities$ = () => this.kafkaService.getKafkaBrokers(this.pageLink);

  constructor(private kafkaService: KafkaService,
              protected domSanitizer: DomSanitizer) {
    super(domSanitizer);
  }

  getColumns() {
    const columns: Array<EntityColumn<KafkaBroker>> = [];
    columns.push(
      new EntityTableColumn<KafkaBroker>('address', 'kafka.address', '80%'),
      new EntityTableColumn<KafkaBroker>('brokerSize', 'kafka.size', '20%',
          entity => formatBytes(entity.brokerSize),
        () => ({color: 'rgba(0,0,0,0.54)'}))
    );
    return columns;
  }
}
