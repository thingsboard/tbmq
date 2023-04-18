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

import { AfterViewInit, ChangeDetectorRef, Component, OnDestroy, OnInit } from '@angular/core';
import { Observable, Subject, timer } from 'rxjs';
import { Router } from '@angular/router';
import { TranslateService } from '@ngx-translate/core';
import { StatsService } from '@core/http/stats.service';
import {
  calculateFixedWindowTimeMs,
  FixedWindow,
  RealtimeWindowType,
  Timewindow,
  TimewindowType
} from '@shared/models/time/time.models';
import { TimeService } from '@core/services/time.service';
import { retry, shareReplay, switchMap, takeUntil } from 'rxjs/operators';
import {
  homeChartJsParams,
  getColor,
  StatsChartTypeTranslationMap,
  StatsChartType,
  TimeseriesData
} from '@shared/models/chart.model';
import Chart from 'chart.js/auto';
import { DEFAULT_HOME_CHART_INTERVAL, POLLING_INTERVAL } from "@shared/models/home-page.model";

@Component({
  selector: 'tb-home-charts',
  templateUrl: './home-charts.component.html',
  styleUrls: ['./home-charts.component.scss']
})
export class HomeChartsComponent implements OnInit, OnDestroy, AfterViewInit {

  charts = {};
  chartsLatestValues = {};
  statsCharts = Object.values(StatsChartType);
  pollChartsData$: Observable<Array<any>>;
  statChartTypeTranslationMap = StatsChartTypeTranslationMap;

  private stopPolling$ = new Subject();

  private destroy$ = new Subject();

  private fixedWindowTimeMs: FixedWindow = {
    endTimeMs: Date.now(),
    startTimeMs: Date.now() - DEFAULT_HOME_CHART_INTERVAL
  };

  constructor(private translate: TranslateService,
              private timeService: TimeService,
              private router: Router,
              private statsService: StatsService,
              private cd: ChangeDetectorRef) {
  }

  ngOnInit() {
    this.setTitles();
  }

  ngAfterViewInit(): void {
    this.statsService.getEntityTimeseries('artem',  this.fixedWindowTimeMs.startTimeMs, this.fixedWindowTimeMs.endTimeMs)
      .pipe(takeUntil(this.stopPolling$))
      .subscribe(data => this.initCharts(data));
  }

  ngOnDestroy() {
    this.stopPolling$.next();
    this.destroy$.next();
    this.destroy$.complete();
  }

  private initCharts(data) {
    for (const chart in StatsChartType) {
        this.charts[chart] = {} as Chart;
        const ctx = document.getElementById(chart) as HTMLCanvasElement;
        const label = this.translate.instant(this.statChartTypeTranslationMap.get(chart as StatsChartType));
        const dataSet = {
          label,
          fill: true,
          backgroundColor: 'transparent',
          borderColor: getColor(chart),
          borderWidth: 3,
          data: data[chart],
          hover: true
        };
        const params = {...homeChartJsParams(), ...{ data: {datasets: [dataSet]} }};
        this.charts[chart] = new Chart(ctx, params);
      }
    this.startPolling();
  }

  private setTitles() {
    for (const key of Object.keys(this.charts)) {
      this.chartsLatestValues[key] = key;
    }
  }

  private startPolling() {
    timer(0, POLLING_INTERVAL).pipe(
      switchMap(() => this.statsService.getLatestTimeseries('artem')),
      retry(),
      takeUntil(this.stopPolling$),
      shareReplay()
    ).subscribe(data => {
      for (const chartType in StatsChartType) {
        this.pushShiftLatestValue(chartType, data);
        this.setLatestValues(chartType, data);
        this.updateScales(chartType);
        this.updateChart(chartType);
      }
    });
  }

  private pushShiftLatestValue(chartType: string, data: TimeseriesData) {
    const latestValue = data[chartType][0];
    if (this.charts[chartType].data.datasets[0].data && latestValue.ts > this.charts[chartType].data.datasets[0].data[this.charts[chartType].data.datasets[0].data.length-1]?.ts) {
      // this.charts[chartType].data.datasets[0].data = this.charts[chartType].data.datasets[0].data.filter(el => el.ts > this.fixedWindowTimeMs.startTimeMs);
      this.charts[chartType].data.datasets[0].data.unshift(latestValue);
    }
  }

  private setLatestValues(chartType, data) {
    this.chartsLatestValues[chartType] = data[chartType].length ? data[chartType][0]?.value : 0;
  }

  private stopPolling() {
    this.stopPolling$.next();
  }

  private updateChart(chartType) {
    this.charts[chartType].update();
  }

  private updateScales(chartType: string) {
    this.fixedWindowTimeMs.startTimeMs += POLLING_INTERVAL;
    this.fixedWindowTimeMs.endTimeMs += POLLING_INTERVAL;
    this.charts[chartType].options.scales.x.min = this.fixedWindowTimeMs.startTimeMs;
    this.charts[chartType].options.scales.x.max = this.fixedWindowTimeMs.endTimeMs;
  }
}
