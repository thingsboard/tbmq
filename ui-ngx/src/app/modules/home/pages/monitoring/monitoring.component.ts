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
import { Observable, Subject, timer } from 'rxjs';
import { KeyValue } from '@angular/common';
import { TranslateService } from '@ngx-translate/core';
import { TimeService } from '@core/services/time.service';
import { Router } from '@angular/router';
import { StatsService } from '@core/http/stats.service';
import { retry, switchMap, takeUntil } from 'rxjs/operators';
import Chart, { ChartConfiguration } from 'chart.js';
import {
  getColor,
  monitoringChartJsParams, MonitoringChartType,
  MonitoringChartTypeTranslationMap, StatsChartType,
  StatsChartTypeTranslationMap
} from '@shared/models/chart.model';
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

  tempIndex = 1;

  timewindow: Timewindow;

  statsCharts = Object.values(StatsChartType);
  monitoringStatsCharts = Object.values(MonitoringChartType);

  statChartTypeTranslationMap = StatsChartTypeTranslationMap;
  monitoringChartTypeTranslationMap = MonitoringChartTypeTranslationMap;

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

  viewDocumentation(type) {
    this.router.navigateByUrl('');
  }

  navigateToPage(type) {
    this.router.navigateByUrl('');
  }

  initCharts(data) {
    let index = 0;
    for (const chart in MonitoringChartType) {
      this.charts[chart] = {} as Chart;
      const ctx = document.getElementById(chart + '1') as HTMLCanvasElement;
      const label = this.translate.instant(this.monitoringChartTypeTranslationMap.get(chart as MonitoringChartType));
      const dataSetConfig = {
        fill: true,
        backgroundColor: 'transparent',
        borderWidth: 3,
        hover: true
      };
      let dataSet = [{
        ...dataSetConfig,
        ...{
          label,
          borderColor: getColor(chart as StatsChartType),
          data: this.prepareDataInChartFormat(data[chart]),
        }
      }];
      if (chart === MonitoringChartType.OUTGOING_MESSAGES) {
        dataSet = [];
        dataSet.push(
          {
            ...dataSetConfig,
            ...{
              label: this.translate.instant('overview.outgoing-messages'),
              borderColor: getColor(StatsChartType.OUTGOING_MESSAGES),
              data: this.prepareDataInChartFormat(data[StatsChartType.OUTGOING_MESSAGES])
            }
          },
          {
            ...dataSetConfig,
            ...{
              label: this.translate.instant('overview.incoming-messages'),
              borderColor: getColor(StatsChartType.INCOMING_MESSAGES),
              data: this.prepareDataInChartFormat(data[StatsChartType.INCOMING_MESSAGES])
            }
          },
          {
            ...dataSetConfig,
            ...{
              label: this.translate.instant('overview.dropped-messages'),
              borderColor: getColor(StatsChartType.DROPPED_MESSAGES),
              data: this.prepareDataInChartFormat(data[StatsChartType.DROPPED_MESSAGES])
            }
          }
        );
      }
      const params = {...monitoringChartJsParams(index, label, calculateFixedWindowTimeMs(this.timewindow)), ...{ data: {datasets: dataSet} }};
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
    this.fetchData();
  }

  private fetchData() {
    this.stopPolling();
    this.statsService.getMonitoringInitData().pipe(
      takeUntil(this.stopPolling$)
    ).subscribe(data => {
      for (const chart in MonitoringChartType) {
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

  private prepareDataInChartFormat(data: Array<any>) {
    if (data?.length) {
      return data.map(el => {
        return {x: el.ts, y: el.value};
      });
    }
  }

  private startPolling() {
    timer(0, 5000).pipe(
      switchMap(() => this.statsService.pollMonitoringLatestValue()),
      retry(),
      takeUntil(this.stopPolling$)
    ).subscribe(data => {
      for (const chart in MonitoringChartType) {
        this.pushShiftLatestValue(data, chart as MonitoringChartType);
        this.updateCharts();
        this.tempIndex++;
      }
    });
  }

  private stopPolling() {
    this.stopPolling$.next();
  }

  private updateCharts() {
    for (const chart in MonitoringChartType) {
      this.charts[chart].update();
    }
  }

  private pushShiftLatestValue(data: Array<any>, chartType: MonitoringChartType) {
    switch (chartType) {
      case MonitoringChartType.OUTGOING_MESSAGES:
        this.charts[chartType].data.datasets[0].data.shift();
        this.charts[chartType].data.datasets[0].data.push(this.transformLatestValue(data[StatsChartType.OUTGOING_MESSAGES]));
        this.charts[chartType].data.datasets[1].data.shift();
        this.charts[chartType].data.datasets[1].data.push(this.transformLatestValue(data[StatsChartType.INCOMING_MESSAGES]));
        this.charts[chartType].data.datasets[2].data.shift();
        this.charts[chartType].data.datasets[2].data.push(this.transformLatestValue(data[StatsChartType.DROPPED_MESSAGES]));
        break;
      case MonitoringChartType.SESSIONS:
      case MonitoringChartType.SUBSCRIPTIONS:
        this.charts[chartType].data.datasets[0].data.shift();
        this.charts[chartType].data.datasets[0].data.push(this.transformLatestValue(data[chartType]));
        break;
    }
  }

  private transformLatestValue(chartData) {
    return chartData.map(el => {
      return {
        x: el.ts + (60 * 60 * 1000 * this.tempIndex),
        y: el.value
      };
    })[0];
  }
}
