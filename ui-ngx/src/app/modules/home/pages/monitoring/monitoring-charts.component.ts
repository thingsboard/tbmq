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

import { Component, output } from '@angular/core';
import { FixedWindow, HOUR, Timewindow } from '@shared/models/time/time.models';
import { TranslateModule } from '@ngx-translate/core';
import { TimeService } from '@core/services/time.service';
import { ChartKey } from '@shared/models/chart.model';
import { MatToolbar } from '@angular/material/toolbar';
import { TimewindowComponent } from '@shared/components/time/timewindow.component';
import { FormsModule } from '@angular/forms';
import { ChartComponent } from '@shared/components/chart/chart.component';
import { ActivatedRoute } from '@angular/router';
import { ConfigService } from '@core/http/config.service';
import { HOME_CHARTS_DURATION } from '@shared/models/home-page.model';

@Component({
    selector: 'tb-monitoring-charts',
    templateUrl: './monitoring-charts.component.html',
    styleUrls: ['./monitoring-charts.component.scss'],
    imports: [MatToolbar, TimewindowComponent, FormsModule, TranslateModule, ChartComponent]
})
export class MonitoringChartsComponent {

  readonly timewindowChanged = output<FixedWindow>();
  chartKeys: ChartKey[];
  timewindow: Timewindow;

  constructor(
    private timeService: TimeService,
    private route: ActivatedRoute,
    private configService: ConfigService,
  ) {
    this.route.data.subscribe(data => this.chartKeys = data.charts);
    this.timewindow = this.timeService.defaultTimewindow();
    const statsCollectionInterval = this.configService.brokerConfig.statsCollectionInterval;
    if (statsCollectionInterval > 6) { // > 1H
      this.timewindow.realtime.timewindowMs = Math.ceil(statsCollectionInterval * HOME_CHARTS_DURATION / HOUR) * HOUR;
    }
  }
}
