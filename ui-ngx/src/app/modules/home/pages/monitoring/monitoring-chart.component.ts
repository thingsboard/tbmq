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

import {
  AfterViewInit,
  ChangeDetectorRef,
  Component,
  input,
  OnChanges,
  OnDestroy,
  OnInit,
  SimpleChanges
} from '@angular/core';
import { calculateFixedWindowTimeMs, FixedWindow, Timewindow, TimewindowType } from '@shared/models/time/time.models';
import { forkJoin, Observable, Subject, timer } from 'rxjs';
import { TranslateModule, TranslateService } from '@ngx-translate/core';
import { TimeService } from '@core/services/time.service';
import { StatsService } from '@core/http/stats.service';
import { share, switchMap, takeUntil } from 'rxjs/operators';
import {
  CHART_TOTAL_ONLY,
  chartJsParams,
  ChartPage,
  ChartTooltipTranslationMap,
  getColor,
  LegendConfig,
  LegendKey,
  MAX_DATAPOINTS_LIMIT,
  StatsChartType,
  StatsChartTypeTranslationMap,
  TimeseriesData,
  TOTAL_KEY, TsValue
} from '@shared/models/chart.model';
import { AppState } from '@core/core.state';
import { Store } from '@ngrx/store';
import { POLLING_INTERVAL } from '@shared/models/home-page.model';
import { ActivatedRoute } from '@angular/router';
import { ActionNotificationShow } from '@core/notification/notification.actions';
import { DataSizeUnitType, DataSizeUnitTypeTranslationMap } from '@shared/models/ws-client.model';
import {
  calculateAvg,
  calculateLatest,
  calculateMax,
  calculateMin,
  calculateTotal,
  convertDataSizeUnits
} from '@core/utils';
import { ChartConfiguration, ChartDataset, ChartType, LineController, Point, TimeSeriesScale } from 'chart.js';
import { TimewindowComponent } from '@shared/components/time/timewindow.component';
import { FormsModule } from '@angular/forms';
import { NgTemplateOutlet } from '@angular/common';
import { FullscreenDirective } from '@shared/components/fullscreen.directive';
import { MatDivider } from '@angular/material/divider';
import { ToggleHeaderComponent, ToggleOption } from '@shared/components/toggle-header.component';
import { MatIcon } from '@angular/material/icon';
import { MatTooltip } from '@angular/material/tooltip';
import { MatIconButton } from '@angular/material/button';
import { SafePipe } from '@shared/pipe/safe.pipe';
import { MatProgressBar } from '@angular/material/progress-bar';

import Chart from 'chart.js/auto';
import Zoom from 'chartjs-plugin-zoom';
import 'chartjs-adapter-moment';
Chart.register([Zoom]);

@Component({
  selector: 'tb-monitoring-chart',
  templateUrl: './monitoring-chart.component.html',
  styleUrls: ['./monitoring-chart.component.scss'],
  imports: [TimewindowComponent, FormsModule, FullscreenDirective, MatDivider, ToggleHeaderComponent, ToggleOption, MatIcon, MatTooltip, MatIconButton, NgTemplateOutlet, SafePipe, TranslateModule, MatProgressBar]
})
export class MonitoringChartComponent implements OnInit, AfterViewInit, OnDestroy, OnChanges {

  readonly chartType = input<StatsChartType>();
  readonly parentTimewindow = input<Timewindow>();

  chartPage = ChartPage.monitoring;
  chart: Chart<'line', TsValue[]>;
  chartTypeTranslationMap = StatsChartTypeTranslationMap;
  dataSizeUnitTypeTranslationMap = DataSizeUnitTypeTranslationMap;
  dataSizeUnitType = Object.values(DataSizeUnitType);
  timewindow: Timewindow;

  chartHeight = 300;
  currentDataSizeUnitType = DataSizeUnitType.BYTE;
  chartContainerHeight: string;
  fullscreenChart: string;
  isFullscreen = false;
  isLoading = false;

  showLegend = true;
  legendConfig: LegendConfig = {
    showMin: true,
    showMax: true,
    showAvg: true,
    showTotal: true,
    showLatest: true
  };
  legendData = [];
  legendKeys = [];

  private fixedWindowTimeMs: FixedWindow;
  private brokerIds: string[];

  private stopPolling$ = new Subject<void>();
  private destroy$ = new Subject<void>();

  chartTooltip = (chartType: string) => this.translate.instant(ChartTooltipTranslationMap.get(chartType));

  constructor(protected store: Store<AppState>,
              private translate: TranslateService,
              private timeService: TimeService,
              private statsService: StatsService,
              private cd: ChangeDetectorRef,
              private route: ActivatedRoute) {
    this.timewindow = this.timeService.defaultTimewindow();
    this.calculateFixedWindowTimeMs();
  }

  ngOnInit() {
    this.initData();
  }

  ngOnDestroy() {
    this.stopPolling();
    this.destroy$.next();
    this.destroy$.complete();
  }

  ngAfterViewInit(): void {
    this.fetchEntityTimeseries(true);
    $(document).on('keydown',
      (event) => {
        if ((event.code === 'Escape') && this.isFullscreen) {
          event.preventDefault();
          this.onFullScreen();
        }
      });
    this.cd.detectChanges();
  }

  ngOnChanges(changes: SimpleChanges): void {
    for (const propName of Object.keys(changes)) {
      const change = changes[propName];
      if (!change.firstChange && change.currentValue !== change.previousValue) {
        if (propName === 'parentTimewindow') {
          this.timewindow = change.currentValue;
          this.onTimewindowChange();
        }
      }
    }
  }

  onTimewindowChange() {
    this.isLoading = true;
    this.stopPolling();
    this.calculateFixedWindowTimeMs();
    this.trafficPayloadChartUnitTypeChanged();
    this.fetchEntityTimeseries();
    this.chart.resetZoom();
  }

  onFullScreen(chartType?: string) {
    this.isFullscreen = !this.isFullscreen;
    if (this.isFullscreen) {
      this.fullscreenChart = chartType;
      let height = 85;
      if (this.brokerIds.length > 1) {
        height -= (this.brokerIds.length * 2);
      }
      this.chartContainerHeight = height + 'vh';
    } else {
      this.fullscreenChart = undefined;
      this.chartContainerHeight = this.chartHeight + 'px';
    }
  }

  trafficPayloadChartUnitTypeChanged(type = DataSizeUnitType.BYTE) {
    if (this.isTrafficPayloadChart()) {
      for (let i = 0; i < this.brokerIds.length; i++) {
        this.chart.data.datasets[i].data = this.chart.data.datasets[i].data
          .map(el => {
            return {
              value: convertDataSizeUnits(el.value, this.currentDataSizeUnitType, type),
              ts: el.ts
            }
          });
      }
      this.currentDataSizeUnitType = type;
      this.updateChartView();
    }
  }

  onLegendKeyEnter(legendKey: LegendKey) {
    const datasetIndex = legendKey.dataIndex;
    this.chart.data.datasets[datasetIndex].borderWidth = 4;
    this.updateChart();
  }

  onLegendKeyLeave(legendKey: LegendKey) {
    const datasetIndex = legendKey.dataIndex;
    this.chart.data.datasets[datasetIndex].borderWidth = 2;
    this.updateChart();
  }

  toggleLegendKey(legendKey: LegendKey) {
    const datasetIndex = legendKey.dataIndex;
    if (this.chart.isDatasetVisible(datasetIndex)) {
      this.chart.hide(datasetIndex);
    } else {
      this.chart.show(datasetIndex);
    }
    this.updateLegendLabel(datasetIndex, this.chart.isDatasetVisible(datasetIndex));
  }

  legendValue(index: number, type: string): number {
    if (this.totalOnly()) {
      return this.legendData[0][type];
    } else {
      return this.legendData[index][type];
    }
  }

  totalOnly(): boolean {
    return CHART_TOTAL_ONLY.includes(this.chartType());
  }

  isTrafficPayloadChart() {
    return this.chartType() === StatsChartType.inboundPayloadTraffic || this.chartType() === StatsChartType.outboundPayloadTraffic;
  }

  private initData() {
    this.brokerIds = this.route.snapshot.data.brokerIds;
  }

  private calculateFixedWindowTimeMs() {
    this.fixedWindowTimeMs = calculateFixedWindowTimeMs(this.timewindow);
  }

  private fetchEntityTimeseries(initCharts = false) {
    const $getEntityTimeseriesTasks: Observable<TimeseriesData>[] = [];
    for (const brokerId of this.brokerIds) {
      $getEntityTimeseriesTasks.push(
        this.statsService.getEntityTimeseries(
          brokerId,
          this.fixedWindowTimeMs.startTimeMs,
          this.fixedWindowTimeMs.endTimeMs,
          [this.chartType()],
          MAX_DATAPOINTS_LIMIT,
          this.timewindow.aggregation.type,
          this.timeService.timewindowGroupingInterval(this.timewindow)
        )
      );
    }
    forkJoin($getEntityTimeseriesTasks)
      .pipe(takeUntil(this.stopPolling$))
      .subscribe(data => {
        this.isLoading = false;
        if (initCharts) {
          this.initCharts(data as TimeseriesData[]);
        } else {
            for (let i = 0; i < this.brokerIds.length; i++) {
              if (this.totalOnly()) {
                this.chart.data.datasets[0].data = data[0][this.chartType()];
              } else {
                this.chart.data.datasets[i].data = data[i][this.chartType()];
              }
              this.updateChartView();
            }
        }
        if (this.timewindow.selectedTab === TimewindowType.REALTIME) {
          this.startPolling();
        }
        this.checkMaxAllowedDataLength(data);
      });
  }

  private initCharts(data: TimeseriesData[]) {
      const ctx = document.getElementById(this.chartType() + this.chartPage) as HTMLCanvasElement;
      const datasets = {data: {datasets: []}};
      this.resetLegendKeys();
      this.updateLegendKeys();
      this.resetLegendData();
      for (let i = 0; i < this.brokerIds.length; i++) {
        const brokerId = this.brokerIds[i];
        if (this.totalOnly()) {
          if (brokerId === TOTAL_KEY) {
            datasets.data.datasets.push(this.getDataset(data, i, brokerId));
            this.updateLegendData(data[i][this.chartType()]);
          }
        } else {
          datasets.data.datasets.push(this.getDataset(data, i, brokerId));
          this.updateLegendData(data[i][this.chartType()]);
        }
      }
      const params = {...chartJsParams(this.chartPage), ...datasets} as ChartConfiguration<'line', TsValue[]>;
      this.chart = new Chart<'line', TsValue[]>(ctx, params);
      if (this.isTrafficPayloadChart()) {
        this.chart.options.plugins.tooltip.callbacks.label = (context) => {
          const value = Number.isInteger(context.parsed.y) ? context.parsed.y : context.parsed.y.toFixed(2);
          return `${value} ${this.dataSizeUnitTypeTranslationMap.get(this.currentDataSizeUnitType)}`;
        }
      }
      this.updateXScale();
      ctx.addEventListener('dblclick', () => {
        this.chart.resetZoom();
        this.updateChartView();
      });
  }

  private getDataset(dataset, i, brokerId): ChartDataset {
    const color = getColor(this.chartType(), i);
    return {
      label: brokerId,
      data: dataset ? dataset[i][this.chartType()] : null,
      pointStyle: 'circle',
      hidden: brokerId !== TOTAL_KEY,
      borderColor: color,
      backgroundColor: color,
      pointHoverBackgroundColor: color,
      pointBorderColor: color,
      pointBackgroundColor: color,
      pointHoverBorderColor: color,
      pointRadius: 0
    };
  }

  private startPolling() {
    const $getLatestTimeseriesTasks: Observable<TimeseriesData>[] = [];
    for (const brokerId of this.brokerIds) {
      $getLatestTimeseriesTasks.push(this.statsService.getLatestTimeseries(brokerId, [this.chartType()]));
    }
    timer(0, POLLING_INTERVAL)
    .pipe(
      switchMap(() => forkJoin($getLatestTimeseriesTasks)),
      takeUntil(this.stopPolling$),
      share()
    ).subscribe(data => {
      this.addPollingIntervalToTimewindow();
      this.prepareData(data);
      this.pushLatestValue(data as TimeseriesData[]);
      this.updateChartView();
    });
  }

  private addPollingIntervalToTimewindow() {
    this.fixedWindowTimeMs.startTimeMs += POLLING_INTERVAL;
    this.fixedWindowTimeMs.endTimeMs += POLLING_INTERVAL;
  }

  private prepareData(data: TimeseriesData[]) {
    if (this.isTrafficPayloadChart()) {
      const tsValue = data[0][this.chartType()][0];
      data[0][StatsChartType[this.chartType()]][0] = {
        value: convertDataSizeUnits(tsValue.value, DataSizeUnitType.BYTE, this.currentDataSizeUnitType),
        ts: tsValue.ts
      }
      this.trafficPayloadChartUnitTypeChanged(this.currentDataSizeUnitType);
    }
  }

  private pushLatestValue(data: TimeseriesData[]) {
    for (let i = 0; i < this.brokerIds.length; i++) {
      let index = i;
      if (data[index][this.chartType()]?.length) {
        if (this.totalOnly()) {
          index = 0;
        }
        const latestValue = data[index][this.chartType()][0];
        const chartData = this.chart.data.datasets[index].data;
        const chartLatestValue = chartData[0];
        if (latestValue?.ts > chartLatestValue?.ts) {
          this.chart.data.datasets[index].data.unshift(latestValue);
        }
      }
    }
  }

  private updateChartView() {
    this.updateXScale();
    this.updateChart();
    this.updateLegend();
  }

  private stopPolling() {
    this.stopPolling$.next();
  }

  private updateChart() {
    this.chart.update('none');
  }

  private updateXScale() {
    const timewindow = calculateFixedWindowTimeMs(this.timewindow);
    this.chart.options.scales.x.min = timewindow.startTimeMs + POLLING_INTERVAL; // TODO fix chart range
    this.chart.options.scales.x.max = timewindow.endTimeMs;
    const hours = this.hoursInRange();
    let format = 'MMM-DD';
    let round: string;
    let unit: string;
    if (hours <= 24) {
      format = 'HH:mm';
      unit = 'minute';
      round = 'minute';
    } else if (hours <= 24 * 30) {
      format = 'MMM-DD HH:mm';
      unit = 'day';
    }
    const time = {
      round,
      unit,
      displayFormats: {
        minute: format
      }
    };
    // @ts-ignore
    this.chart.options.scales.x.time = {...this.chart.options.scales.x.time, ...time};
  }

  private hoursInRange(): number {
    const hourMs = 1000 * 60 * 60;
    return (this.fixedWindowTimeMs.endTimeMs - this.fixedWindowTimeMs.startTimeMs) / hourMs;
  }

  private checkMaxAllowedDataLength(data) {
    let showWarning = false;
    for (const brokerData of data) {
      for (const key in brokerData) {
        const dataLength = brokerData[key].length;
        if (dataLength === MAX_DATAPOINTS_LIMIT) {
          showWarning = true;
          break;
        }
      }
    }
    if (showWarning) {
      this.store.dispatch(new ActionNotificationShow(
        {
          message: this.translate.instant('monitoring.max-allowed-timeseries'),
          type: 'warn',
          duration: 0,
          verticalPosition: 'top',
          horizontalPosition: 'left'
        })
      );
    }
  }

  private resetLegendKeys() {
    this.legendKeys = [];
  }

  private resetLegendData() {
    this.legendData = [];
  }

  private updateLegendKeys() {
    for (let i = 0; i < this.brokerIds.length; i++) {
      const color = getColor(this.chartType(), i);
      const brokerId = this.brokerIds[i];
      if (this.totalOnly()) {
        if (brokerId === TOTAL_KEY) {
          this.addLegendKey(0, brokerId, color);
        }
      } else {
        this.addLegendKey(i, brokerId, color);
      }
    }
  }

  private updateLegendData(data: any[]) {
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

  private addLegendKey(index: number, brokerId: string, color: string) {
    this.legendKeys.push({
      dataKey: {
        label: brokerId,
        color,
        hidden: brokerId !== TOTAL_KEY
      },
      dataIndex: index
    });
  }

  private updateLegend() {
    this.resetLegendData();
    for (let i = 0; i < this.brokerIds.length; i++) {
      const brokerId = this.brokerIds[i];
      const data = this.chart.data.datasets[i]?.data
        .filter(value => value.ts >= this.fixedWindowTimeMs.startTimeMs - POLLING_INTERVAL);
      if (this.totalOnly()) {
        if (brokerId === TOTAL_KEY) {
          this.updateLegendData(data);
        }
      } else {
        this.updateLegendData(data);
      }
    }
  }

  private updateLegendLabel(datasetIndex, isDatasetvisible) {
    this.legendKeys[datasetIndex].dataKey.color = isDatasetvisible ? getColor(this.chartType(), datasetIndex) : null;
    this.legendKeys[datasetIndex].dataKey.hidden = !this.legendKeys[datasetIndex].dataKey.hidden;
    this.cd.detectChanges();
  }
}
