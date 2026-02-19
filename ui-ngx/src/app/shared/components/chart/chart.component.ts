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
  AfterViewInit,
  Component, computed,
  input,
  model,
  OnChanges,
  OnDestroy,
  OnInit,
  signal,
  SimpleChanges,
  ViewChild
} from '@angular/core';
import {
  calculateFixedWindowTimeMs,
  FixedWindow,
  MINUTE,
  Timewindow,
  TimewindowType
} from '@shared/models/time/time.models';
import { forkJoin, Observable, Subject } from 'rxjs';
import { TranslateModule, TranslateService } from '@ngx-translate/core';
import { TimeService } from '@core/services/time.service';
import { TimeseriesService } from '@core/http/timeseries.service';
import { share, switchMap, takeUntil } from 'rxjs/operators';
import {
  chartJsParams,
  ChartView,
  CHARTS_TOTAL_ENTITY_ID_ONLY,
  getColor,
  MAX_DATAPOINTS_LIMIT,
  ChartKey,
  TimeseriesData,
  TOTAL_ENTITY_ID,
  TsValue,
} from '@shared/models/chart.model';
import { AppState } from '@core/core.state';
import { Store } from '@ngrx/store';
import { POLLING_INTERVAL } from '@shared/models/home-page.model';
import { ActivatedRoute } from '@angular/router';
import { ActionNotificationShow } from '@core/notification/notification.actions';
import { DataSizeUnit, DataSizeUnitTranslationMap } from '@shared/models/ws-client.model';
import { convertDataSizeUnits } from '@core/utils';
import { ChartConfiguration, ChartDataset } from 'chart.js';
import { FullscreenDirective } from '@shared/components/fullscreen.directive';
import { MatProgressBar } from '@angular/material/progress-bar';
import { ChartToolbarComponent } from '@shared/components/chart/chart-toolbar.component';
import { ChartCanvasComponent } from '@shared/components/chart/chart-canvas.component';
import { ChartLegendComponent } from '@shared/components/chart/chart-legend.component';

import Chart from 'chart.js/auto';
import Zoom from 'chartjs-plugin-zoom';
import 'chartjs-adapter-moment';

Chart.register([Zoom]);

@Component({
  selector: 'tb-chart',
  templateUrl: './chart.component.html',
  styleUrls: ['./chart.component.scss'],
  imports: [FullscreenDirective, TranslateModule, MatProgressBar, ChartToolbarComponent, ChartCanvasComponent, ChartLegendComponent]
})
export class ChartComponent implements OnInit, AfterViewInit, OnDestroy, OnChanges {

  @ViewChild(ChartLegendComponent) private legendComp: ChartLegendComponent;

  readonly isFullscreen = signal(false);

  readonly chartKey = input<ChartKey>();
  readonly chartHeight = input<number>(300);
  readonly globalTimewindow = input<Timewindow>();
  readonly showLegend = input<boolean>(true);
  readonly showToolbar = input<boolean>(true);
  readonly chartView = input<ChartView>(ChartView.detailed);
  readonly showTimewindow = input<boolean>(true);
  readonly showFullscreen = input<boolean>(true);
  readonly showDataSizeUnitToggle = input<boolean>(true);

  readonly entityIds = signal<string[]>(this.route.snapshot.data.entityIds || [TOTAL_ENTITY_ID]);
  readonly totalEntityIdOnly = computed(() => CHARTS_TOTAL_ENTITY_ID_ONLY.includes(this.chartKey()));
  readonly visibleEntityIds = model<string[]>([TOTAL_ENTITY_ID]);
  readonly chartHasDataSize = computed(() => this.chartKey() === ChartKey.inboundPayloadTraffic || this.chartKey() === ChartKey.outboundPayloadTraffic);
  readonly dataSizeUnit = signal<DataSizeUnit>(DataSizeUnit.BYTE);

  chart: Chart<'line', TsValue[]>;
  dataSizeUnitTranslations = DataSizeUnitTranslationMap;
  timewindow = this.timeService.defaultTimewindow();
  ChartView = ChartView;
  isLoading = false;

  private fixedWindowTimeMs: FixedWindow;
  private stopPolling$ = new Subject<void>();
  private destroy$ = new Subject<void>();

  constructor(protected store: Store<AppState>,
              private translate: TranslateService,
              private timeService: TimeService,
              private timeseriesService: TimeseriesService,
              private route: ActivatedRoute) {
  }

  ngOnInit() {
    this.init();
  }

  ngOnDestroy() {
    this.stopPolling();
    this.destroy$.next();
    this.destroy$.complete();
  }

  ngAfterViewInit(): void {
    this.fetchEntityTimeseries(this.visibleEntityIds(), true, this.getHistoricalDataObservables(this.visibleEntityIds()));
  }

  ngOnChanges(changes: SimpleChanges): void {
    for (const propName of Object.keys(changes)) {
      const change = changes[propName];
      if (!change.firstChange && change.currentValue !== change.previousValue) {
        if (propName === 'globalTimewindow') {
          this.timewindow = change.currentValue;
          this.onTimewindowChange();
        }
      }
    }
  }

  onTimewindowChange(timewindow?: Timewindow) {
    if (timewindow) {
      this.timewindow = timewindow;
    }
    this.isLoading = true;
    this.stopPolling();
    this.calcWindowTime();
    this.dataSizeUnitChange();
    this.fetchEntityTimeseries(this.visibleEntityIds(), false, this.getHistoricalDataObservables(this.visibleEntityIds()));
    this.chart.resetZoom();
  }

  onFullscreenChange(fullscreen: boolean) {
    this.isFullscreen.set(fullscreen);
  }

  dataSizeUnitChange(type = DataSizeUnit.BYTE) {
    const datasets = this.chart?.data?.datasets;
    if (this.chartHasDataSize() && datasets?.length) {
      for (const ds of datasets) {
        if (ds.data?.length) {
          ds.data = ds.data.map(el => ({
            value: convertDataSizeUnits(el.value, this.dataSizeUnit(), type),
            ts: el.ts
          }));
        }
      }
      this.dataSizeUnit.set(type);
      this.updateChartView();
    }
  }

  onEntityIdTimeseriesRequested(dataKey: string) {
    this.fetchEntityTimeseries([dataKey], false, this.getHistoricalDataObservables([dataKey]), false);
  }

  private init() {
    this.timewindow = this.globalTimewindow();
    this.calcWindowTime();
    $(document).on('keydown',
      (event) => {
        if ((event.code === 'Escape') && this.isFullscreen()) {
          event.preventDefault();
          this.onFullscreenChange(false);
        }
      });
  }

  private calcWindowTime() {
    this.fixedWindowTimeMs = calculateFixedWindowTimeMs(this.timewindow);
    this.fixedWindowTimeMs.startTimeMs = Math.ceil(this.fixedWindowTimeMs.startTimeMs / MINUTE) * MINUTE;
  }

  private getHistoricalDataObservables(dataKeys: string[]): Observable<TimeseriesData>[] {
    return this.getTimeseriesData(dataKeys, false);
  }

  private fetchEntityTimeseries(dataKeys: string[], initCharts: boolean, $tasks: Observable<TimeseriesData>[], fetchLatest = true) {
    forkJoin($tasks)
      .pipe(takeUntil(this.stopPolling$))
      .subscribe(data => {
        this.isLoading = false;
        if (initCharts) {
          this.initCharts(data);
        } else {
          if (this.totalEntityIdOnly()) {
            this.chart.data.datasets[0].data = data[0][this.chartKey()];
          } else {
            for (let i = 0; i < dataKeys.length; i++) {
              const dataKey = dataKeys[i];
              const datasetIndex = this.chart.data.datasets.findIndex(ds => ds.label === dataKey);
              if (datasetIndex > -1) {
                this.chart.data.datasets[datasetIndex].data = data[i][this.chartKey()];
              }
            }
          }
          this.updateChartView();
        }
        if (fetchLatest && this.timewindow.selectedTab === TimewindowType.REALTIME) {
          this.startPolling();
        }
        this.checkMaxAllowedDataLength(data);
      });
  }

  private getHistoricalData(dataKey: string): Observable<TimeseriesData> {
    return this.timeseriesService.getEntityTimeseries(
      dataKey,
      this.fixedWindowTimeMs.startTimeMs,
      this.fixedWindowTimeMs.endTimeMs,
      [this.chartKey()],
      MAX_DATAPOINTS_LIMIT,
      this.timewindow.aggregation.type,
      this.timeService.timewindowGroupingInterval(this.timewindow)
    );
  }

  private initCharts(data: TimeseriesData[]) {
    const ctx = document.getElementById(this.chartKey()) as HTMLCanvasElement;
    const datasets = {data: {datasets: []}};
    for (let i = 0; i < this.entityIds().length; i++) {
      const dataKey = this.entityIds()[i];
      const value = this.visibleEntityIds().includes(dataKey) ? data : null
      datasets.data.datasets.push(this.getDataset(value, i, dataKey));
    }
    const params = {...chartJsParams(this.chartView()), ...datasets} as ChartConfiguration<'line', TsValue[]>;
    this.chart = new Chart<'line', TsValue[]>(ctx, params);
    this.updateChartView();
    this.setListeners(ctx);
  }

  private setListeners(ctx: HTMLCanvasElement) {
    if (this.chartHasDataSize()) {
      this.chart.options.plugins.tooltip.callbacks.label = (context) => {
        const value = Number.isInteger(context.parsed.y) ? context.parsed.y : context.parsed.y.toFixed(2);
        return `${value} ${this.dataSizeUnitTranslations.get(this.dataSizeUnit())}`;
      }
    }
    ctx.addEventListener('dblclick', () => {
      this.chart.resetZoom();
      this.updateChartView();
    });
  }

  private getDataset(dataset: any[], i: number, dataKey: string): ChartDataset {
    const color = getColor(this.chartKey(), i);
    return {
      label: dataKey,
      data: dataset ? dataset[i][this.chartKey()] : null,
      pointStyle: 'circle',
      hidden: dataKey !== TOTAL_ENTITY_ID,
      borderColor: color,
      backgroundColor: color,
      pointHoverBackgroundColor: color,
      pointBorderColor: color,
      pointBackgroundColor: color,
      pointHoverBorderColor: color,
      pointRadius: 0,
      clip: 5,
      tension: 0.1,
    };
  }

  private startPolling() {
    this.timeService.getSyncTimer()
      .pipe(
        switchMap(() => forkJoin(this.getTimeseriesData(this.visibleEntityIds(), true))),
        takeUntil(this.stopPolling$),
        share()
      )
      .subscribe(data => {
        this.addPollingIntervalToTimewindow();
        this.prepareData(data as TimeseriesData[]);
        this.pushLatestValue(this.visibleEntityIds(), data);
        this.updateChartView();
      });
  }

  private getLatestData(dataKey: string) {
    return this.timeseriesService.getLatestTimeseries(dataKey, [this.chartKey()]);
  }

  private addPollingIntervalToTimewindow() {
    this.fixedWindowTimeMs.startTimeMs += POLLING_INTERVAL;
    this.fixedWindowTimeMs.endTimeMs += POLLING_INTERVAL;
  }

  private prepareData(data: TimeseriesData[]) {
    if (this.chartHasDataSize() && data[0]?.[this.chartKey()]?.length) {
      const tsValue = data[0][this.chartKey()][0];
      data[0][this.chartKey()][0] = {
        value: convertDataSizeUnits(tsValue.value, DataSizeUnit.BYTE, this.dataSizeUnit()),
        ts: tsValue.ts
      } as TsValue;
    }
  }

  private pushLatestValue(dataKeys: string[], data: TimeseriesData[]) {
    for (let i = 0; i < dataKeys.length; i++) {
      const dataKey = dataKeys[i];
      if (this.totalEntityIdOnly()) {
        if (dataKey !== TOTAL_ENTITY_ID) {
          continue;
        }
        if (data[0][this.chartKey()]?.length) {
          const latestValue = data[0][this.chartKey()][0];
          const chartData = this.chart.data.datasets[0].data;
          const chartLatestValue = chartData[0];
          if (!chartLatestValue || latestValue?.ts > chartLatestValue?.ts) {
            this.chart.data.datasets[0].data.unshift(latestValue);
            this.chart.data.datasets[0].data.pop();
          }
        }
      } else {
        const datasetIndex = this.chart.data.datasets.findIndex(ds => ds.label === dataKey);
        if (datasetIndex > -1 && data[i]?.[this.chartKey()]?.length) {
          const latestValue = data[i][this.chartKey()][0];
          const chartData = this.chart.data.datasets[datasetIndex].data;
          const chartLatestValue = chartData[0];
          if (!chartLatestValue || latestValue?.ts > chartLatestValue?.ts) {
            this.chart.data.datasets[datasetIndex].data.unshift(latestValue);
            this.chart.data.datasets[datasetIndex].data.pop();
          }
        }
      }
    }
  }

  private updateChartView() {
    this.updateXScale();
    this.updateLegend();
    this.updateChart();
  }

  private stopPolling() {
    this.stopPolling$.next();
  }

  private updateChart() {
    this.chart.update('none');
  }

  private updateLegend() {
    this.legendComp?.updateLegend();
  }

  private updateXScale() {
    if (!this.chart.isZoomedOrPanned()) {
      this.calcWindowTime();
      this.chart.options.scales.x.min = this.fixedWindowTimeMs.startTimeMs;
      this.chart.options.scales.x.max = this.fixedWindowTimeMs.endTimeMs;
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

  private getTimeseriesData(dataKeys: string[], latest = false): Observable<TimeseriesData>[] {
    const tasks: Observable<TimeseriesData>[] = [];
    for (const dataKey of dataKeys) {
      if (this.totalEntityIdOnly() && dataKey !== TOTAL_ENTITY_ID) {
        continue;
      }
      tasks.push(latest ? this.getLatestData(dataKey) : this.getHistoricalData(dataKey));
    }
    return tasks;
  }
}
