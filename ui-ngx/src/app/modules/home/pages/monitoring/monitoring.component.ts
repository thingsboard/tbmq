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

import { Component } from '@angular/core';
import {
  calculateFixedWindowTimeMs,
  FixedWindow,
  Timewindow,
  TimewindowType
} from '@shared/models/time/time.models';
import { forkJoin, Subject, timer } from 'rxjs';
import { TranslateService } from '@ngx-translate/core';
import { TimeService } from '@core/services/time.service';
import { StatsService } from '@core/http/stats.service';
import { mergeMap, retry, share, switchMap, takeUntil } from 'rxjs/operators';
import {
  getColor,
  monitoringChartJsParams,
  StatsChartType,
  StatsChartTypeTranslationMap,
  TimeseriesData, TsValue
} from '@shared/models/chart.model';
import { PageComponent } from '@shared/components/page.component';
import { AppState } from '@core/core.state';
import { Store } from '@ngrx/store';
import Chart from 'chart.js/auto';
import 'chartjs-adapter-moment';
import Zoom from 'chartjs-plugin-zoom';
import { POLLING_INTERVAL } from '@shared/models/home-page.model';
import { ConfigService } from '@core/http/config.service';

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
  latestValues = {};

  private stopPolling$ = new Subject();
  private destroy$ = new Subject();
  private fixedWindowTimeMs: FixedWindow;

  private getLatestTimeseries = this.configService.getBrokerServiceIds().pipe(
    mergeMap((ids) => {
      const $tasks = [];
      for (const id of ids) {
        $tasks.push(this.statsService.getLatestTimeseries(id));
      }
      return forkJoin($tasks);
    })
  );

  private $getEntityTimeseries = this.configService.getBrokerServiceIds().pipe(
    mergeMap((ids) => {
      const $tasks = [];
      for (const id of ids) {
        $tasks.push(this.statsService.getEntityTimeseries(id, this.fixedWindowTimeMs.startTimeMs, this.fixedWindowTimeMs.endTimeMs));
      }
      return forkJoin($tasks);
    })
  );

  constructor(protected store: Store<AppState>,
              private translate: TranslateService,
              private timeService: TimeService,
              private configService: ConfigService,
              private statsService: StatsService) {
    super(store);
  }

  ngOnInit() {
    this.timewindow = this.timeService.defaultTimewindow();
    this.calculateFixedWindowTimeMs();
  }

  ngOnDestroy() {
    this.stopPolling();
    this.destroy$.next();
    this.destroy$.complete();
  }

  ngAfterViewInit(): void {
    this.fetchEntityTimeseries(true);
  }

  onTimewindowChange() {
    this.stopPolling();
    this.calculateFixedWindowTimeMs();
    this.fetchEntityTimeseries();
  }

  private calculateFixedWindowTimeMs() {
    this.fixedWindowTimeMs = calculateFixedWindowTimeMs(this.timewindow);
  }

  private fetchEntityTimeseries(initCharts = false) {
    this.$getEntityTimeseries
      .pipe(takeUntil(this.stopPolling$))
      .subscribe(data => {
        if (initCharts) {
          this.initCharts(data[0]);
        } else {
          for (const chartType in StatsChartType) {
            if (data[0][chartType]?.length) {
              this.charts[chartType].data.datasets[0].data = data[0][chartType];
              // @ts-ignore
              this.updateChartView(data[0], chartType);
            }
          }
        }
        if (this.timewindow.selectedTab === TimewindowType.REALTIME) {
          this.startPolling();
        }
      });
  }

  private initCharts(data) {
    for (const chartType in StatsChartType) {
      this.charts[chartType] = {} as Chart;
      this.latestValues[chartType] = data[chartType] ? data[chartType][0].value : 0;
      const ctx = document.getElementById(chartType + this.chartIdSuf) as HTMLCanvasElement;
      const datasets = {
        data: {
          datasets: [{
            fill: false,
            backgroundColor: 'transparent',
            hoverBackgroundColor: '#999999',
            hover: true,
            pointStyle: 'line',
            borderColor: getColor(chartType),
            data: data[chartType]
          }]
        }
      };
      const params = {...monitoringChartJsParams(), ...datasets};
      this.charts[chartType] = new Chart(ctx, params);
      this.updateXScale(chartType);
      ctx.addEventListener('dblclick', (evt) => {
        this.charts[chartType].resetZoom();
        this.updateXScale(chartType);
        this.updateChart(chartType);
      });
    }
  }

  private startPolling() {
    timer(0, POLLING_INTERVAL)
    .pipe(
      switchMap(() => this.getLatestTimeseries),
      retry(),
      takeUntil(this.stopPolling$),
      share()
    ).subscribe(data => {
      this.addPollingIntervalToTimewindow();
      for (const chartType in StatsChartType) {
        // @ts-ignore
        this.pushLatestValue(data[0], chartType);
        // @ts-ignore
        this.updateChartView(data[0], chartType);
      }
    });
  }

  private addPollingIntervalToTimewindow() {
    this.fixedWindowTimeMs.startTimeMs += POLLING_INTERVAL;
    this.fixedWindowTimeMs.endTimeMs += POLLING_INTERVAL;
  }

  private pushLatestValue(data: TimeseriesData, chartType: string) {
    if (data[chartType].length) {
      const latestValue = data[chartType][0];
      latestValue.ts = this.fixedWindowTimeMs.endTimeMs;
      this.addStartChartValue(chartType, latestValue);
      this.charts[chartType].data.datasets[0].data.unshift(latestValue);
    }
  }

  private updateChartView(data: TimeseriesData, chartType) {
    this.updateXScale(chartType);
    this.updateLabel(data, chartType);
    this.updateChart(chartType);
  }

  private stopPolling() {
    this.stopPolling$.next();
  }

  private updateChart(chartType: string) {
    this.charts[chartType].update();
  }

  private updateLabel(data: TimeseriesData, chartType: string) {
    this.latestValues[chartType] = data[chartType][0].value;
    this.charts[chartType].data.datasets[0].label = `${this.translate.instant(this.statChartTypeTranslationMap.get(chartType))} - ${this.latestValues[chartType]}`;
  }

  private updateXScale(chartType: string) {
    this.charts[chartType].options.scales.x.min = this.fixedWindowTimeMs.startTimeMs;
    this.charts[chartType].options.scales.x.max = this.fixedWindowTimeMs.endTimeMs;
    if (this.inDayRange()) {
      this.charts[chartType].options.scales.x.time.unit = 'hour';
    } else {
      this.charts[chartType].options.scales.x.time.unit = 'day';
    }
  }

  private inDayRange(): boolean {
    const hourMs = 1000 * 60 * 60;
    return (this.fixedWindowTimeMs.endTimeMs - this.fixedWindowTimeMs.startTimeMs) / hourMs < 24;
  }

  private addStartChartValue(chartType: string, latestValue: TsValue) {
    const data = this.charts[chartType].data.datasets[0].data;
    if (!data.length) {
      data.push({ value: latestValue.value, ts: this.fixedWindowTimeMs.startTimeMs });
    }
  }
}
