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
import { HomeOverviewComponent } from '@home/pages/home-overview/home-overview.component';
import { HomeOverviewRoutingModule } from '@home/pages/home-overview/home-overview-routing.module';
import { HomeChartsComponent } from '@home/components/home-charts/home-charts.component';
import { QuickLinksComponent } from '@home/components/quick-links/quick-links.component';
import { VersionCardComponent } from '@home/components/version-card/version-card.component';
import { CardConfigComponent } from '@home/components/card-config/card-config.component';
import { KafkaTopicsTableComponent } from '@home/components/kafka-topics-table/kafka-topics-table.component';
import { KafkaBrokersTableComponent } from '@home/components/kafka-brokers-table/kafka-brokers-table.component';
import { KafkaConsumersTableComponent } from '@home/components/kafka-consumers-table/kafka-consumers-table.component';
import { HomeCardsSessionsCredentialsComponent } from '@home/components/home-cards-sessions-credentials/home-cards-sessions-credentials.component';
import { KafkaTablesTabGroupComponent } from '@home/components/kafka-tables-tab-group/kafka-tables-tab-group.component';
import { GettingStartedComponent } from '@home/components/getting-started/getting-started.component';
import { KafkaEntitiesTableComponent } from '@home/components/entity/kafka-entities-table.component';
import { HomeCardsTableComponent } from '@home/components/home-cards-sessions-credentials/home-cards-table.component';

@NgModule({
  declarations: [
    HomeOverviewComponent,
    HomeChartsComponent,
    QuickLinksComponent,
    VersionCardComponent,
    HomeCardsSessionsCredentialsComponent,
    HomeCardsTableComponent,
    CardConfigComponent,
    KafkaTopicsTableComponent,
    KafkaBrokersTableComponent,
    KafkaConsumersTableComponent,
    KafkaTablesTabGroupComponent,
    GettingStartedComponent,
    KafkaEntitiesTableComponent
  ],
  imports: [
    CommonModule,
    SharedModule,
    HomeOverviewRoutingModule
  ]
})
export class HomeOverviewModule {
}
