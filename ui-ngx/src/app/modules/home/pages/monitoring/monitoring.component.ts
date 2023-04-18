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

import { ChangeDetectorRef, Component } from '@angular/core';
import {
  calculateFixedWindowTimeMs,
  FixedWindow,
  RealtimeWindowType,
  Timewindow,
  TimewindowType
} from '@shared/models/time/time.models';
import { Subject, timer } from 'rxjs';
import { TranslateService } from '@ngx-translate/core';
import { TimeService } from '@core/services/time.service';
import { Router } from '@angular/router';
import { StatsService } from '@core/http/stats.service';
import { retry, shareReplay, switchMap, takeUntil } from 'rxjs/operators';
import {
  getColor,
  monitoringChartJsParams,
  StatsChartType,
  StatsChartTypeTranslationMap,
  TimeseriesData
} from '@shared/models/chart.model';
import { PageComponent } from '@shared/components/page.component';
import { AppState } from '@core/core.state';
import { Store } from '@ngrx/store';
import Chart from 'chart.js/auto';
import 'chartjs-adapter-moment';
import Zoom from 'chartjs-plugin-zoom';

Chart.register([Zoom]);

@Component({
  selector: 'tb-monitoring',
  templateUrl: './monitoring.component.html',
  styleUrls: ['./monitoring.component.scss']
})
export class MonitoringComponent extends PageComponent {

  charts = {};
  timewindow: Timewindow;
  statsCharts = Object.values(StatsChartType);
  statChartTypeTranslationMap = StatsChartTypeTranslationMap;
  latestValues = {};

  private stopPolling$ = new Subject();
  private destroy$ = new Subject();
  private fixedWindowTimeMs: FixedWindow;

  constructor(protected store: Store<AppState>,
              private translate: TranslateService,
              private timeService: TimeService,
              private router: Router,
              private statsService: StatsService,
              private cd: ChangeDetectorRef) {
    super(store);
  }

  ngOnInit() {
    this.timewindow = this.timeService.defaultTimewindow();
    this.fixedWindowTimeMs = calculateFixedWindowTimeMs(this.timewindow);
  }

  ngAfterViewInit(): void {
    const endTs = Date.now();
    const startTs = endTs - this.timewindow.realtime.interval;
    this.statsService.getEntityTimeseries('artem', startTs, endTs)
      .pipe(takeUntil(this.stopPolling$))
      .subscribe(data => this.initCharts(data));
  }

  ngOnDestroy() {
    this.stopPolling$.next();
    this.destroy$.next();
    this.destroy$.complete();
  }

  onTimewindowChange() {
    this.fetchData();
  }

  private initCharts(data) {
    for (const chartType in StatsChartType) {
      this.charts[chartType] = {} as Chart;
      this.latestValues[chartType] = data[chartType] ? data[chartType][0].value : 0;
      const ctx = document.getElementById(chartType + '1') as HTMLCanvasElement;
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
      this.updateScales(chartType);
      ctx.addEventListener('dblclick', (evt) => this.charts[chartType].resetZoom());
    }
    this.startPolling();
  }

  private fetchData() {
    this.fixedWindowTimeMs = calculateFixedWindowTimeMs(this.timewindow);
    if (this.timewindow.selectedTab === TimewindowType.HISTORY) {
      this.stopPolling();
    }
    this.statsService.getEntityTimeseries('artem', this.fixedWindowTimeMs.startTimeMs, this.fixedWindowTimeMs.endTimeMs).pipe(
      takeUntil(this.stopPolling$)
    ).subscribe(data => {
      for (const chartType in StatsChartType) {
        if (data[chartType]?.length) {
          this.charts[chartType].data.datasets[0].data = data[chartType];
          if (!this.charts[chartType].isZoomedOrPanned()) {
            this.updateScales(chartType);
          }
          this.updateLabel(data, chartType);
          this.updateChart(chartType);
        }
      }
      if (this.timewindow.selectedTab === TimewindowType.REALTIME) {
        this.startPolling();
      }
    });
  }

  private startPolling() {
    timer(0, 10000).pipe(
      switchMap(() => this.statsService.getLatestTimeseries('artem')),
      retry(),
      takeUntil(this.stopPolling$),
      shareReplay()
    ).subscribe(data => {
      for (const chartType in StatsChartType) {
        if (this.charts[chartType].data.datasets[0].data.length && data[chartType].length && data[chartType][0].ts > this.charts[chartType].data.datasets[0].data[0].ts) {
          this.pushShiftLatestValue(data, chartType);
        }
        if (this.timewindow.realtime.realtimeType === RealtimeWindowType.LAST_INTERVAL) {
          this.fixedWindowTimeMs.startTimeMs += this.timewindow.realtime.timewindowMs;
          this.fixedWindowTimeMs.endTimeMs += this.timewindow.realtime.timewindowMs;
        } else if (this.timewindow.realtime.realtimeType === RealtimeWindowType.INTERVAL) {
          this.fixedWindowTimeMs.startTimeMs += this.timewindow.realtime.interval;
          this.fixedWindowTimeMs.endTimeMs += this.timewindow.realtime.interval;
        }
        if (!this.charts[chartType].isZoomedOrPanned()) {
          this.updateScales(chartType);
        }
        this.updateLabel(data, chartType);
        this.updateChart(chartType);
      }
    });
  }

  private pushShiftLatestValue(data: TimeseriesData, chartType: string) {
    const latestValue = data[chartType][0];
    this.charts[chartType].data.datasets[0].data.shift();
    this.charts[chartType].data.datasets[0].data.push(latestValue);
  }

  private stopPolling() {
    this.stopPolling$.next();
  }

  private updateChart(chartType: string) {
    this.charts[chartType].update();
  }

  private updateLabel(data: any, chartType: string) {
    this.latestValues[chartType] = data[chartType][0].value;
    this.charts[chartType].data.datasets[0].label = `${this.translate.instant(this.statChartTypeTranslationMap.get(chartType))} - ${this.latestValues[chartType]}`;
  }

  private updateScales(chartType: string) {
    this.fixedWindowTimeMs = calculateFixedWindowTimeMs(this.timewindow);
    console.log('this.fixedWindowTimeMs.endTimeMs', new Date(this.fixedWindowTimeMs.endTimeMs));
    console.log('this.fixedWindowTimeMs.startTime', new Date(this.fixedWindowTimeMs.startTimeMs));
    this.charts[chartType].options.scales.x.min = this.fixedWindowTimeMs.startTimeMs;
    this.charts[chartType].options.scales.x.max = this.fixedWindowTimeMs.endTimeMs;
  }
}
