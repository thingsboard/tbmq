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
import { CHARTS_TRAFFIC_PERFORMANCE } from '@shared/models/chart.model';
import { MatToolbar } from '@angular/material/toolbar';
import { TimewindowComponent } from '@shared/components/time/timewindow.component';
import { FormsModule } from '@angular/forms';
import { MonitoringChartComponent } from '@home/pages/monitoring/monitoring-chart.component';

@Component({
    selector: 'tb-monitoring-traffic-performance',
    templateUrl: './monitoring-traffic-performance.component.html',
    styleUrls: ['./monitoring-traffic-performance.component.scss'],
    imports: [MatToolbar, TimewindowComponent, FormsModule, TranslateModule, MonitoringChartComponent]
})
export class MonitoringTrafficPerformanceComponent {

  readonly timewindowChanged = output<FixedWindow>();
  readonly chartTypes = CHARTS_TRAFFIC_PERFORMANCE;
  timewindow: Timewindow;

  constructor(private timeService: TimeService) {
    this.timewindow = this.timeService.defaultTimewindow();
  }
}
