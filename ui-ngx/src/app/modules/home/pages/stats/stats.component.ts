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
import { Observable, of, Subject } from 'rxjs';
import { StatsService } from "@core/http/stats.service";
import {
  StatsChartType,
  StatsChartTypeTranslationMap,
  ThreeCardsData
} from "@shared/models/stats.model";
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

  updateAvailable$: Observable<boolean> = of(false);

  sessionCardConfig: ThreeCardsData = {
    title: 'mqtt-client-session.sessions',
    link: {
      enabled: true,
      href: 'sessions'
    },
    docs: {
      enabled: true,
      href: 'docs/sessions'
    },
    actions: {
      enabled: false,
      items: null
    },
    items: [
      {
        key: 'connected',
        label: 'mqtt-client-session.connected',
        value: 155
      },
      {
        key: 'disconnected',
        label: 'mqtt-client-session.disconnected',
        value: 100
      },
      {
        key: 'total',
        label: 'home.total',
        value: 255
      }
    ]
  }
  clientCredentialsCardConfig: ThreeCardsData = {
    title: 'mqtt-client-credentials.client-credentials-type',
    link: {
      enabled: true,
      href: 'client-credentials'
    },
    docs: {
      enabled: true,
      href: 'docs/client-credentials'
    },
    actions: {
      enabled: false,
      items: [
        {
          label: 'mqtt-client-credentials.add',
          href: '/client-credentials'
        }
      ]
    },
    items: [
      {
        key: 'connected',
        label: 'mqtt-client-credentials.type-devices',
        value: 11
      },
      {
        key: 'disconnected',
        label: 'mqtt-client-credentials.type-applications',
        value: 44
      },
      {
        key: 'total',
        label: 'home.total',
        value: 55
      }
    ]
  }

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
      let dataSet = {
        label: label,
        fill: false,
        backgroundColor: this.getColor(index),
        borderColor: this.getColor(index),
        hoverBackgroundColor: this.getColor(index),
        hoverBorderColor: this.getColor(index),
        borderWidth: 1,
        data: this.transformData(data[chart])
      };
      this.charts[chart] = new Chart(ctx, {
        type: 'line',
        data: {datasets: [dataSet]},
        options: {
          animation: {
            duration: 1000
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
            text: [label, Math.floor(Math.random()*100)],
            lineHeight: 1.5,
            padding: 15,
            fontStyle: 'normal',
            fontColor: '#2f2f2f',
            fontSize: 13,
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
                display: false,
                source: 'auto'
              },
              distribution: 'series',
              bounds: 'dataset',
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
            mode: 'dataset'
          }
        }
      });
      index++;
    }
  }

  onTimewindowChange() {
    this.updateData();
  }

  viewDocumentation(type) {
    this.router.navigateByUrl('');
  }

  navigateToPage(type) {
    this.router.navigateByUrl('');
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
