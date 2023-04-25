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
  TimeseriesData, TOTAL_KEY, TsValue
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
  private brokerIds: Array<string> = ['artem', 'artem2', 'artem3', TOTAL_KEY];

  private getLatestTimeseries = this.configService.getBrokerServiceIds().pipe(
    mergeMap((ids) => {
      const $tasks = [];
      for (const id of this.brokerIds) {
        $tasks.push(this.statsService.getLatestTimeseries(id));
      }
      return forkJoin($tasks);
    })
  );

  private $getEntityTimeseries = this.configService.getBrokerServiceIds().pipe(
    mergeMap((ids) => {
      const $tasks = [];
      for (const id of this.brokerIds) {
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
    // this.generateRandomData();
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
          this.initCharts(data as TimeseriesData[]);
        } else {
          for (const chartType in StatsChartType) {
            for (let i = 0; i < this.brokerIds.length; i++) {
              const brokerId = this.brokerIds[i];
              this.latestValues[brokerId] = {};
              this.latestValues[brokerId][chartType] = 0;
              this.charts[chartType].data.datasets[i].data = data[i][chartType];
              this.updateChartView(data as TimeseriesData[], chartType);
            }
          }
        }
        if (this.timewindow.selectedTab === TimewindowType.REALTIME) {
          this.startPolling();
        }
      });
  }

  private initCharts(data: TimeseriesData[]) {
    for (const chartType in StatsChartType) {
      this.charts[chartType] = {} as Chart;
      const ctx = document.getElementById(chartType + this.chartIdSuf) as HTMLCanvasElement;
      const datasets = {
        data: {
          datasets: []
        }
      };
      for (let i = 0; i < this.brokerIds.length; i++) {
        const brokerId = this.brokerIds[i];
        this.latestValues[brokerId] = {};
        this.latestValues[brokerId][chartType] = 0;

        datasets.data.datasets.push({
          data: data[i][chartType],
          borderColor: getColor(chartType, i),
          backgroundColor: getColor(chartType, i),
          hidden: brokerId === TOTAL_KEY,
          pointStyle: 'line',
        });
      }
      const params = {...monitoringChartJsParams(), ...datasets};
      this.charts[chartType] = new Chart(ctx, params);
      this.updateXScale(chartType);
      ctx.addEventListener('dblclick', () => {
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
        this.pushLatestValue(data as TimeseriesData[], chartType);
        this.updateChartView(data as TimeseriesData[], chartType);
      }
    });
  }

  private addPollingIntervalToTimewindow() {
    this.fixedWindowTimeMs.startTimeMs += POLLING_INTERVAL;
    this.fixedWindowTimeMs.endTimeMs += POLLING_INTERVAL;
  }

  private pushLatestValue(data: TimeseriesData[], chartType: string) {
    for (let i = 0; i < this.brokerIds.length; i++) {
      if (data[i][chartType].length) {
        const latestValue = data[i][chartType][0];
        latestValue.ts = this.fixedWindowTimeMs.endTimeMs;
        this.addStartChartValue(chartType, latestValue);
        this.charts[chartType].data.datasets[i].data.unshift(latestValue);
      }
    }
  }

  private updateChartView(data: TimeseriesData[], chartType: string) {
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

  private updateLabel(data: TimeseriesData[], chartType: string) {
    for (let i = 0; i < this.brokerIds.length; i++) {
      const brokerId = this.brokerIds[i];
      this.latestValues[brokerId][chartType] = data[i][chartType][0].value;
      this.charts[chartType].data.datasets[i].label = `${this.brokerIds[i]}: ${this.latestValues[this.brokerIds[i]][chartType]}`;
    }
  }

  private updateXScale(chartType: string) {
    this.charts[chartType].options.scales.x.min = this.fixedWindowTimeMs.startTimeMs;
    this.charts[chartType].options.scales.x.max = this.fixedWindowTimeMs.endTimeMs;
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
    return (this.fixedWindowTimeMs.endTimeMs - this.fixedWindowTimeMs.startTimeMs) / hourMs < 24;
  }

  private addStartChartValue(chartType: string, latestValue: TsValue) {
    const data = this.charts[chartType].data.datasets[0].data;
    if (!data.length) {
      data.push({ value: latestValue.value, ts: this.fixedWindowTimeMs.startTimeMs });
    }
  }

  private generateRandomData() {
    timer(0, POLLING_INTERVAL * 6)
      .pipe(switchMap(() => {
          const data = {
            incomingMsgs: Math.floor(Math.random() * 100),
            outgoingMsgs: Math.floor(Math.random() * 100),
            droppedMsgs: Math.floor(Math.random() * 100),
            sessions: Math.floor(Math.random() * 100),
            subscriptions: Math.floor(Math.random() * 100)
          };
          return this.statsService.saveTelemetry('artem', data);
        })).subscribe();
    timer(0, POLLING_INTERVAL * 6)
      .pipe(switchMap(() => {
        const data = {
          incomingMsgs: Math.floor(Math.random() * 100),
          outgoingMsgs: Math.floor(Math.random() * 100),
          droppedMsgs: Math.floor(Math.random() * 100),
          sessions: Math.floor(Math.random() * 100),
          subscriptions: Math.floor(Math.random() * 100)
        };
        return this.statsService.saveTelemetry('artem2', data);
      })).subscribe();
    timer(0, POLLING_INTERVAL * 6)
      .pipe(switchMap(() => {
        const data = {
          incomingMsgs: Math.floor(Math.random() * 100),
          outgoingMsgs: Math.floor(Math.random() * 100),
          droppedMsgs: Math.floor(Math.random() * 100),
          sessions: Math.floor(Math.random() * 100),
          subscriptions: Math.floor(Math.random() * 100)
        };
        return this.statsService.saveTelemetry('artem3', data);
      })).subscribe();
    timer(0, POLLING_INTERVAL * 6)
      .pipe(switchMap(() => {
        const data = {
          incomingMsgs: Math.floor(Math.random() * 100),
          outgoingMsgs: Math.floor(Math.random() * 100),
          droppedMsgs: Math.floor(Math.random() * 100),
          sessions: Math.floor(Math.random() * 100),
          subscriptions: Math.floor(Math.random() * 100)
        };
        return this.statsService.saveTelemetry(TOTAL_KEY, data);
      })).subscribe();
  }
}
