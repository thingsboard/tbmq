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

import { NgModule } from '@angular/core';
import { CommonModule } from '@angular/common';
import { SharedModule } from '@app/shared/shared.module';
import { StatsComponent } from "@home/pages/stats/stats.component";
import { StatsRoutingModule } from "@home/pages/stats/stats-routing.module";
import { ChartComponent } from "@home/components/chart/chart.component";
import { QuickLinksComponent } from "@home/components/quick-links/quick-links.component";
import { VersionCardComponent } from "@home/components/version-card/version-card.component";
import { CardThreeItemsComponent } from "@home/components/card-three-items/card-three-items.component";
import { CardConfigComponent } from "@home/components/card-config/card-config.component";
import { KafkaTableComponent } from "@home/components/kafka-table/kafka-table.component";

@NgModule({
  declarations: [
    StatsComponent,
    ChartComponent,
    QuickLinksComponent,
    VersionCardComponent,
    CardThreeItemsComponent,
    CardConfigComponent,
    KafkaTableComponent
  ],
  imports: [
    CommonModule,
    SharedModule,
    StatsRoutingModule
  ]
})
export class StatsModule {
}
