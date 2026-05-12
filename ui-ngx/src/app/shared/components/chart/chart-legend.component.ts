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

import {
  Component,
  input,
  OnInit,
  output
} from '@angular/core';
import { TranslateModule } from '@ngx-translate/core';
import {
  ChartKey,
  ChartSeries,
  getColor,
  LegendConfig,
  LegendKey,
  TOTAL_ENTITY_ID
} from '@shared/models/chart.model';
import { SafePipe } from '@shared/pipe/safe.pipe';
import { ChartLegendItemComponent } from './chart-legend-item.component';
import type { ECharts } from 'echarts/core';
import {
  calculateAvg,
  calculateLatest,
  calculateMax,
  calculateMin,
  calculateTotal,
  formatLargeNumber,
} from '@core/utils';

@Component({
  selector: 'tb-chart-legend',
  templateUrl: './chart-legend.component.html',
  imports: [TranslateModule, SafePipe, ChartLegendItemComponent]
})
export class ChartLegendComponent implements OnInit {
  readonly isFullscreen = input<boolean>(false);
  readonly legendConfig = input<LegendConfig>({
    showMin: true,
    showMax: true,
    showAvg: true,
    showTotal: true,
    showLatest: true
  });
  readonly chart = input<ECharts | null>(null);
  readonly series = input<ChartSeries[]>([]);
  readonly chartKey = input<ChartKey>();
  readonly entityIds = input<string[]>([]);
  readonly visibleEntityIds = input<string[]>();
  readonly totalEntityIdOnly = input<boolean>();
  readonly toggleVisibility = input<(dataKey: string) => boolean>();

  readonly visibleEntityIdsChange = output<string[]>();
  readonly entityIdTimeseriesRequested = output<string>();

  legendKeys: LegendKey[] = [];
  legendData: Array<{ min: number; max: number; avg: number; total: number; latest: number }> = [];

  ngOnInit() {
    this.buildLegend();
  }

  private buildLegend() {
    const keys = this.entityIds() || [];
    for (let i = 0; i < keys.length; i++) {
      const color = getColor(this.chartKey(), i);
      const dataKey = keys[i];
      if (!this.totalEntityIdOnly() || dataKey === TOTAL_ENTITY_ID) {
        const index = this.totalEntityIdOnly() ? 0 : i;
        const legendKey = {
          dataKey: {
            label: dataKey,
            color,
            hidden: dataKey !== TOTAL_ENTITY_ID
          },
          dataIndex: index
        };
        this.legendKeys.push(legendKey);
      }
    }
  }

  legendValue(index: number, type: 'min' | 'max' | 'avg' | 'total' | 'latest'): string {
    const idx = this.totalEntityIdOnly() ? 0 : index;
    const value = this.legendData[idx]?.[type] ?? 0;
    return formatLargeNumber(value);
  }

  updateLegend(): void {
    this.legendData = [];
    const series = this.series() || [];
    for (const s of series) {
      const data = s.data;
      this.legendData.push({
        min: data?.length ? Math.floor(calculateMin(data)) : 0,
        max: data?.length ? Math.floor(calculateMax(data)) : 0,
        avg: data?.length ? Math.floor(calculateAvg(data)) : 0,
        total: data?.length ? Math.floor(calculateTotal(data)) : 0,
        latest: data?.length ? Math.floor(calculateLatest(data)) : 0
      });
    }
  }

  updateLineWidth(legendKey: LegendKey, width: number) {
    const visible = (this.visibleEntityIds()).includes(legendKey.dataKey.label);
    const chart = this.chart();
    if (!visible || !chart) {
      return;
    }
    const seriesName = legendKey.dataKey.label;
    chart.setOption({
      series: [{ name: seriesName, lineStyle: { width } } as any],
    });
  }

  toggleLegendKey(legendKey: LegendKey) {
    const dataKey = legendKey.dataKey.label;
    const toggle = this.toggleVisibility();
    if (!toggle) {
      return;
    }
    const visibleIds = [...(this.visibleEntityIds() ?? [])];
    if (!visibleIds.includes(dataKey)) {
      visibleIds.push(dataKey);
      this.visibleEntityIdsChange.emit(visibleIds);
      this.entityIdTimeseriesRequested.emit(dataKey);
    }
    const nowVisible = toggle(dataKey);
    this.updateLegendLabel(legendKey.dataIndex, nowVisible);
  }

  private updateLegendLabel(datasetIndex: number, isVisible: boolean) {
    const color = isVisible ? getColor(this.chartKey(), datasetIndex) : null;
    if (this.legendKeys[datasetIndex]?.dataKey) {
      this.legendKeys[datasetIndex].dataKey.color = color as any;
      this.legendKeys[datasetIndex].dataKey.hidden = !isVisible;
    }
  }
}
