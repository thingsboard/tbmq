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

import { ChangeDetectorRef, Component, EventEmitter, input, OnChanges, Output, SimpleChanges } from '@angular/core';
import { TranslateModule } from '@ngx-translate/core';
import { LegendConfig, LegendKey, ChartDataKey, TOTAL_KEY } from '@shared/models/chart.model';
import { SafePipe } from '@shared/pipe/safe.pipe';
import { ChartLegendItemComponent } from './chart-legend-item.component';
import { ChartDataset } from 'chart.js';
import { POLLING_INTERVAL } from '@shared/models/home-page.model';
import { calculateFixedWindowTimeMs, Timewindow } from '@shared/models/time/time.models';
import {
  calculateAvg,
  calculateLatest,
  calculateMax,
  calculateMin,
  calculateTotal
} from '@core/utils';
import { getColor } from '@shared/models/chart.model';
import Chart from 'chart.js/auto';

@Component({
  selector: 'tb-chart-legend',
  templateUrl: './chart-legend.component.html',
  imports: [TranslateModule, SafePipe, ChartLegendItemComponent]
})
export class ChartLegendComponent implements OnChanges {
  readonly isFullscreen = input<boolean>(false);
  readonly legendConfig = input<LegendConfig>({
    showMin: true,
    showMax: true,
    showAvg: true,
    showTotal: true,
    showLatest: true
  });
  readonly totalOnly = input<boolean>(false);
  readonly datasets = input<ChartDataset<'line', any>[]>([]);
  readonly timewindow = input<Timewindow>();
  readonly chartType = input<ChartDataKey>();
  readonly dataKeys = input<string[]>([]);
  readonly chart = input<Chart<'line', any>>();
  readonly visibleDataKeys = input<string[]>([]);

  @Output() legendItemsChange = new EventEmitter<any[]>();
  @Output() legendItemsVisibilityChange = new EventEmitter<string[]>();
  @Output() fetchDataKeyData = new EventEmitter<string>();

  legendKeys: LegendKey[] = [];
  legendData: Array<{
    min: number;
    max: number;
    avg: number;
    total: number;
    latest: number
  }> = [];

  constructor(private cd: ChangeDetectorRef) {
  }

  legendValue(index: number, type: string): number {
    if (this.totalOnly()) {
      return this.legendData[0]?.[type];
    } else {
      return this.legendData[index]?.[type];
    }
  }

  resetLegendData() {
    this.legendData = [];
  }

  updateLegendData(data: any[]) {
    if (data?.length) {
      this.legendData.push({
        min: Math.floor(calculateMin(data)),
        max: Math.floor(calculateMax(data)),
        avg: Math.floor(calculateAvg(data)),
        total: Math.floor(calculateTotal(data)),
        latest: Math.floor(calculateLatest(data))
      });
    } else {
      this.legendData.push({
        min: 0,
        max: 0,
        avg: 0,
        total: 0,
        latest: 0
      });
    }
  }

  updateLegend(): void {
    this.resetLegendData();
    const datasets = this.datasets() || [];
    const tw = this.timewindow?.();
    const fixed = tw ? calculateFixedWindowTimeMs(tw) : null;
    const start = fixed?.startTimeMs || 0;
    const end = fixed?.endTimeMs || Number.MAX_SAFE_INTEGER;
    for (const ds of datasets) {
      const data = (ds?.data as any[])?.filter(value => value.ts >= start - POLLING_INTERVAL && value.ts <= end);
      this.updateLegendData(data);
    }
  }

  ngOnChanges(changes: SimpleChanges): void {
    if (changes['dataKeys'] || changes['chartType'] || changes['totalOnly']) {
      this.updateLegendKeys();
    }
    if (changes['datasets'] || changes['timewindow']) {
      this.updateLegend();
    }
  }

  private updateLegendKeys() {
    this.legendKeys = [];
    const ids = this.dataKeys() || [];
    for (let i = 0; i < ids.length; i++) {
      const color = getColor(this.chartType(), i);
      const dataKey = ids[i];
      if (!this.totalOnly() || dataKey === TOTAL_KEY) {
        const index = this.totalOnly() ? 0 : i;
        this.addLegendKey(index, dataKey, color);
      }
    }
    this.legendItemsChange.emit(this.legendKeys);
    this.cd.detectChanges();
  }

  private addLegendKey(index: number, dataKey: string, color: string) {
    this.legendKeys.push({
      dataKey: {
        label: dataKey,
        color,
        hidden: dataKey !== TOTAL_KEY
      },
      dataIndex: index
    });
  }

  updateLineWidth(legendKey: LegendKey, width: number) {
    const visible = (this.visibleDataKeys() || []).includes(legendKey.dataKey.label);
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
    const visibleIds = [...(this.visibleDataKeys() || [])];
    if (!visibleIds.includes(dataKey)) {
      visibleIds.push(dataKey);
      this.legendItemsVisibilityChange.emit(visibleIds);
      this.fetchDataKeyData.emit(dataKey);
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

  private updateLegendLabel(datasetIndex: number, isDatasetVisible: boolean) {
    const color = isDatasetVisible ? getColor(this.chartType(), datasetIndex) : null;
    if (this.legendKeys[datasetIndex]?.dataKey) {
      this.legendKeys[datasetIndex].dataKey.color = color as any;
      this.legendKeys[datasetIndex].dataKey.hidden = !this.legendKeys[datasetIndex].dataKey.hidden;
      this.legendItemsChange.emit(this.legendKeys);
    }
  }
}
