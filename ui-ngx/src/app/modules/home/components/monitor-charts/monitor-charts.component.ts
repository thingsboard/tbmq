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

import { AfterViewInit, Component, EventEmitter, Input, OnDestroy, OnInit, Output } from '@angular/core';
import { interval, Observable, Subject } from "rxjs";
import { Router } from "@angular/router";
import { TranslateService } from "@ngx-translate/core";
import Chart from "chart.js";
import { StatsChartType, StatsChartTypeTranslationMap } from "@shared/models/stats.model";
import { StatsService } from "@core/http/stats.service";
import { calculateFixedWindowTimeMs, FixedWindow, Timewindow, TimewindowType } from "@shared/models/time/time.models";
import { TimeService } from "@core/services/time.service";
import { retry, switchMap, take, takeUntil } from "rxjs/operators";

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

  timewindow: Timewindow;

  charts = {};
  statsCharts = Object.values(StatsChartType);
  pollChartsData$: Observable<any>;

  private statChartTypeTranslationMap = StatsChartTypeTranslationMap;
  private stopPolling = new Subject();

  private destroy$ = new Subject();

  constructor(private translate: TranslateService,
              private timeService: TimeService,
              private router: Router,
              private statsService: StatsService) {
  }

  ngOnInit() {
    this.timewindow = this.timeService.defaultTimewindow();
    this.pollChartsData$ = interval(5000).pipe(
      switchMap(() => this.statsService.pollEntityTimeseriesMock()),
      retry(),
      takeUntil(this.stopPolling)
    );
  }

  ngAfterViewInit(): void {
    const fixedWindowTimeMs: FixedWindow = calculateFixedWindowTimeMs(this.timewindow);
    this.statsService.getEntityTimeseriesMock().pipe(
      takeUntil(this.stopPolling)
    ).subscribe(
      data => {
        this.initCharts(data);
      }
    );
  }

  ngOnDestroy() {
    this.stopPolling.next();
    this.destroy$.next();
    this.destroy$.complete();
  }

  getColor(index) {
    var colors = ['#65655e', '#b0a3d4',  '#7d80da', '#c6afb1', '#79addc'];
    return colors[index];
  }

  initCharts(data) {
    let index = 0;
    for (let chart in StatsChartType) {
      this.charts[chart] = {} as Chart;
      let ctx = document.getElementById(chart) as HTMLCanvasElement;
      let label = this.translate.instant(this.statChartTypeTranslationMap.get(<StatsChartType>chart));
      let lastValue = data[chart].length ? data[chart][data[chart].length-1].value : 0;
      let dataSet = {
        label: label,
        fill: false,
        backgroundColor: this.getColor(index),
        borderColor: this.getColor(index),
        hoverBackgroundColor: this.getColor(index),
        hoverBorderColor: this.getColor(index),
        borderWidth: 3,
        data: this.transformData(data[chart])
      };
      this.charts[chart] = new Chart(ctx, {
        type: 'line',
        data: {datasets: [dataSet]},
        options: {
          elements: {
            point: {
              pointStyle: 'line'
            }
          },
          animation: {
            duration: 1000
          },
          layout: {
            padding: {
              left: 0,
              right: 0,
              top: 0,
              bottom: 0
            }
          },
          legend: {
            display: false
          },
          title: {
            display: true,
            text: [label, lastValue],
            lineHeight: 2,
            padding: 10,
            fontStyle: 'normal',
            fontColor: '#4b4b4b',
            fontSize: 12
          },
          scales: {
            yAxes: [{
              display: false,
              type: 'linear',
              gridLines: {
                display: false
              },
              ticks: {
                min: 0,
                stepSize: Math.floor(dataSet.data.length)
              }
            }],
            xAxes: [{
              type: 'time',
              gridLines: {
                display: false
              },
              ticks: {
                display: true,
                fontSize: 10,
                fontFamily: 'sans serif',
                autoSkip: true,
                autoSkipPadding: (60*60*1000),
                maxRotation: 0,
                padding: -10,
                labelOffset: 0
              },
              distribution: 'series',
              bounds: 'ticks',
              time: {
                round: 'second',
                unitStepSize: 5*60*1000,
                unit: 'millisecond',
                displayFormats: {
                  millisecond: 'mm:ss'
                }
              }
            }]
          },
          hover: {
            mode: 'dataset'
          }
        }
      });
      index++;
    }
    this.startPolling();
  }

  onTimewindowChange() {
    this.timewindowObject.emit(this.timewindow);
    this.updateData();
  }

  private updateData() {
    if (this.timewindow.selectedTab === TimewindowType.HISTORY) {
      this.stopPolling.next();
    }
    const fixedWindowTimeMs: FixedWindow = calculateFixedWindowTimeMs(this.timewindow);
    this.fetchData(fixedWindowTimeMs);
  }

  private fetchData(fixedWindowTimeMs: FixedWindow) {
    this.stopPolling.next();
    this.statsService.getEntityTimeseriesMock(fixedWindowTimeMs.startTimeMs, fixedWindowTimeMs.endTimeMs).pipe(
      takeUntil(this.stopPolling)
    ).subscribe(data => {
      for (let chart in StatsChartType) {
        this.charts[chart].data.datasets[0].data = data[chart].map(el => {
          return {
            x: el['ts'],
            y: el['value']
          }
        });
        const lastValue = data[chart][data[chart].length-1].value;
        this.charts[chart].options.title.text[1] = lastValue;
      }
      this.updateCharts();
      if (this.timewindow.selectedTab === TimewindowType.REALTIME) {
        this.startPolling()
      }
    });
  }

  private transformData(data: Array<any>) {
    if (data?.length) {
      return data.map(el => { return { x: el['ts'], y: el['value'] } });
    }
  }

  startPolling() {
    this.pollChartsData$.subscribe(data => {
      for (let chart in StatsChartType) {
        const value = data[chart].map(el => {
          return {
            x: el['ts'],
            y: el['value']
          }
        })[0];
        this.charts[chart].options.title.text[1] = value.y;
        this.charts[chart].data.datasets[0].data.shift();
        this.charts[chart].data.datasets[0].data.push(value);
        this.updateCharts();
      }
    });
  }

  updateCharts() {
    for (let chart in StatsChartType) {
      this.charts[chart].update();
    }
  }

  viewDocumentation(type) {
    this.router.navigateByUrl('');
  }

  navigateToPage(type) {
    this.router.navigateByUrl('');
  }

}
