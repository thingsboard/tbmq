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
import { Store } from '@ngrx/store';
import { AppState } from '@core/core.state';
import { PageComponent } from '@shared/components/page.component';
import { Router } from '@angular/router';
import { FormBuilder } from '@angular/forms';
import { TranslateService } from '@ngx-translate/core';
import { Subject } from 'rxjs';
import { StatsService } from "@core/http/stats.service";
import { StatsChartType, StatsChartTypeTranslationMap } from "@shared/models/stats.model";
import {
  calculateFixedWindowTimeMs,
  FixedWindow,
  Timewindow
} from "@shared/models/time/time.models";
import { TimeService } from "@core/services/time.service";
import Chart from 'chart.js';

@Component({
  selector: 'tb-mail-server',
  templateUrl: './stats.component.html',
  styleUrls: ['./stats.component.scss']
})
export class StatsComponent extends PageComponent implements OnInit, OnDestroy, AfterViewInit {

  statsCharts = Object.values(StatsChartType);
  charts = {};
  timewindow: Timewindow;

  private statChartTypeTranslationMap = StatsChartTypeTranslationMap;
  private destroy$ = new Subject();

  constructor(protected store: Store<AppState>,
              private router: Router,
              private statsService: StatsService,
              private translate: TranslateService,
              private timeService: TimeService,
              public fb: FormBuilder) {
    super(store);
  }

  ngOnInit() {
    this.timewindow = this.timeService.defaultTimewindow();
  }

  ngAfterViewInit(): void {
    this.statsService.getEntityTimeseriesMock().subscribe(
      data => {
        this.initCharts(data);
      }
    );
  }

  ngOnDestroy() {
    this.destroy$.next();
    this.destroy$.complete();
    super.ngOnDestroy();
  }

  initCharts(data) {
    for (let chart in StatsChartType) {
      this.charts[chart] = {} as Chart;
      let ctx = document.getElementById(chart) as HTMLCanvasElement;
      let label = this.translate.instant(this.statChartTypeTranslationMap.get(<StatsChartType>chart));
      let dataSet = {
        label: label,
        fill: true,
        backgroundColor: 'rgba(0, 87, 34, 0.5)',
        borderColor: 'rgba(0, 87, 34, 0.5)',
        hoverBackgroundColor: 'rgba(0, 87, 34, 0.75)',
        hoverBorderColor: 'rgba(0, 87, 34, 1)',
        borderWidth: 0,
        data: this.transformData(data[chart])
      };
      this.charts[chart] = new Chart(ctx, {
        type: 'line',
        data: {datasets: [dataSet]},
        options: {
          animation: {
            duration: 2000
          },
          layout: {
            padding: {
              left: 10,
              right: 10,
              top: 10,
              bottom: 10
            }
          },
          legend: {
            display: false
          },
          title: {
            display: true,
            text: label
          },
          scales: {
            yAxes: [{
              display: true,
              type: 'linear',
              ticks: {
                min: 0,
                stepSize: Math.floor(dataSet.data.length)
              }
            }],
            xAxes: [{
              type: 'time',
              ticks: {
                display: true,
                source: 'auto'
              },
              distribution: 'series',
              bounds: 'data',
              time: {
                round: 'second',
                unitStepSize: 100000,
                unit: 'millisecond',
                displayFormats: {
                  millisecond: 'hh:mm'
                }
              }
            }]
          },
          hover: {
            mode: 'point'
          }
        }
      });
    }
  }

  onTimewindowChange() {
    this.updateData();
  }

  private updateData() {
    const fixedWindowTimeMs: FixedWindow = calculateFixedWindowTimeMs(this.timewindow);
    this.updateCharts(fixedWindowTimeMs)
  }

  private updateCharts(fixedWindowTimeMs: FixedWindow) {
    this.statsService.getEntityTimeseriesMock().subscribe(data => {
      for (let chart in StatsChartType) {
        this.charts[chart].data.datasets[0].data = data[chart].map(el => {
          return {
            x: el['ts'],
            y: el['value']
          }
        });
        this.charts[chart].update();
      }
    });
  }

  private transformData(data: Array<any>) {
    if (data?.length) {
      return data.map(el => { return { x: el['ts'], y: el['value'] } });
    }
  }
}
