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

// @ts-nocheck

import { ChangeDetectorRef, Component } from '@angular/core';
import { calculateFixedWindowTimeMs, FixedWindow, Timewindow, TimewindowType } from '@shared/models/time/time.models';
import { forkJoin, Observable, Subject, timer } from 'rxjs';
import { TranslateService } from '@ngx-translate/core';
import { TimeService } from '@core/services/time.service';
import { chartKeysTotal, getTimeseriesDataLimit, StatsService } from '@core/http/stats.service';
import { share, switchMap, takeUntil } from 'rxjs/operators';
import {
  chartJsParams,
  ChartPage,
  ChartTooltipTranslationMap,
  getColor,
  ONLY_TOTAL_KEYS,
  StatsChartType,
  StatsChartTypeTranslationMap,
  TimeseriesData,
  TOTAL_KEY
} from '@shared/models/chart.model';
import { PageComponent } from '@shared/components/page.component';
import { AppState } from '@core/core.state';
import { Store } from '@ngrx/store';
import Chart from 'chart.js/auto';
import 'chartjs-adapter-moment';
import Zoom from 'chartjs-plugin-zoom';
import { POLLING_INTERVAL } from '@shared/models/home-page.model';
import { ActivatedRoute } from '@angular/router';
import { ActionNotificationShow } from '@core/notification/notification.actions';
import { UntypedFormBuilder, UntypedFormGroup, Validators } from '@angular/forms';

Chart.register([Zoom]);

@Component({
  selector: 'tb-monitoring',
  templateUrl: './ws-client.component.html',
  styleUrls: ['./ws-client.component.scss']
})
export class WsClientComponent extends PageComponent {

  clients = [];
  subscriptions = [];
  client;
  subscription;
  clientForm: UntypedFormGroup;
  subscriptionForm: UntypedFormGroup;
  selectedTabIndex: 0;

  private destroy$ = new Subject();

  constructor(protected store: Store<AppState>,
              private translate: TranslateService,
              private cd: ChangeDetectorRef,
              private fb: UntypedFormBuilder,
              private route: ActivatedRoute) {
    super(store);
    // this.clients.push(this.client);
    this.clientForm = this.fb.group({
      name: [null, [Validators.required]],
      uri: ['ws://', [Validators.required]],
      host: [window.location.hostname + ":", [Validators.required]],
      port: ['8084', [Validators.required]],
      path: ['/mqtt', [Validators.required]],
      clientId: ['tbmq_dev', [Validators.required]],
      username: ['tbmq_dev', [Validators.required]],
      password: ['tbmq_dev', [Validators.required]]
    });
    this.subscriptionForm = this.fb.group({
      qos: [1, [Validators.required]],
      topic: ['testtopic/#', [Validators.required]],
      retain: [true, [Validators.required]]
    });
  }

  connectClient() {
    this.client = this.clientForm.getRawValue();
    this.clients.push(this.clientForm.getRawValue());
  }

  saveClient() {
  }

  createSubscription() {
    this.subscription = this.subscriptionForm.getRawValue();
    this.subscriptions.push(this.subscriptionForm.getRawValue());
  }

  changeSubscription(subsciption){
    this.subscription = subsciption;
  }

  ngOnDestroy() {
    this.destroy$.next();
    this.destroy$.complete();
  }

  onTabChanged(ev) {
    const clientId = ev.tab.textLabel;
    const client = this.clients[ev.index];
    this.clientForm.patchValue(client);
    this.client = client;
    console.log('ev', ev);
  }

}
