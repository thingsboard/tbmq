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

import { Component, EventEmitter, input, Output } from '@angular/core';
import { TranslateModule } from '@ngx-translate/core';
import { LegendConfig, LegendKey } from '@shared/models/chart.model';
import { SafePipe } from '@shared/pipe/safe.pipe';
import { MonitoringChartLegendItemComponent } from './monitoring-chart-legend-item.component';

@Component({
  selector: 'tb-monitoring-chart-legend',
  standalone: true,
  imports: [TranslateModule, SafePipe, MonitoringChartLegendItemComponent],
  templateUrl: './monitoring-chart-legend.component.html'
})
export class MonitoringChartLegendComponent {
  readonly showLegend = input<boolean>(true);
  readonly isFullscreen = input<boolean>(false);
  readonly legendConfig = input<LegendConfig>({
    showMin: true,
    showMax: true,
    showAvg: true,
    showTotal: true,
    showLatest: true
  });
  readonly legendKeys = input<any[]>([]);
  readonly legendData = input<any[]>([]);
  readonly totalOnly = input<boolean>(false);

  @Output() legendKeyEnter = new EventEmitter<LegendKey>();
  @Output() legendKeyLeave = new EventEmitter<LegendKey>();
  @Output() toggleLegendKey = new EventEmitter<LegendKey>();

  legendValue(index: number, type: string): number {
    if (this.totalOnly()) {
      return this.legendData()[0]?.[type];
    } else {
      return this.legendData()[index]?.[type];
    }
  }
}
