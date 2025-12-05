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

import { AfterViewInit, Component, ElementRef, OnDestroy, OnInit, viewChild } from '@angular/core';
import { retry, Subject } from 'rxjs';
import { TranslateModule } from '@ngx-translate/core';
import { StatsService } from '@core/http/stats.service';
import { calculateFixedWindowTimeMs, FixedWindow } from '@shared/models/time/time.models';
import { TimeService } from '@core/services/time.service';
import { shareReplay, switchMap, takeUntil } from 'rxjs/operators';
import {
  CHARTS_HOME,
  chartJsParams,
  ChartPage,
  getColor,
  StatsChartType,
  TimeseriesData, TOTAL_KEY
} from '@shared/models/chart.model';
import Chart from 'chart.js/auto';
import { HOME_CHARTS_DURATION, HomePageTitleType, POLLING_INTERVAL } from '@shared/models/home-page.model';
import { ResizeObserver } from '@juggle/resize-observer';
import { CardTitleButtonComponent } from '@shared/components/button/card-title-button.component';
import { MatIconButton } from '@angular/material/button';
import { MatIcon } from '@angular/material/icon';
import { NgxHmCarouselComponent, NgxHmCarouselItemDirective } from 'ngx-hm-carousel';
import { FormsModule } from '@angular/forms';
import { MonitoringChartCanvasComponent } from '../../pages/monitoring/monitoring-chart-canvas.component';
import { MonitoringChartToolbarComponent } from '../../pages/monitoring/monitoring-chart-toolbar.component';
import 'chartjs-adapter-moment';

@Component({
    selector: 'tb-home-charts',
    templateUrl: './home-charts.component.html',
    styleUrls: ['./home-charts.component.scss'],
    imports: [CardTitleButtonComponent, MatIconButton, MatIcon, NgxHmCarouselComponent, FormsModule, NgxHmCarouselItemDirective, TranslateModule, MonitoringChartCanvasComponent, MonitoringChartToolbarComponent]
})
export class HomeChartsComponent implements OnInit, OnDestroy, AfterViewInit {

  readonly homeChartsContainer = viewChild<ElementRef>('homeChartsContainer');

  cardType = HomePageTitleType.MONITORING;
  chartPage = ChartPage.home;
  charts = {};
  chartKeys = CHARTS_HOME;
  chartWidth: number;
  chartHeight: number;
  chartsCarouselIndex = 0;
  items = 5;

  private stopPolling$ = new Subject<void>();
  private destroy$ = new Subject<void>();
  private fixedWindowTimeMs: FixedWindow;

  constructor(
    private timeService: TimeService,
    private statsService: StatsService,
  ) {
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
      const ctx = document.getElementById(chartType + this.chartPage) as HTMLCanvasElement;
      const color = getColor(chartType, 0);
      const dataSet = {
        data: data ? data[chartType] : null,
        borderColor: color,
        backgroundColor: color,
        label: TOTAL_KEY,
        pointBorderColor: color,
        pointBackgroundColor: color,
        pointHoverBackgroundColor: color,
        pointHoverBorderColor: color,
        pointRadius: 0,
        chartType
      };
      const params = {...chartJsParams(this.chartPage), ...{data: {datasets: [dataSet]}}};
      this.charts[chartType] = new Chart(ctx, params as any);
      this.updateXScale(chartType);
    }
  }

  private startPolling() {
    this.timeService.getSyncTimer()
      .pipe(
        switchMap(() => this.statsService.getLatestTimeseries(TOTAL_KEY)),
        retry(),
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
      let width = (containerWidth / 5) - 16;
      this.items = 5;
      if (containerWidth < 500 || window.innerHeight > window.innerWidth) { // mobile or portrait
        width = containerWidth / 2;
        this.items = 2;
      }
      this.chartWidth = width;
      this.chartHeight = Math.round(width * 0.5);
    });
    resizeObserver.observe(this.homeChartsContainer().nativeElement);
  }
}
