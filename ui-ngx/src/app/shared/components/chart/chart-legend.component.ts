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
  OnChanges,
  OnInit,
  output,
  SimpleChanges
} from '@angular/core';
import { TranslateModule } from '@ngx-translate/core';
import { LegendConfig, LegendKey, ChartKey, TOTAL_ENTITY_ID } from '@shared/models/chart.model';
import { SafePipe } from '@shared/pipe/safe.pipe';
import { ChartLegendItemComponent } from './chart-legend-item.component';
import { ChartDataset } from 'chart.js';
import {
  calculateAvg,
  calculateLatest,
  calculateMax,
  calculateMin,
  calculateTotal,
} from '@core/utils';
import { getColor } from '@shared/models/chart.model';
import Chart from 'chart.js/auto';

@Component({
  selector: 'tb-chart-legend',
  templateUrl: './chart-legend.component.html',
  imports: [TranslateModule, SafePipe, ChartLegendItemComponent]
})
export class ChartLegendComponent implements OnInit, OnChanges {
  readonly isFullscreen = input<boolean>(false);
  readonly legendConfig = input<LegendConfig>({
    showMin: true,
    showMax: true,
    showAvg: true,
    showTotal: true,
    showLatest: true
  });
  readonly chart = input<Chart<'line', any>>();
  readonly datasets = input<ChartDataset<'line', any>[]>([]);
  readonly chartKey = input<ChartKey>();
  readonly entityIds = input<string[]>([]);
  readonly visibleEntityIds = input<string[]>();
  readonly totalEntityIdOnly = input<boolean>();

  readonly visibleEntityIdsChange = output<string[]>();
  readonly entityIdTimeseriesRequested = output<string>();

  legendKeys: LegendKey[] = [];
  legendData = [{
    min: 0,
    max: 0,
    avg: 0,
    total: 0,
    latest: 0,
  }];

  constructor() {
  }

  ngOnInit() {
    this.buildLegend();
  }

  ngOnChanges(changes: SimpleChanges): void {
    for (const propName of Object.keys(changes)) {
      const change = changes[propName];
      if (!change.firstChange && change.currentValue !== change.previousValue) {
        if (propName === 'datasets' && change.currentValue) {
          this.updateLegend();
        }
      }
    }
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

  legendValue(index: number, type: string): number {
    if (this.totalEntityIdOnly()) {
      return this.legendData[0]?.[type];
    } else {
      return this.legendData[index]?.[type];
    }
  }

  updateLegend(): void {
    this.legendData = [];
    const datasets = this.datasets() || [];
    for (const ds of datasets) {
      this.updateLegendData(ds?.data);
    }
  }

  updateLineWidth(legendKey: LegendKey, width: number) {
    const visible = (this.visibleEntityIds()).includes(legendKey.dataKey.label);
    if (visible) {
      const datasetIndex = legendKey.dataIndex as number;
      const chart = this.chart?.();
      if (chart?.data?.datasets?.[datasetIndex]) {
        chart.data.datasets[datasetIndex].borderWidth = width;
        chart.update('none');
      }
    }
  }

  toggleLegendKey(legendKey: LegendKey) {
    const dataKey = legendKey.dataKey.label;
    const chart = this.chart?.();
    if (!chart) {
      return;
    }
    const visibleIds = [...(this.visibleEntityIds())];
    if (!visibleIds.includes(dataKey)) {
      visibleIds.push(dataKey);
      this.visibleEntityIdsChange.emit(visibleIds);
      this.entityIdTimeseriesRequested.emit(dataKey);
    }

    const datasetIndex = legendKey.dataIndex as number;
    if (chart.isDatasetVisible(datasetIndex)) {
      chart.hide(datasetIndex);
    } else {
      chart.show(datasetIndex);
    }
    const isVisible = chart.isDatasetVisible(datasetIndex);
    this.updateLegendLabel(datasetIndex, isVisible);
  }

  private updateLegendData(data: any[]) {
    this.legendData.push({
      min: data?.length ? Math.floor(calculateMin(data)) : 0,
      max: data?.length ? Math.floor(calculateMax(data)) : 0,
      avg: data?.length ? Math.floor(calculateAvg(data)) : 0,
      total: data?.length ? Math.floor(calculateTotal(data)) : 0,
      latest: data?.length ? Math.floor(calculateLatest(data)) : 0
    });
  }

  private updateLegendLabel(datasetIndex: number, isDatasetVisible: boolean) {
    const color = isDatasetVisible ? getColor(this.chartKey(), datasetIndex) : null;
    if (this.legendKeys[datasetIndex]?.dataKey) {
      this.legendKeys[datasetIndex].dataKey.color = color as any;
      this.legendKeys[datasetIndex].dataKey.hidden = !this.legendKeys[datasetIndex].dataKey.hidden;
    }
  }
}
