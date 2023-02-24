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
import { deepClone } from "@core/utils";

@Component({
  selector: 'tb-mail-server',
  templateUrl: './stats.component.html',
  styleUrls: ['./stats.component.scss']
})
export class StatsComponent extends PageComponent implements OnInit, OnDestroy, AfterViewInit {

  public keys = ['incomingMessages', 'outgoingMessages'];

  public data = {
    "incomingMessages": [],
    "outgoingMessages": []
  }

  public charts = {};

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
    this.initCharts();
  }

  ngOnDestroy() {
    this.destroy$.next();
    this.destroy$.complete();
    super.ngOnDestroy();
  }

  initCharts() {
    for (let i = 0; i < this.keys.length; i++) {
      const key = this.keys[i];
      let id = key + i;
      let ctx = document.getElementById(id) as HTMLCanvasElement;
      let dataSet = {
        label: key,
        fill: false,
        backgroundColor: 'rgb(252,141,98, 0.5)',
        borderColor: 'rgb(252,141,98, 0.5)',
        hoverBackgroundColor: 'rgb(252,141,98, 0.75)',
        hoverBorderColor: 'rgb(252,141,98)',
        data: this.data[key]
      };
      this.charts[key] = {};
      this.charts[key] = new Chart(ctx, {
        type: 'line',
        data: {datasets: [dataSet]},
        options: {
          title: {
            display: true,
            text: 'Title '+ id
          },
          scales: {
            xAxes: [{
              type: 'time',
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
            mode: 'point'
          }
        }
      });
    }
  }

  changeTimerange() {
    this.statsService.getEntityTimeseriesMock().subscribe(
      data => {
        this.updateCharts(data);
      }
    )
  }

  updateCharts(data) {
    for (let key of Object.keys(data)) {
      this.data = null;
      this.data = deepClone(data);
      this.charts[key].update();
    }
  }

}
