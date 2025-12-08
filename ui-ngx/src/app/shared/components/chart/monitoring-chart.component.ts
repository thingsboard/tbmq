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

import { Component, effect, input, model } from '@angular/core';

@Component({
  selector: 'tb-monitoring-chart',
  templateUrl: './monitoring-chart.component.html'
})
export class MonitoringChartComponent {

  readonly chartType = input<string>();
  readonly chartPage = input<string>();
  readonly isFullscreen = input<boolean>(false);
  readonly legendKeys = input<any[]>([]);
  chartHeight = input<number>(300);

  chartContainerHeight = model<string>('300px');

  constructor() {
    effect(() => {
      this.onFullScreen();
    });
  }

  onFullScreen() {
    if (this.isFullscreen()) {
      const height = 120 + ((this.legendKeys().length - 1) * 24);
      this.chartContainerHeight.set(`calc(100vh - ${height}px)`);
    } else {
      this.chartContainerHeight.set(this.chartHeight() + 'px');
    }
  }
}
