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

import { ChangeDetectionStrategy, Component, Input, OnInit } from '@angular/core';
import { Router } from "@angular/router";
import { Observable } from "rxjs";

@Component({
  selector: 'tb-monitor-client-credentials-card',
  templateUrl: './monitor-client-credentials-card.component.html',
  styleUrls: ['./monitor-client-credentials-card.component.scss'],
  changeDetection: ChangeDetectionStrategy.OnPush
})
export class MonitorClientCredentialsCardComponent implements OnInit {

  @Input() isLoading$: Observable<boolean>;
  @Input('data') data: any;

  config = [{
    key: 'devices', label: 'mqtt-client-credentials.type-devices', value: 0
  }, {
    key: 'applications', label: 'mqtt-client-credentials.type-applications', value: 0
  }, {
    key: 'total', label: 'home.total', value: 0
  }];

  constructor(protected router: Router) {
  }

  ngOnInit(): void {
    this.config.map(el => {
      el.value = this.data ? this.data[el.key] : 0;
    });
  }

  viewDocumentation(type) {
    this.router.navigateByUrl('');
  }

  navigateToPage(type) {
    this.router.navigateByUrl('');
  }


}
