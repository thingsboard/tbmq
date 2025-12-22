///
/// Copyright © 2016-2025 The Thingsboard Authors
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

import { AfterViewInit, Component, OnDestroy, input } from '@angular/core';
import { forkJoin, Observable, retry, Subject } from 'rxjs';
import { shareReplay, switchMap, take, takeUntil } from 'rxjs/operators';
import { ClientCredentialsInfo } from '@shared/models/credentials.model';
import { ClientCredentialsService } from '@core/http/client-credentials.service';
import { ClientSessionService } from '@core/http/client-session.service';
import { ClientSessionStatsInfo } from '@shared/models/session.model';
import { CredentialsHomeCardConfig, HomePageTitleType, SessionsHomeCardConfig } from '@shared/models/home-page.model';
import { HomeCardsTableComponent } from './home-cards-table.component';
import { TranslateModule } from '@ngx-translate/core';
import { TimeService } from '@core/services/time.service';

@Component({
    selector: 'tb-home-cards-sessions-credentials',
    templateUrl: './home-cards-sessions-credentials.component.html',
    imports: [HomeCardsTableComponent, TranslateModule]
})
export class HomeCardsSessionsCredentialsComponent implements AfterViewInit, OnDestroy {

  readonly isLoading$ = input<Observable<boolean>>();

  cardTypeCredentials = HomePageTitleType.CLIENT_CREDENTIALS;
  cardTypeSession = HomePageTitleType.SESSION;
  sessionsLatest: ClientSessionStatsInfo;
  credentialsLatest: ClientCredentialsInfo;
  sessionConfig = SessionsHomeCardConfig;
  credentialsConfig = CredentialsHomeCardConfig;

  private stopPolling$ = new Subject<void>();
  private $tasks = forkJoin([
    this.clientSessionService.getClientSessionsStats(),
    this.clientCredentialsService.getClientCredentialsStatsInfo()
  ]);

  constructor(
    private clientCredentialsService: ClientCredentialsService,
    private clientSessionService: ClientSessionService,
    private timeService: TimeService,
  ) {
    this.$tasks.pipe(take(1)).subscribe(data => this.update(data));
  }

  ngAfterViewInit(): void {
    this.startPolling();
  }

  ngOnDestroy(): void {
    this.stopPolling$.next();
  }

  private startPolling() {
    this.timeService.getSyncTimer()
      .pipe(
        switchMap(() => this.$tasks),
        retry(),
        takeUntil(this.stopPolling$),
        shareReplay()
      )
      .subscribe(data => this.update(data));
  }

  private update(data: [ClientSessionStatsInfo, ClientCredentialsInfo]) {
    [this.sessionsLatest, this.credentialsLatest] = [...data];
  }
}
