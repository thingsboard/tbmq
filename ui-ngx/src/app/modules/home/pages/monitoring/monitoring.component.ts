///
/// Copyright © 2016-2023 The Thingsboard Authors
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

// @ts-nocheck

import {Component} from '@angular/core';
import {calculateFixedWindowTimeMs, FixedWindow, Timewindow, TimewindowType} from '@shared/models/time/time.models';
import {forkJoin, Observable, Subject, timer} from 'rxjs';
import {TranslateService} from '@ngx-translate/core';
import {TimeService} from '@core/services/time.service';
import {chartKeysTotal, getTimeseriesDataLimit, StatsService} from '@core/http/stats.service';
import {share, switchMap, takeUntil} from 'rxjs/operators';
import {
  ChartTooltipTranslationMap,
  getColor,
  monitoringChartJsParams,
  ONLY_TOTAL_KEYS,
  StatsChartType,
  StatsChartTypeTranslationMap,
  TimeseriesData,
  TOTAL_KEY
} from '@shared/models/chart.model';
import {PageComponent} from '@shared/components/page.component';
import {AppState} from '@core/core.state';
import {Store} from '@ngrx/store';
import Chart from 'chart.js/auto';
import 'chartjs-adapter-moment';
import Zoom from 'chartjs-plugin-zoom';
import {POLLING_INTERVAL} from '@shared/models/home-page.model';
import {ActivatedRoute} from '@angular/router';
import {ActionNotificationShow} from '@core/notification/notification.actions';

Chart.register([Zoom]);

@Component({
  selector: 'tb-monitoring',
  templateUrl: './monitoring.component.html',
  styleUrls: ['./monitoring.component.scss']
})
export class MonitoringComponent extends PageComponent {

  chartIdSuf = 'monitoring';
  charts = {};
  timewindow: Timewindow;
  statsCharts = Object.values(StatsChartType);
  statChartTypeTranslationMap = StatsChartTypeTranslationMap;
  isFullscreen = false;
  chartHeight = 300;
  chartContainerHeight;
  fullscreenChart;

  private fixedWindowTimeMs: FixedWindow;
  private brokerIds: string[];

  private stopPolling$ = new Subject();
  private destroy$ = new Subject();

  chartTooltip = (chartType: string) => this.translate.instant(ChartTooltipTranslationMap.get(chartType));

  constructor(protected store: Store<AppState>,
              private translate: TranslateService,
              private timeService: TimeService,
              private statsService: StatsService,
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
    $(document).on('keydown',
    (event) => {
      if ((event.which === 27) && this.isFullscreen) {
        event.preventDefault();
        this.onFullScreen();
      }
    });
  }

  onTimewindowChange() {
    this.stopPolling();
    this.calculateFixedWindowTimeMs();
    this.fetchEntityTimeseries();
    for (const chartType in StatsChartType) {
      this.charts[chartType].resetZoom();
    }
  }

  onFullScreen(chartType: string) {
    this.isFullscreen = !this.isFullscreen;
    if (this.isFullscreen) {
      this.fullscreenChart = chartType;
      this.chartContainerHeight = '90%';
    } else {
      this.fullscreenChart = undefined;
      this.chartContainerHeight = this.chartHeight + 'px';
    }
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
      $getEntityTimeseriesTasks.push(this.statsService.getEntityTimeseries(brokerId, this.fixedWindowTimeMs.startTimeMs, this.fixedWindowTimeMs.endTimeMs, chartKeysTotal));
    }
    forkJoin($getEntityTimeseriesTasks)
      .pipe(takeUntil(this.stopPolling$))
      .subscribe(data => {
        if (initCharts) {
          this.initCharts(data as TimeseriesData[]);
        } else {
          for (const chartType in StatsChartType) {
            for (let i = 0; i < this.brokerIds.length; i++) {
              const brokerId = this.brokerIds[i];
              if (!ONLY_TOTAL_KEYS.includes(chartType)) {
                this.charts[chartType].data.datasets[i].data = data[i][chartType];
              } else {
                this.charts[chartType].data.datasets[0].data = data[0][chartType];
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
    const getDataset = (dataset, chartType, i, brokerId) => {
      const color = getColor(chartType, i);
      return {
        label: this.brokerIds[i],
        data: dataset ? dataset[i][chartType] : null,
        pointStyle: 'circle',
        hidden: brokerId !== TOTAL_KEY,
        borderColor: color,
        backgroundColor: color,
        pointHoverBackgroundColor: color,
        pointBorderColor: 'rgba(0,0,0,0)',
        pointBackgroundColor: 'rgba(0,0,0,0)',
        pointHoverBorderColor: color,
        pointBorderWidth: 0
      };
    };
    for (const chartType in StatsChartType) {
      this.charts[chartType] = {} as Chart;
      const ctx = document.getElementById(chartType + this.chartIdSuf) as HTMLCanvasElement;
      const datasets = {data: {datasets: []}};
      for (let i = 0; i < this.brokerIds.length; i++) {
        const brokerId = this.brokerIds[i];
        if (ONLY_TOTAL_KEYS.includes(chartType)) {
          if (brokerId === TOTAL_KEY) {
            datasets.data.datasets.push(getDataset(data, chartType, i, brokerId));
          }
        } else {
          datasets.data.datasets.push(getDataset(data, chartType, i, brokerId));
        }
      }
      const params = {...monitoringChartJsParams(), ...datasets};
      this.charts[chartType] = new Chart(ctx, params);
      this.updateXScale(chartType);
      ctx.addEventListener('dblclick', () => {
        this.charts[chartType].resetZoom();
        this.updateChartView(chartType);
      });
    }
  }

  private startPolling() {
    const $getLatestTimeseriesTasks: Observable<TimeseriesData>[] = [];
    for (const brokerId of this.brokerIds) {
      $getLatestTimeseriesTasks.push(this.statsService.getLatestTimeseries(brokerId, chartKeysTotal));
    }
    timer(0, POLLING_INTERVAL)
    .pipe(
      switchMap(() => forkJoin($getLatestTimeseriesTasks)),
      takeUntil(this.stopPolling$),
      share()
    ).subscribe(data => {
      this.addPollingIntervalToTimewindow();
      for (const chartType in StatsChartType) {
        this.pushLatestValue(data as TimeseriesData[], chartType);
        this.updateChartView(chartType);
      }
    });
  }

  private addPollingIntervalToTimewindow() {
    this.fixedWindowTimeMs.startTimeMs += POLLING_INTERVAL;
    this.fixedWindowTimeMs.endTimeMs += POLLING_INTERVAL;
  }

  private pushLatestValue(data: TimeseriesData[], chartType: string) {
    for (let i = 0; i < this.brokerIds.length; i++) {
      let index = i;
      if (data[index][chartType]?.length) {
        if (ONLY_TOTAL_KEYS.includes(chartType)) index = 0;
        const latestValue = data[index][chartType][0];
        this.charts[chartType].data.datasets[index].data.unshift(latestValue);
      }
    }
  }

  private updateChartView(chartType: string) {
    this.updateXScale(chartType);
    this.updateChart(chartType);
  }

  private stopPolling() {
    this.stopPolling$.next();
  }

  private updateChart(chartType: string) {
    this.charts[chartType].update();
  }

  private updateXScale(chartType: string) {
    if (this.isNotZoomed(chartType)) {
      this.charts[chartType].options.scales.x.min = this.fixedWindowTimeMs.startTimeMs;
      this.charts[chartType].options.scales.x.max = this.fixedWindowTimeMs.endTimeMs;
    }
    if (this.inHourRange()) {
      this.charts[chartType].options.scales.x.time.unit = 'minute';
    } else if (this.inDayRange()) {
      this.charts[chartType].options.scales.x.time.unit = 'hour';
    } else {
      this.charts[chartType].options.scales.x.time.unit = 'day';
    }
  }

  private inHourRange(): boolean {
    const hourMs = 1000 * 60 * 60;
    return (this.fixedWindowTimeMs.endTimeMs - this.fixedWindowTimeMs.startTimeMs) / hourMs <= 1;
  }

  private inDayRange(): boolean {
    const hourMs = 1000 * 60 * 60;
    return (this.fixedWindowTimeMs.endTimeMs - this.fixedWindowTimeMs.startTimeMs) / hourMs <= 24;
  }

  private isNotZoomed(chartType) {
    return this.charts[chartType].getZoomLevel() === 1;
  }

  private checkMaxAllowedDataLength(data) {
    let showWarning = false;
    for (const brokerData of data) {
      for (const key in brokerData) {
        const dataLength = brokerData[key].length;
        if (dataLength === getTimeseriesDataLimit) {
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
}
