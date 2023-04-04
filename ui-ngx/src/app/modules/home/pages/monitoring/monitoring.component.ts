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
import { Timewindow, TimewindowType } from '@shared/models/time/time.models';
import { Subject, timer } from 'rxjs';
import { TranslateService } from '@ngx-translate/core';
import { TimeService } from '@core/services/time.service';
import { Router } from '@angular/router';
import { StatsService } from '@core/http/stats.service';
import { retry, switchMap, takeUntil } from 'rxjs/operators';
import {
  getColor,
  monitoringChartJsParams,
  StatsChartType,
  StatsChartTypeTranslationMap
} from '@shared/models/chart.model';
import { PageComponent } from '@shared/components/page.component';
import { AppState } from '@core/core.state';
import { Store } from '@ngrx/store';
import Chart from 'chart.js/auto';
import 'chartjs-adapter-moment';
import Zoom from 'chartjs-plugin-zoom';

Chart.register([Zoom]);

@Component({
  selector: 'tb-mqtt-admin-credentials',
  templateUrl: './monitoring.component.html',
  styleUrls: ['./monitoring.component.scss']
})
export class MonitoringComponent extends PageComponent {

  @Output() timewindowObject = new EventEmitter<Timewindow>();

  charts = {};
  mockDataTsCalculationIndex = 1;
  timewindow: Timewindow;
  statsCharts = Object.values(StatsChartType);
  statChartTypeTranslationMap = StatsChartTypeTranslationMap;

  private stopPolling$ = new Subject();
  private destroy$ = new Subject();

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
  }

  ngAfterViewInit(): void {
    this.statsService.getMonitoringInitData().pipe(
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

  onTimewindowChange() {
    this.timewindowObject.emit(this.timewindow);
    this.getTimewindow();
  }

  private initCharts(data) {
    let index = 0;
    for (const chart in StatsChartType) {
      this.charts[chart] = {} as Chart;
      const ctx = document.getElementById(chart + '1') as HTMLCanvasElement;
      const label = this.translate.instant(this.statChartTypeTranslationMap.get(chart as StatsChartType));
      const datasets = {
        data: {
          datasets: [{
            label,
            fill: false,
            backgroundColor: 'transparent',
            hoverBackgroundColor: '#999999',
            hover: true,
            pointStyle: 'line',
            borderColor: getColor(chart as StatsChartType),
            data: data[chart]
          }]
        }
      };
      const params = {...monitoringChartJsParams(label), ...datasets};
      this.charts[chart] = new Chart(ctx, params);
      ctx.addEventListener('dblclick', (evt) => this.charts[chart].resetZoom());
      index++;
    }
    this.startPolling();
  }

  private getTimewindow() {
    if (this.timewindow.selectedTab === TimewindowType.HISTORY) {
      this.stopPolling();
    }
    this.fetchData();
  }

  private fetchData() {
    this.stopPolling();
    this.statsService.getMonitoringInitData().pipe(
      takeUntil(this.stopPolling$)
    ).subscribe(data => {
      for (const chart in StatsChartType) {
        this.charts[chart].data.datasets[0].data = data[chart];
      }
      this.updateCharts();
      if (this.timewindow.selectedTab === TimewindowType.REALTIME) {
        this.startPolling();
      }
    });
  }

  private startPolling() {
    timer(0, 5000).pipe(
      switchMap(() => this.statsService.pollMonitoringLatestValue()),
      retry(),
      takeUntil(this.stopPolling$)
    ).subscribe(data => {
      for (const chart in StatsChartType) {
        this.pushShiftLatestValue(data, chart as StatsChartType);
        this.updateCharts();
        this.mockDataTsCalculationIndex++;
      }
    });
  }

  private pushShiftLatestValue(data: Array<any>, chartType: StatsChartType) {
    this.charts[chartType].data.datasets[0].data.shift();
    this.charts[chartType].data.datasets[0].data.push(this.transformLatestValue(data[chartType]));
  }

  private stopPolling() {
    this.stopPolling$.next();
  }

  private updateCharts() {
    for (const chart in StatsChartType) {
      this.charts[chart].update();
    }
  }

  private transformLatestValue(chartData) {
    return chartData.map(el => {
      return {
        ts: el.ts + (60 * 60 * 500 * this.mockDataTsCalculationIndex),
        value: el.value
      };
    })[0];
  }
}
