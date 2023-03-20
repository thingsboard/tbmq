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

import { Component, EventEmitter, Input, OnInit, Output } from '@angular/core';
import { Observable, of } from 'rxjs';
import { ConfigParams, ConfigParamsTranslationMap } from "@shared/models/stats.model";
import { ActionNotificationShow } from "@core/notification/notification.actions";
import { Store } from "@ngrx/store";
import { AppState } from "@core/core.state";
import { TranslateService } from "@ngx-translate/core";
import { Router } from '@angular/router';
import { map, mergeMap, take } from 'rxjs/operators';
import { ConfigService } from '@core/http/config.service';

@Component({
  selector: 'tb-card-config',
  templateUrl: './card-config.component.html',
  styleUrls: ['./card-config.component.scss']
})
export class CardConfigComponent implements OnInit {

  configParamsTranslationMap = ConfigParamsTranslationMap;
  configParams = ConfigParams;

  overviewConfig: any;

  @Input() isLoading$: Observable<boolean>;

  constructor(protected store: Store<AppState>,
              private router: Router,
              private translate: TranslateService,
              private configService: ConfigService) { }

  ngOnInit(): void {
    this.overviewConfig = this.configService.getConfig();
  }

  onCopy() {
    const message = this.translate.instant('config.port-copied');
    this.store.dispatch(new ActionNotificationShow(
      {
        message,
        type: 'success',
        duration: 1500,
        verticalPosition: 'top',
        horizontalPosition: 'left'
      }));
  }

  viewDocumentation(page: string) {
    window.open(`https://thingsboard.io/docs/mqtt-broker/${page}`, '_blank');
  }

  navigateToPage(page: string) {
    this.router.navigateByUrl(`/${page}`);
  }
}
