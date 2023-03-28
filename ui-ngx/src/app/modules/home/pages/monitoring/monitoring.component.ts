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

import { ChangeDetectorRef, Component, EventEmitter, Output } from '@angular/core';
import { calculateFixedWindowTimeMs, FixedWindow, Timewindow, TimewindowType } from '@shared/models/time/time.models';
import { StatsChartType, StatsChartTypeTranslationMap } from '@shared/models/stats.model';
import { Observable, Subject, timer } from 'rxjs';
import { KeyValue } from '@angular/common';
import { TranslateService } from '@ngx-translate/core';
import { TimeService } from '@core/services/time.service';
import { Router } from '@angular/router';
import { StatsService } from '@core/http/stats.service';
import { retry, switchMap, takeUntil } from 'rxjs/operators';
import Chart, { ChartConfiguration } from 'chart.js';
import { getColor, monitoringChartJsParams } from '@shared/models/chart.model';
import { PageComponent } from '@shared/components/page.component';
import { AppState } from '@core/core.state';
import { Store } from '@ngrx/store';

@Component({
  selector: 'tb-mqtt-admin-credentials',
  templateUrl: './monitoring.component.html',
  styleUrls: ['./monitoring.component.scss']
})
export class MonitoringComponent extends PageComponent {

  @Output() timewindowObject = new EventEmitter<Timewindow>();

  charts = {};
  chartsLatestValues = {};
  timewindow: Timewindow;
  statsCharts = Object.values(StatsChartType);
  pollChartsData$: Observable<Array<KeyValue<string, any>>>;
  statChartTypeTranslationMap = StatsChartTypeTranslationMap;
  fixedWindowTimeMs: FixedWindow;

  private stopPolling$ = new Subject();

  private destroy$ = new Subject();

  constructor(protected store: Store<AppState>,
              private translate: TranslateService,
              private timeService: TimeService,
              private router: Router,
              private statsService: StatsService,
              private cd: ChangeDetectorRef) {
    super(store);
    this.setTitles();
  }

  ngOnInit() {
    this.timewindow = this.timeService.defaultTimewindow();
    this.fixedWindowTimeMs = calculateFixedWindowTimeMs(this.timewindow);
    this.pollChartsData$ = timer(0, 5000).pipe(
      switchMap(() => this.statsService.pollMonitoringGraphMock()),
      retry(),
      takeUntil(this.stopPolling$)
    );
  }

  ngAfterViewInit(): void {
    this.statsService.getMonitoringGraphMock().pipe(
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
      const ctx = document.getElementById(chart + '1') as HTMLCanvasElement;
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
      const params = {...monitoringChartJsParams(index, label, this.fixedWindowTimeMs.endTimeMs - this.fixedWindowTimeMs.startTimeMs), ...{ data: {datasets: [dataSet]} }};
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
    this.statsService.getMonitoringGraphMock(fixedWindowTimeMs.startTimeMs, fixedWindowTimeMs.endTimeMs).pipe(
      takeUntil(this.stopPolling$)
    ).subscribe(data => {
      for (const chart in StatsChartType) {
        this.charts[chart].data.datasets[0].data = data[chart].map(el => {
          return {
            x: el.ts,
            y: el.value
          };
        });
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
