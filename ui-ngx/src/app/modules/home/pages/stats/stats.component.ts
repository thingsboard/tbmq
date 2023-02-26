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
import Chart from 'chart.js';
import { StatsService } from "@core/http/stats.service";
import { StatChartType, StatChartTypeTranslationMap } from "@shared/models/stats.model";

@Component({
  selector: 'tb-mail-server',
  templateUrl: './stats.component.html',
  styleUrls: ['./stats.component.scss']
})
export class StatsComponent extends PageComponent implements OnInit, OnDestroy, AfterViewInit {

  public keys = ['INCOMING_MESSAGES',
    'OUTGOING_MESSAGES',
    'DROPPED_GMESSAGES',
    'SESSIONS',
    'SUBSCRIPTIONS',
    'TOPICS'];
  public charts = {};

  private statChartTypeTranslationMap = StatChartTypeTranslationMap;

  private destroy$ = new Subject();

  constructor(protected store: Store<AppState>,
              private router: Router,
              private statsService: StatsService,
              private translate: TranslateService,
              public fb: FormBuilder) {
    super(store);
  }

  ngOnInit() {
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
    for (let chart in StatChartType) {
      this.charts[chart] = {} as Chart;
      let id = chart;
      let ctx = document.getElementById(id) as HTMLCanvasElement;
      //@ts-ignore
      let label = this.translate.instant(this.statChartTypeTranslationMap.get(chart));
      let dataSet = {
        label: label,
        backgroundColor: 'rgb(1,141,98, 0.5)',
        borderColor: 'rgb(1,141,98, 0.5)',
        hoverBackgroundColor: 'rgb(1,141,98, 0.75)',
        hoverBorderColor: 'rgb(1,141,98)',
        borderWidth: 1,
        data: data[chart].map(el => { return { x: el['ts'], y: el['value'] }})
      };
      this.charts[chart] = new Chart(ctx, {
        type: 'bar',
        data: {datasets: [dataSet]},
        options: {
          legend: {
            display: false
          },
          title: {
            display: true,
            text: label
          },
          scales: {
            yAxes: [{
              display: true
            }],
            xAxes: [{
              type: 'time',
              time: {
                unitStepSize: 1,
                /*unit: 'minute',
                displayFormats: {
                  hour: 'hA',
                  day: 'YYYY-MM-DD',
                  month: 'YYYY-MM',
                  minute: 'MMM DD',
                }*/
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

  changeTimerange() {
    this.statsService.getEntityTimeseriesMock().subscribe(data => {
      for (let chart in StatChartType) {
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
}
