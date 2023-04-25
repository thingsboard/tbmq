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

import { AfterViewInit, Component, OnDestroy, OnInit } from '@angular/core';
import { Subject, timer } from 'rxjs';
import { TranslateService } from '@ngx-translate/core';
import { StatsService } from '@core/http/stats.service';
import { calculateFixedWindowTimeMs, FixedWindow } from '@shared/models/time/time.models';
import { TimeService } from '@core/services/time.service';
import { retry, shareReplay, switchMap, takeUntil } from 'rxjs/operators';
import {
  getColor,
  homeChartJsParams,
  StatsChartType,
  StatsChartTypeTranslationMap,
  TimeseriesData,
  TsValue
} from '@shared/models/chart.model';
import Chart from 'chart.js/auto';
import { DEFAULT_HOME_CHART_INTERVAL, POLLING_INTERVAL } from '@shared/models/home-page.model';

@Component({
  selector: 'tb-home-charts',
  templateUrl: './home-charts.component.html',
  styleUrls: ['./home-charts.component.scss']
})
export class HomeChartsComponent implements OnInit, OnDestroy, AfterViewInit {

  chartIdSuf = 'home';

  charts = {};
  latestValues = {};
  statsCharts = Object.values(StatsChartType);
  statChartTypeTranslationMap = StatsChartTypeTranslationMap;

  private stopPolling$ = new Subject();
  private destroy$ = new Subject();
  private fixedWindowTimeMs: FixedWindow;

  constructor(private translate: TranslateService,
              private timeService: TimeService,
              private statsService: StatsService) {
  }

  ngOnInit() {
    this.calculateFixedWindowTimeMs();
  }

  private calculateFixedWindowTimeMs() {
    this.fixedWindowTimeMs = calculateFixedWindowTimeMs(this.timeService.defaultTimewindow());
    this.fixedWindowTimeMs.startTimeMs = this.fixedWindowTimeMs.endTimeMs - DEFAULT_HOME_CHART_INTERVAL;
  }

  ngAfterViewInit(): void {
    this.fetchEntityTimeseries();
  }

  ngOnDestroy() {
    this.stopPolling$.next();
    this.destroy$.next();
    this.destroy$.complete();
  }

  private fetchEntityTimeseries() {
    this.statsService.getEntityTimeseries('artem', this.fixedWindowTimeMs.startTimeMs, this.fixedWindowTimeMs.endTimeMs)
      .pipe(takeUntil(this.stopPolling$))
      .subscribe(data => {
        this.initCharts(data);
        this.startPolling();
      });
  }

  private initCharts(data) {
    for (const chartType in StatsChartType) {
      this.charts[chartType] = {} as Chart;
      const ctx = document.getElementById(chartType + this.chartIdSuf) as HTMLCanvasElement;
      const dataSet = {
        data: data[chartType],
        borderColor: getColor(chartType, 0),
        borderWidth: 3
      };
      const params = {...homeChartJsParams(), ...{data: {datasets: [dataSet]}}};
      this.charts[chartType] = new Chart(ctx, params);
      this.latestValues[chartType] = chartType;
      this.updateXScale(chartType);
    }
  }

  private startPolling() {
    timer(0, POLLING_INTERVAL)
      .pipe(
        switchMap(() => this.statsService.getLatestTimeseries('artem')),
        retry(),
        takeUntil(this.stopPolling$),
        shareReplay())
      .subscribe(data => {
        this.addPollingIntervalToTimewindow();
        for (const chartType in StatsChartType) {
          this.pushLatestValue(chartType, data);
          this.updateChartView(data, chartType);
        }
      });
  }

  private addPollingIntervalToTimewindow() {
    this.fixedWindowTimeMs.startTimeMs += POLLING_INTERVAL;
    this.fixedWindowTimeMs.endTimeMs += POLLING_INTERVAL;
  }

  private pushLatestValue(chartType: string, latestData: TimeseriesData) {
    if (latestData[chartType].length) {
      const latestValue = latestData[chartType][0];
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

  private updateXScale(chartType: string) {
    this.charts[chartType].options.scales.x.min = this.fixedWindowTimeMs.startTimeMs;
    this.charts[chartType].options.scales.x.max = this.fixedWindowTimeMs.endTimeMs;
  }

  private updateLabel(data: TimeseriesData, chartType: string) {
    this.latestValues[chartType] = data[chartType][0].value;
  }

  private updateChart(chartType) {
    this.charts[chartType].update();
  }

  private addStartChartValue(chartType: string, latestValue: TsValue) {
    const data = this.charts[chartType].data.datasets[0].data;
    if (!data.length) {
      data.push({ value: latestValue.value, ts: this.fixedWindowTimeMs.startTimeMs });
    }
  }
}
