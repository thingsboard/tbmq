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
  ElementRef,
  OnDestroy,
  OnInit,
  viewChildren
} from '@angular/core';
import {
  calculateFixedWindowTimeMs,
  FixedWindow,
  Timewindow,
  TimewindowType
} from '@shared/models/time/time.models';
import { forkJoin, Observable, Subject, timer } from 'rxjs';
import { TranslateService, TranslateModule } from '@ngx-translate/core';
import { TimeService } from '@core/services/time.service';
import { StatsService } from '@core/http/stats.service';
import { share, switchMap, takeUntil } from 'rxjs/operators';
import {
  CHART_ALL,
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
  TOTAL_KEY
} from '@shared/models/chart.model';
import { PageComponent } from '@shared/components/page.component';
import { AppState } from '@core/core.state';
import { Store } from '@ngrx/store';
import Chart from 'chart.js/auto';
import 'chartjs-adapter-moment';
import Zoom from 'chartjs-plugin-zoom';
import { POLLING_INTERVAL } from '@shared/models/home-page.model';
import { ActivatedRoute } from '@angular/router';
import { ActionNotificationShow } from '@core/notification/notification.actions';
import { DataSizeUnitType, DataSizeUnitTypeTranslationMap, } from '@shared/models/ws-client.model';
import {
  calculateAvg,
  calculateLatest,
  calculateMax,
  calculateMin,
  calculateTotal,
  convertDataSizeUnits
} from '@core/utils';
import { ChartConfiguration, ChartDataset } from 'chart.js';
import { MatToolbar } from '@angular/material/toolbar';
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

Chart.register([Zoom]);

@Component({
    selector: 'tb-monitoring',
    templateUrl: './monitoring.component.html',
    styleUrls: ['./monitoring.component.scss'],
    imports: [MatToolbar, TimewindowComponent, FormsModule, FullscreenDirective, MatDivider, ToggleHeaderComponent, ToggleOption, MatIcon, MatTooltip, MatIconButton, NgTemplateOutlet, SafePipe, TranslateModule]
})
export class MonitoringComponent extends PageComponent implements OnInit, AfterViewInit, OnDestroy {

  readonly chartElements = viewChildren<ElementRef>('chartElement');

  chartPage = ChartPage.monitoring;
  charts = {};
  chartTypes = CHART_ALL;
  chartTypeTranslationMap = StatsChartTypeTranslationMap;
  dataSizeUnitTypeTranslationMap = DataSizeUnitTypeTranslationMap;
  dataSizeUnitType = Object.values(DataSizeUnitType);
  timewindow: Timewindow;

  chartHeight = 300;
  currentDataSizeUnitType = DataSizeUnitType.BYTE;
  chartContainerHeight: string;
  fullscreenElements: HTMLElement[] = [];
  fullscreenChart: string;
  isFullscreen = false;

  showLegend = true;
  legendConfig: LegendConfig = {
    showMin: true,
    showMax: true,
    showAvg: true,
    showTotal: true,
    showLatest: true
  };
  legendData = {};
  legendKeys = {};

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
    super(store);
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
    this.fullscreenElements = this.chartElements().map((element) => element.nativeElement);
    $(document).on('keydown',
      (event) => {
        if ((event.code === 'Escape') && this.isFullscreen) {
          event.preventDefault();
          this.onFullScreen();
        }
      });
    this.cd.detectChanges();
  }

  onTimewindowChange() {
    this.stopPolling();
    this.calculateFixedWindowTimeMs();
    this.processedBytesUnitTypeChanged();
    this.fetchEntityTimeseries();
    for (const chartType in StatsChartType) {
      this.charts[chartType].resetZoom();
    }
  }

  onFullScreen(chartType?: string) {
    this.isFullscreen = !this.isFullscreen;
    if (this.isFullscreen) {
      this.fullscreenChart = chartType;
      let height = 90;
      if (this.brokerIds.length > 1) {
        height = height - (this.brokerIds.length * 2);
      }
      this.chartContainerHeight = height + '%';
    } else {
      this.fullscreenChart = undefined;
      this.chartContainerHeight = this.chartHeight + 'px';
    }
  }

  processedBytesUnitTypeChanged(type = DataSizeUnitType.BYTE) {
    const chartType = StatsChartType.processedBytes;
    for (let i = 0; i < this.brokerIds.length; i++) {
      this.charts[chartType].data.datasets[i].data = this.charts[chartType].data.datasets[i].data.map(el => {
        return {
          value: convertDataSizeUnits(el.value, this.currentDataSizeUnitType, type),
          ts: el.ts
        }
      });
    }
    this.currentDataSizeUnitType = type;
    this.updateChartView(chartType);
  }

  onLegendKeyEnter(legendKey: LegendKey, chartType: StatsChartType) {
    const datasetIndex = legendKey.dataIndex;
    this.charts[chartType].data.datasets[datasetIndex].borderWidth = 4;
    this.updateChart(chartType);
  }

  onLegendKeyLeave(legendKey: LegendKey, chartType: StatsChartType) {
    const datasetIndex = legendKey.dataIndex;
    this.charts[chartType].data.datasets[datasetIndex].borderWidth = 2;
    this.updateChart(chartType);
  }

  toggleLegendKey(legendKey: LegendKey, chartType: StatsChartType) {
    const datasetIndex = legendKey.dataIndex;
    this.charts[chartType].isDatasetVisible(datasetIndex)
      ? this.charts[chartType].hide(datasetIndex)
      : this.charts[chartType].show(datasetIndex);
    this.updateLegendLabel(chartType, datasetIndex, this.charts[chartType].isDatasetVisible(datasetIndex));
  }

  trackByIndex(index: number): number {
    return index;
  }

  legendValue(index: number, chartType: StatsChartType, type: string): number {
    if (this.totalOnly(chartType)) {
      return this.legendData[chartType]?.data[0][type];
    } else {
      return this.legendData[chartType]?.data[index][type];
    }
  }

  totalOnly(chartType: string): boolean {
    return CHART_TOTAL_ONLY.includes(chartType as StatsChartType);
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
          CHART_ALL,
          MAX_DATAPOINTS_LIMIT,
          this.timewindow.aggregation.type,
          this.timeService.timewindowGroupingInterval(this.timewindow)
        )
      );
    }
    forkJoin($getEntityTimeseriesTasks)
      .pipe(takeUntil(this.stopPolling$))
      .subscribe(data => {
        if (initCharts) {
          this.initCharts(data as TimeseriesData[]);
        } else {
          for (const chartType in StatsChartType) {
            for (let i = 0; i < this.brokerIds.length; i++) {
              if (this.totalOnly(chartType)) {
                this.charts[chartType].data.datasets[0].data = data[0][chartType];
              } else {
                this.charts[chartType].data.datasets[i].data = data[i][chartType];
              }
              this.updateChartView(chartType);
            }
          }
        }
        if (this.timewindow.selectedTab === TimewindowType.REALTIME) {
          this.startPolling();
        }
        this.checkMaxAllowedDataLength(data);
      });
  }

  private initCharts(data: TimeseriesData[]) {
    for (const chartType in StatsChartType) {
      this.charts[chartType] = {} as Chart;
      const ctx = document.getElementById(chartType + this.chartPage) as HTMLCanvasElement;
      const datasets = {data: {datasets: []}};
      this.resetLegendKeys(chartType);
      this.updateLegendKeys(chartType);
      this.resetLegendData(chartType);
      for (let i = 0; i < this.brokerIds.length; i++) {
        const brokerId = this.brokerIds[i];
        if (this.totalOnly(chartType)) {
          if (brokerId === TOTAL_KEY) {
            datasets.data.datasets.push(this.getDataset(data, chartType, i, brokerId));
            this.updateLegendData(data[i][chartType], chartType);
          }
        } else {
          datasets.data.datasets.push(this.getDataset(data, chartType, i, brokerId));
          this.updateLegendData(data[i][chartType], chartType);
        }
      }
      const params = {...chartJsParams(this.chartPage), ...datasets};
      this.charts[chartType] = new Chart(ctx, params as ChartConfiguration);
      if (chartType === StatsChartType.processedBytes) {
        this.charts[chartType].options.plugins.tooltip.callbacks.label = (context) => {
          const value = Number.isInteger(context.parsed.y) ? context.parsed.y : context.parsed.y.toFixed(2);
          return `${value} ${this.dataSizeUnitTypeTranslationMap.get(this.currentDataSizeUnitType)}`;
        }
      }
      this.updateXScale(chartType);
      ctx.addEventListener('dblclick', () => {
        this.charts[chartType].resetZoom();
        this.updateChartView(chartType);
      });
    }
  }

  private getDataset(dataset, chartType, i, brokerId): ChartDataset {
    const color = getColor(chartType, i);
    return {
      label: brokerId,
      data: dataset ? dataset[i][chartType] : null,
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
      $getLatestTimeseriesTasks.push(this.statsService.getLatestTimeseries(brokerId, CHART_ALL));
    }
    timer(0, POLLING_INTERVAL)
    .pipe(
      switchMap(() => forkJoin($getLatestTimeseriesTasks)),
      takeUntil(this.stopPolling$),
      share()
    ).subscribe(data => {
      this.addPollingIntervalToTimewindow();
      for (const chartType in StatsChartType) {
        this.prepareData(chartType, data);
        this.pushLatestValue(data as TimeseriesData[], chartType);
        this.updateChartView(chartType);
      }
    });
  }

  private addPollingIntervalToTimewindow() {
    this.fixedWindowTimeMs.startTimeMs += POLLING_INTERVAL;
    this.fixedWindowTimeMs.endTimeMs += POLLING_INTERVAL;
  }

  private prepareData(chartType: string, data: TimeseriesData[]) {
    if (chartType === StatsChartType.processedBytes) {
      const tsValue = data[0][StatsChartType.processedBytes][0];
      data[0][StatsChartType.processedBytes][0] = {
        // @ts-ignore
        value: convertDataSizeUnits(tsValue.value, DataSizeUnitType.BYTE, this.currentDataSizeUnitType),
        ts: tsValue.ts
      }
      this.processedBytesUnitTypeChanged(this.currentDataSizeUnitType);
    }
  }

  private pushLatestValue(data: TimeseriesData[], chartType: string) {
    for (let i = 0; i < this.brokerIds.length; i++) {
      let index = i;
      if (data[index][chartType]?.length) {
        if (this.totalOnly(chartType)) {
          index = 0;
        }
        const latestValue = data[index][chartType][0];
        this.charts[chartType].data.datasets[index].data.unshift(latestValue);
      }
    }
  }

  private updateChartView(chartType: string) {
    this.updateXScale(chartType);
    this.updateChart(chartType);
    this.updateLegend(chartType);
  }

  private stopPolling() {
    this.stopPolling$.next();
  }

  private updateChart(chartType: string) {
    this.charts[chartType].update('none');
  }

  private updateXScale(chartType: string) {
    if (this.hoursInRange() <= 24) {
      this.charts[chartType].options.scales.x.time.displayFormats.minute = 'HH:mm';
    } else if (this.hoursInRange() <= (24 * 30)) {
      this.charts[chartType].options.scales.x.time.displayFormats.minute = 'MMM-DD HH:mm';
    } else {
      this.charts[chartType].options.scales.x.time.displayFormats.minute = 'MMM-DD';
    }
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

  private resetLegendKeys(chartType: string) {
    this.legendKeys[chartType] = {};
    this.legendKeys[chartType].keys = [];
  }

  private resetLegendData(chartType: string) {
    this.legendData[chartType] = {};
    this.legendData[chartType].data = [];
  }

  private updateLegendKeys(chartType: string) {
    for (let i = 0; i < this.brokerIds.length; i++) {
      const color = getColor(chartType, i);
      const brokerId = this.brokerIds[i];
      if (this.totalOnly(chartType)) {
        if (brokerId === TOTAL_KEY) {
          this.addLegendKey(chartType, 0, brokerId, color);
        }
      } else {
        this.addLegendKey(chartType, i, brokerId, color);
      }
    }
  }

  private updateLegendData(data: any[], chartType: string) {
    if (data?.length) {
      this.legendData[chartType].data.push({
        min: Math.floor(calculateMin(data)),
        max: Math.floor(calculateMax(data)),
        avg: Math.floor(calculateAvg(data)),
        total: Math.floor(calculateTotal(data)),
        latest: Math.floor(calculateLatest(data))
      });
    } else {
      this.legendData[chartType].data.push({
        min: 0,
        max: 0,
        avg: 0,
        total: 0,
        latest: 0
      });
    }
  }

  private addLegendKey(chartType: string, index: number, brokerId: string, color: string) {
    this.legendKeys[chartType].keys.push({
      dataKey: {
        label: brokerId,
        color,
        hidden: brokerId !== TOTAL_KEY
      },
      dataIndex: index
    });
  }

  private updateLegend(chartType: string) {
    this.resetLegendData(chartType);
    for (let i = 0; i < this.brokerIds.length; i++) {
      const brokerId = this.brokerIds[i];
      const data = this.charts[chartType].data.datasets[i]?.data;
      if (this.totalOnly(chartType)) {
        if (brokerId === TOTAL_KEY) {
          this.updateLegendData(data, chartType);
        }
      } else {
        this.updateLegendData(data, chartType);
      }
    }
  }

  private updateLegendLabel(chartType, datasetIndex, isDatasetvisible) {
    this.legendKeys[chartType].keys[datasetIndex].dataKey.color = isDatasetvisible ? getColor(chartType, datasetIndex) : null;
    this.legendKeys[chartType].keys[datasetIndex].dataKey.hidden = !this.legendKeys[chartType].keys[datasetIndex].dataKey.hidden;
    this.cd.detectChanges();
  }
}
