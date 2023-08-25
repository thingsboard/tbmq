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

import { AfterViewInit, Component, ElementRef, OnDestroy, OnInit, ViewChild } from '@angular/core';
import { Subject, timer } from 'rxjs';
import { TranslateService } from '@ngx-translate/core';
import { StatsService } from '@core/http/stats.service';
import { calculateFixedWindowTimeMs, FixedWindow } from '@shared/models/time/time.models';
import { TimeService } from '@core/services/time.service';
import { shareReplay, switchMap, takeUntil } from 'rxjs/operators';
import {
  ChartTooltipTranslationMap,
  getColor,
  homeChartJsParams,
  StatsChartType,
  StatsChartTypeTranslationMap,
  TimeseriesData, TOTAL_KEY
} from '@shared/models/chart.model';
import Chart from 'chart.js/auto';
import { HOME_CHARTS_DURATION, HomePageTitleType, POLLING_INTERVAL } from '@shared/models/home-page.model';
import { ResizeObserver } from '@juggle/resize-observer';

@Component({
  selector: 'tb-home-charts',
  templateUrl: './home-charts.component.html',
  styleUrls: ['./home-charts.component.scss']
})
export class HomeChartsComponent implements OnInit, OnDestroy, AfterViewInit {

  @ViewChild('homeChartsContainer', {static: true}) homeChartsContainer: ElementRef;

  cardType = HomePageTitleType.MONITORING;
  chartIdSuf = 'home';
  charts = {};
  statsCharts = Object.values(StatsChartType);
  statChartTypeTranslationMap = StatsChartTypeTranslationMap;
  chartWidth: string;
  chartHeight: string;

  private stopPolling$ = new Subject();
  private destroy$ = new Subject();
  private fixedWindowTimeMs: FixedWindow;

  chartTooltip = (chartType: string) => this.translate.instant(ChartTooltipTranslationMap.get(chartType));

  constructor(private translate: TranslateService,
              private timeService: TimeService,
              private statsService: StatsService) {
  }

  ngOnInit() {
    this.resizeCharts();
    this.calculateFixedWindowTimeMs();
  }

  private calculateFixedWindowTimeMs() {
    this.fixedWindowTimeMs = calculateFixedWindowTimeMs(this.timeService.defaultTimewindow());
    this.fixedWindowTimeMs.startTimeMs = this.fixedWindowTimeMs.endTimeMs - HOME_CHARTS_DURATION;
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
    this.statsService.getEntityTimeseries(TOTAL_KEY, this.fixedWindowTimeMs.startTimeMs, this.fixedWindowTimeMs.endTimeMs)
      .pipe(takeUntil(this.stopPolling$))
      .subscribe(data => {
        this.initCharts(data);
        this.startPolling();
      },
        () => {
          this.initCharts();
        }
      );
  }

  private initCharts(data = null) {
    for (const chartType in StatsChartType) {
      this.charts[chartType] = {} as Chart;
      const ctx = document.getElementById(chartType + this.chartIdSuf) as HTMLCanvasElement;
      const color = getColor(chartType, 0);
      const dataSet = {
        data: data ? data[chartType] : null,
        borderColor: color,
        backgroundColor: color,
        label: TOTAL_KEY,
        pointBorderColor: color,
        pointBackgroundColor: color,
        pointHoverBackgroundColor: color,
        pointHoverBorderColor: color
      };
      const params = {...homeChartJsParams(), ...{data: {datasets: [dataSet]}}};
      this.charts[chartType] = new Chart(ctx, params);
      this.updateXScale(chartType);
    }
  }

  private startPolling() {
    timer(0, POLLING_INTERVAL)
      .pipe(
        switchMap(() => this.statsService.getLatestTimeseries(TOTAL_KEY)),
        takeUntil(this.stopPolling$),
        shareReplay())
      .subscribe(data => {
        this.addPollingIntervalToTimewindow();
        for (const chartType in StatsChartType) {
          this.pushLatestValue(chartType, data);
          this.updateChartView(chartType);
        }
      });
  }

  private addPollingIntervalToTimewindow() {
    this.fixedWindowTimeMs.startTimeMs += POLLING_INTERVAL;
    this.fixedWindowTimeMs.endTimeMs += POLLING_INTERVAL;
  }

  private pushLatestValue(chartType: string, latestData: TimeseriesData) {
    if (latestData[chartType]?.length) {
      const latestValue = latestData[chartType][0];
      this.charts[chartType].data.datasets[0].data.unshift(latestValue);
    }
  }

  private updateChartView(chartType) {
    this.updateXScale(chartType);
    this.updateChart(chartType);
  }

  private updateXScale(chartType: string) {
    this.charts[chartType].options.scales.x.min = this.fixedWindowTimeMs.startTimeMs;
    this.charts[chartType].options.scales.x.max = this.fixedWindowTimeMs.endTimeMs;
  }

  private updateChart(chartType) {
    this.charts[chartType].update();
  }

  private resizeCharts() {
    const resizeObserver = new ResizeObserver((entries) => {
      const containerWidth = entries[0].contentRect.width;
      let chartWidthPx = (containerWidth / 5) - 16;
      if (containerWidth < 700) chartWidthPx = (containerWidth / 5) - 32; // Tablet
      if (containerWidth < 400) chartWidthPx = containerWidth / 2; // Mobile
      this.chartWidth = chartWidthPx + 'px';
      this.chartHeight = (chartWidthPx * 0.5) + 'px';
    });
    resizeObserver.observe(this.homeChartsContainer.nativeElement);
  }
}
