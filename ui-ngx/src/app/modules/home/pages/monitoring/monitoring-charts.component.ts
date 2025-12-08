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

import { Component, output } from '@angular/core';
import { FixedWindow, Timewindow } from '@shared/models/time/time.models';
import { TranslateModule } from '@ngx-translate/core';
import { TimeService } from '@core/services/time.service';
import { StatsChartType } from '@shared/models/chart.model';
import { MatToolbar } from '@angular/material/toolbar';
import { TimewindowComponent } from '@shared/components/time/timewindow.component';
import { FormsModule } from '@angular/forms';
import { ChartComponent } from '@shared/components/chart/chart.component';
import { ActivatedRoute } from '@angular/router';

@Component({
    selector: 'tb-monitoring-charts',
    templateUrl: './monitoring-charts.component.html',
    styleUrls: ['./monitoring-charts.component.scss'],
    imports: [MatToolbar, TimewindowComponent, FormsModule, TranslateModule, ChartComponent]
})
export class MonitoringChartsComponent {

  readonly timewindowChanged = output<FixedWindow>();
  chartTypes: StatsChartType[];
  timewindow: Timewindow;

  constructor(
    private timeService: TimeService,
    private route: ActivatedRoute,
  ) {
    this.route.data.subscribe(data => this.chartTypes = data.charts);
    this.timewindow = this.timeService.defaultTimewindow();
  }
}
