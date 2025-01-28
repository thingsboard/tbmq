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

import { AfterViewInit, ChangeDetectorRef, Component } from '@angular/core';
import { HomeChartsComponent } from '../../components/home-charts/home-charts.component';
import { HomeCardsSessionsCredentialsComponent } from '../../components/home-cards-sessions-credentials/home-cards-sessions-credentials.component';
import { CardConfigComponent } from '../../components/card-config/card-config.component';
import { KafkaBrokersHomeTableComponent } from '../../components/kafka-tables/kafka-brokers-home-table.component';
import { KafkaTablesTabGroupComponent } from '../../components/kafka-tables/kafka-tables-tab-group.component';
import { GettingStartedHomeComponent } from '../../components/getting-started/getting-started-home.component';
import { QuickLinksComponent } from '../../components/quick-links/quick-links.component';
import { VersionCardComponent } from '../../components/version-card/version-card.component';

@Component({
    selector: 'tb-home-overview',
    templateUrl: './home-overview.component.html',
    styleUrls: ['./home-overview.component.scss'],
    imports: [HomeChartsComponent, HomeCardsSessionsCredentialsComponent, CardConfigComponent, KafkaBrokersHomeTableComponent, KafkaTablesTabGroupComponent, GettingStartedHomeComponent, QuickLinksComponent, VersionCardComponent]
})
export class HomeOverviewComponent implements AfterViewInit {

  constructor(private cd: ChangeDetectorRef) {
  }

  ngAfterViewInit(): void {
    this.cd.detectChanges();
  }
}
