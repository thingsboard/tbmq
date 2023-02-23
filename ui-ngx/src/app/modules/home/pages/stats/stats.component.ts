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
import { MailServerService } from '@core/http/mail-server.service';
import { TranslateService } from '@ngx-translate/core';
import { Subject } from 'rxjs';
import Chart from 'chart.js';
import moment from "moment";

@Component({
  selector: 'tb-mail-server',
  templateUrl: './stats.component.html',
  styleUrls: ['./stats.component.scss']
})
export class StatsComponent extends PageComponent implements OnInit, OnDestroy, AfterViewInit {

  private destroy$ = new Subject();

  constructor(protected store: Store<AppState>,
              private router: Router,
              private adminService: MailServerService,
              private translate: TranslateService,
              public fb: FormBuilder) {
    super(store);
  }

  ngAfterViewInit(): void {
    var ctx = document.getElementById('myChart') as HTMLCanvasElement;

    var s1 = {
      label: 's1',
      fill: false,
      backgroundColor: 'rgb(252,141,98, 0.5)',
      borderColor: 'rgb(252,141,98, 0.5)',
      hoverBackgroundColor: 'rgb(252,141,98, 0.75)',
      hoverBorderColor: 'rgb(252,141,98)',
      data: [
        { x: 1606943100000, y: 1.5 },
        { x: 1607943200000, y: 2.7 },
        { x: 1608943300000, y: 1.3 },
        { x: 1609943400000, y: 5.2 },
        { x: 1610943500000, y: 9.4 },
        { x: 1611943600000, y: 6.5 },
      ]
    };

    var s2 = {
      label: 's2',
      fill: false,
      backgroundColor: 'rgb(141,160,203, 0.25)',
      borderColor: 'rgb(141,160,203, 0.5)',
      hoverBackgroundColor: 'rgb(141,160,203, 0.75)',
      hoverBorderColor: 'rgb(141,160,203)',
      data: [
        { x: 1604953100000, y: 4.5 },
        { x: 1607963200000, y: 2.7 },
        { x: 1608973300000, y: 3.3 },
        { x: 1613983400000, y: 4.2 },
        { x: 1620993500000, y: 7.4 },
        { x: 1632043600000, y: 6.5 },
      ]
    };

    var ctx = document.getElementById('myChart') as HTMLCanvasElement;
    var chart = new Chart(ctx, {
      type: 'line',
      data: { datasets: [s1, s2] },
      options: {
        scales: {
          xAxes: [{
            type: 'time',
            //distribution: 'series',
            time: {
              unitStepSize: 500,
              unit: 'hour',
              displayFormats: {
                hour: 'hA',
                day: 'YYYY-MM-DD',
                month: 'YYYY-MM'
              }
            }
          }]
        },
        hover: {
          mode: 'dataset'
        },
        plugins: {
          zoom: {
            // Container for pan options
            pan: {
              // Boolean to enable panning
              enabled: true,
              // Panning directions. Remove the appropriate direction to disable
              // Eg. 'y' would only allow panning in the y direction
              mode: 'x'
            },
            // Container for zoom options
            zoom: {
              // Boolean to enable zooming
              enabled: true,
              // Zooming directions. Remove the appropriate direction to disable
              // Eg. 'y' would only allow zooming in the y direction
              mode: 'x',
            }
          }
        }
      }
    });
  }

  ngOnInit() {
    this.createChart();
  }

  ngOnDestroy() {
    this.destroy$.next();
    this.destroy$.complete();
    super.ngOnDestroy();
  }

  private createChart() {
  }
}
