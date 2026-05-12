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
  Component,
  computed,
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
  buildEChartsOption,
  buildSeries,
  buildTooltipFormatter,
  ChartKey,
  CHARTS_TOTAL_ENTITY_ID_ONLY,
  ChartSeries,
  ChartView,
  getColor,
  MAX_DATAPOINTS_LIMIT,
  TimeseriesData,
  TOTAL_ENTITY_ID,
  TsValue
} from '@shared/models/chart.model';
import { AppState } from '@core/core.state';
import { Store } from '@ngrx/store';
import { POLLING_INTERVAL } from '@shared/models/home-page.model';
import { ActivatedRoute } from '@angular/router';
import { ActionNotificationShow } from '@core/notification/notification.actions';
import { DataSizeUnit, DataSizeUnitLongTranslationMap } from '@shared/models/ws-client.model';
import { convertDataSizeUnits } from '@core/utils';
import { ConfigService } from '@core/http/config.service';
import { FullscreenDirective } from '@shared/components/fullscreen.directive';
import { MatProgressBar } from '@angular/material/progress-bar';
import { ChartToolbarComponent } from '@shared/components/chart/chart-toolbar.component';
import { ChartCanvasComponent } from '@shared/components/chart/chart-canvas.component';
import { ChartLegendComponent } from '@shared/components/chart/chart-legend.component';

import * as echarts from 'echarts/core';
import { LineChart } from 'echarts/charts';
import {
  GridComponent,
  TooltipComponent,
  DataZoomComponent,
} from 'echarts/components';
import { CanvasRenderer } from 'echarts/renderers';
import type { ECharts } from 'echarts/core';

echarts.use([LineChart, GridComponent, TooltipComponent, DataZoomComponent, CanvasRenderer]);

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
  readonly chartIntervalUnit = computed<string>(() => {
    if (CHARTS_TOTAL_ENTITY_ID_ONLY.includes(this.chartKey())) {
      return '';
    }
    const prefix = this.chartHasDataSize() ? DataSizeUnitLongTranslationMap.get(this.dataSizeUnit()) : 'msg';
    const interval = this.configService.brokerConfig.statsCollectionInterval;
    const minStr = interval === 1
      ? this.translate.instant('timewindow.short.minutes', {minutes: ''}).trim()
      : this.translate.instant('timewindow.short.minutes', {minutes: interval}).trim();
    return `${prefix} / ${minStr}`;
  });

  chart: ECharts | null = null;
  series: ChartSeries[] = [];
  timewindow = this.timeService.defaultTimewindow();
  ChartView = ChartView;
  isLoading = false;

  private fixedWindowTimeMs: FixedWindow;
  private stopPolling$ = new Subject<void>();
  private destroy$ = new Subject<void>();
  private resizeObserver?: ResizeObserver;

  constructor(protected store: Store<AppState>,
              private translate: TranslateService,
              private timeService: TimeService,
              private timeseriesService: TimeseriesService,
              private route: ActivatedRoute,
              private configService: ConfigService) {
  }

  ngOnInit() {
    this.init();
  }

  ngOnDestroy() {
    this.stopPolling();
    this.destroy$.next();
    this.destroy$.complete();
    this.resizeObserver?.disconnect();
    this.resizeObserver = undefined;
    if (this.chart) {
      this.chart.dispose();
      this.chart = null;
    }
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
    this.resetZoom();
  }

  onFullscreenChange(fullscreen: boolean) {
    this.isFullscreen.set(fullscreen);
    setTimeout(() => this.chart?.resize());
  }

  dataSizeUnitChange(type = DataSizeUnit.BYTE) {
    if (this.chartHasDataSize() && this.series.length) {
      for (const s of this.series) {
        if (s.data?.length) {
          s.data = s.data.map(el => ({
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

  toggleSeriesVisibility(dataKey: string): boolean {
    const s = this.series.find(it => it.name === dataKey);
    if (!s) {
      return false;
    }
    s.visible = !s.visible;
    this.updateChartView();
    return s.visible;
  }

  getSeries(): ChartSeries[] {
    return this.series;
  }

  private init() {
    this.timewindow = this.globalTimewindow();
    this.calcWindowTime();
    document.addEventListener('keydown', this.escapeHandler);
  }

  private escapeHandler = (event: KeyboardEvent) => {
    if (event.code === 'Escape' && this.isFullscreen()) {
      event.preventDefault();
      this.onFullscreenChange(false);
    }
  };

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
            if (this.series[0]) {
              this.series[0].data = this.toCurrentUnit(data[0][this.chartKey()]);
            }
          } else {
            for (let i = 0; i < dataKeys.length; i++) {
              const dataKey = dataKeys[i];
              const s = this.series.find(it => it.name === dataKey);
              if (s) {
                s.data = this.toCurrentUnit(data[i][this.chartKey()]);
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
    const container = document.getElementById(this.chartKey()) as HTMLElement;
    if (!container) {
      return;
    }
    this.series = [];
    for (let i = 0; i < this.entityIds().length; i++) {
      const dataKey = this.entityIds()[i];
      if (this.totalEntityIdOnly() && dataKey !== TOTAL_ENTITY_ID) {
        continue;
      }
      const color = getColor(this.chartKey(), i);
      const visible = dataKey === TOTAL_ENTITY_ID;
      const seriesData = visible ? this.toCurrentUnit(data[i]?.[this.chartKey()]) : null;
      this.series.push({
        name: dataKey,
        color,
        data: seriesData ?? [],
        visible,
      });
    }
    this.chart = echarts.init(container);
    this.setListeners(container);
    this.updateChartView();
    this.observeResize(container);
  }

  private observeResize(container: HTMLElement) {
    this.resizeObserver?.disconnect();
    this.resizeObserver = new ResizeObserver(() => this.chart?.resize());
    this.resizeObserver.observe(container);
  }

  private setListeners(container: HTMLElement) {
    container.addEventListener('dblclick', () => {
      this.resetZoom();
      this.updateChartView();
    });
  }

  private resetZoom() {
    if (this.chart) {
      this.chart.dispatchAction({ type: 'dataZoom', start: 0, end: 100 });
    }
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

  private toCurrentUnit(data: TsValue[]): TsValue[] {
    if (!data) {
      return [];
    }
    if (!this.chartHasDataSize() || this.dataSizeUnit() === DataSizeUnit.BYTE) {
      return data;
    }
    return data.map(el => ({
      value: convertDataSizeUnits(el.value, DataSizeUnit.BYTE, this.dataSizeUnit()),
      ts: el.ts
    }));
  }

  private prepareData(data: TimeseriesData[]) {
    if (this.chartHasDataSize()) {
      for (const brokerData of data) {
        if (brokerData?.[this.chartKey()]?.length) {
          const tsValue = brokerData[this.chartKey()][0];
          brokerData[this.chartKey()][0] = {
            value: convertDataSizeUnits(tsValue.value, DataSizeUnit.BYTE, this.dataSizeUnit()),
            ts: tsValue.ts
          } as TsValue;
        }
      }
    }
  }

  private pushLatestValue(dataKeys: string[], data: TimeseriesData[]) {
    for (let i = 0; i < dataKeys.length; i++) {
      const dataKey = dataKeys[i];
      if (this.totalEntityIdOnly()) {
        if (dataKey !== TOTAL_ENTITY_ID) {
          continue;
        }
        if (data[0]?.[this.chartKey()]?.length) {
          const latestValue = data[0][this.chartKey()][0];
          const seriesData = this.series[0]?.data;
          if (!seriesData) {
            continue;
          }
          const head = seriesData[0];
          if (!head || latestValue?.ts > head?.ts) {
            seriesData.unshift(latestValue);
          }
        }
      } else {
        const s = this.series.find(it => it.name === dataKey);
        if (s && data[i]?.[this.chartKey()]?.length) {
          const latestValue = data[i][this.chartKey()][0];
          const head = s.data[0];
          if (!head || latestValue?.ts > head?.ts) {
            s.data.unshift(latestValue);
          }
        }
      }
    }
  }

  private updateChartView() {
    this.calcWindowTime();
    this.updateChart();
    this.updateLegend();
  }

  private stopPolling() {
    this.stopPolling$.next();
  }

  private updateChart() {
    if (!this.chart) {
      return;
    }
    const option = buildEChartsOption({
      view: this.chartView(),
      enableZoom: this.chartView() === ChartView.detailed,
      xMin: this.fixedWindowTimeMs.startTimeMs,
      xMax: this.fixedWindowTimeMs.endTimeMs,
      tooltipFormatter: buildTooltipFormatter(() => ({
        intervalUnit: this.chartIntervalUnit(),
        totalEntityIdOnly: this.totalEntityIdOnly(),
      })),
      series: this.series.map(s =>
        buildSeries(s.name, s.color, s.visible ? s.data : null, s.visible)
      ),
    });
    this.chart.setOption(option, { notMerge: false, replaceMerge: ['series'] });
  }

  private updateLegend() {
    this.legendComp?.updateLegend();
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
