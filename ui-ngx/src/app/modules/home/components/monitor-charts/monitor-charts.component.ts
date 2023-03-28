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

import { AfterViewInit, ChangeDetectorRef, Component, EventEmitter, OnDestroy, OnInit, Output } from '@angular/core';
import { Observable, Subject, timer } from 'rxjs';
import { Router } from '@angular/router';
import { TranslateService } from '@ngx-translate/core';
import Chart, { ChartConfiguration } from 'chart.js';
import { StatsChartType, StatsChartTypeTranslationMap } from '@shared/models/stats.model';
import { StatsService } from '@core/http/stats.service';
import { calculateFixedWindowTimeMs, FixedWindow, Timewindow, TimewindowType } from '@shared/models/time/time.models';
import { TimeService } from '@core/services/time.service';
import { retry, switchMap, takeUntil } from 'rxjs/operators';
import { defaultChartJsParams, getColor } from '@shared/models/chart.model';
import { KeyValue } from '@angular/common';

export enum StatsType {
  DROPPED_MESSAGES = 'DROPPED_MESSAGES',
  INCOMING_MESSAGES = 'INCOMING_MESSAGES',
  OUTGOING_MESSAGES = 'OUTGOING_MESSAGES',
  RETAINED_MESSAGES = 'RETAINED_MESSAGES',
  CONNECTIONS = 'CONNECTIONS',
  SUBSCRIPTIONS = 'SUBSCRIPTIONS'
}

@Component({
  selector: 'tb-monitor-charts',
  templateUrl: './monitor-charts.component.html',
  styleUrls: ['./monitor-charts.component.scss']
})
export class MonitorChartsComponent implements OnInit, OnDestroy, AfterViewInit {

  @Output() timewindowObject = new EventEmitter<Timewindow>();

  charts = {};
  chartsLatestValues = {};
  timewindow: Timewindow;
  statsCharts = Object.values(StatsChartType);
  pollChartsData$: Observable<Array<KeyValue<string, any>>>;
  statChartTypeTranslationMap = StatsChartTypeTranslationMap;

  private stopPolling$ = new Subject();

  private destroy$ = new Subject();

  constructor(private translate: TranslateService,
              private timeService: TimeService,
              private router: Router,
              private statsService: StatsService,
              private cd: ChangeDetectorRef) {
    this.setTitles();
  }

  ngOnInit() {
    this.timewindow = this.timeService.defaultTimewindow();
    this.pollChartsData$ = timer(0, 5000).pipe(
      switchMap(() => this.statsService.pollEntityTimeseriesMock()),
      retry(),
      takeUntil(this.stopPolling$)
    );
  }

  ngAfterViewInit(): void {
    const fixedWindowTimeMs: FixedWindow = calculateFixedWindowTimeMs(this.timewindow);
    this.statsService.getEntityTimeseriesMock().pipe(
      takeUntil(this.stopPolling$)
    ).subscribe(
      data => {
        this.initCharts(data);
      }
    );
  }

  ngOnDestroy() {
    this.stopPolling$.next();
    this.destroy$.next();
    this.destroy$.complete();
  }

  viewDocumentation(type) {
    this.router.navigateByUrl('');
  }

  navigateToPage(type) {
    this.router.navigateByUrl('');
  }

  initCharts(data) {
    let index = 0;
    for (const chart in StatsChartType) {
      this.charts[chart] = {} as Chart;
      const ctx = document.getElementById(chart) as HTMLCanvasElement;
      const label = this.translate.instant(this.statChartTypeTranslationMap.get(chart as StatsChartType));
      const dataSet = {
        label,
        fill: true,
        backgroundColor: 'transparent',
        borderColor: getColor(index),
        borderWidth: 3,
        data: this.transformData(data[chart]),
        hover: true
      };
      const params = {...defaultChartJsParams(), ...{ data: {datasets: [dataSet]} }};
      this.charts[chart] = new Chart(ctx, params as ChartConfiguration);
      index++;
    }
    this.startPolling();
  }

  setTitles() {
    for (const key of Object.keys(this.charts)) {
      this.chartsLatestValues[key] = key;
    }
  }

  setLatestValues(data) {
    for (const key of Object.keys(this.charts)) {
      this.chartsLatestValues[key] = data[key].length ? data[key][0]?.value : null;
    }
    this.cd.detectChanges();
  }

  onTimewindowChange() {
    this.timewindowObject.emit(this.timewindow);
    this.getTimewindow();
  }

  private getTimewindow() {
    if (this.timewindow.selectedTab === TimewindowType.HISTORY) {
      this.stopPolling();
    }
    const fixedWindowTimeMs: FixedWindow = calculateFixedWindowTimeMs(this.timewindow);
    this.fetchData(fixedWindowTimeMs);
  }

  private fetchData(fixedWindowTimeMs: FixedWindow) {
    this.stopPolling();
    this.statsService.getEntityTimeseriesMock(fixedWindowTimeMs.startTimeMs, fixedWindowTimeMs.endTimeMs).pipe(
      takeUntil(this.stopPolling$)
    ).subscribe(data => {
      for (const chart in StatsChartType) {
        this.charts[chart].data.datasets[0].data = data[chart].map(el => {
          return {
            x: el.ts,
            y: el.value
          };
        });
        // const lastValue = data[chart][data[chart].length - 1].value;
        // this.charts[chart].options.title.text[1] = lastValue;
      }
      this.updateCharts();
      if (this.timewindow.selectedTab === TimewindowType.REALTIME) {
        this.startPolling();
      }
    });
  }

  private transformData(data: Array<any>) {
    if (data?.length) {
      return data.map(el => {
        return {x: el.ts, y: el.value};
      });
    }
  }

  private startPolling() {
    this.pollChartsData$.subscribe(data => {
      for (const chart in StatsChartType) {
        const value = data[chart].map(el => {
          return {
            x: el.ts,
            y: el.value
          };
        })[0];
        this.chartsLatestValues[chart] =
        this.charts[chart].data.datasets[0].data.shift();
        this.charts[chart].data.datasets[0].data.push(value);
        this.setLatestValues(data);
        this.updateCharts();
      }
    });
  }

  private stopPolling() {
    this.stopPolling$.next();
  }

  private updateCharts() {
    for (const chart in StatsChartType) {
      this.charts[chart].update();
    }
  }
}
