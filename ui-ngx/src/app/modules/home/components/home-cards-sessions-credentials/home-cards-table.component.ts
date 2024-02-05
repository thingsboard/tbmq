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

import { Component, Input } from '@angular/core';
import { Observable } from 'rxjs';
import { ClientCredentialsInfo } from '@shared/models/credentials.model';
import { ClientSessionStatsInfo } from '@shared/models/session.model';
import { HomeCardFilter, HomePageTitleType } from '@shared/models/home-page.model';
import { Router } from '@angular/router';

@Component({
  selector: 'tb-home-cards-table',
  templateUrl: './home-cards-table.component.html',
  styleUrls: ['home-cards-table.component.scss']
})
export class HomeCardsTableComponent {

  @Input()
  isLoading$: Observable<boolean>;

  @Input()
  cardType: HomePageTitleType;

  @Input()
  latestValues: ClientSessionStatsInfo | ClientCredentialsInfo;

  @Input()
  config: HomeCardFilter;

  @Input()
  docsLink: string;

  @Input()
  docsTooltip: string;

  constructor(private router: Router) {
  }

  navigateApplyFilter(item: HomeCardFilter) {
    this.router.navigate([item.path], {queryParams: item.filter});
  }
}
