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

import { AfterViewInit, Component, Input, OnDestroy } from '@angular/core';
import { forkJoin, Observable, retry, Subject, timer } from 'rxjs';
import { shareReplay, switchMap, takeUntil } from 'rxjs/operators';
import { ClientCredentialsInfo } from '@shared/models/credentials.model';
import { ClientCredentialsService } from '@core/http/client-credentials.service';
import { ClientSessionService } from '@core/http/client-session.service';
import { ClientSessionStatsInfo } from '@shared/models/session.model';
import { CredentialsHomeCardConfig, HomePageTitleType, POLLING_INTERVAL, SessionsHomeCardConfig } from '@shared/models/home-page.model';

@Component({
  selector: 'tb-home-cards-sessions-credentials',
  templateUrl: './home-cards-sessions-credentials.component.html',
  styles: [':host {display: flex}']
})
export class HomeCardsSessionsCredentialsComponent implements AfterViewInit, OnDestroy {

  @Input() isLoading$: Observable<boolean>;

  cardTypeCredentials = HomePageTitleType.CLIENT_CREDENTIALS;
  cardTypeSession = HomePageTitleType.SESSION;
  sessionsLatest: ClientSessionStatsInfo;
  credentialsLatest: ClientCredentialsInfo;
  sessionConfig = SessionsHomeCardConfig;
  credentialsConfig = CredentialsHomeCardConfig;

  private stopPolling$ = new Subject<void>();

  constructor(private clientCredentialsService: ClientCredentialsService,
              private clientSessionService: ClientSessionService) {
  }

  ngAfterViewInit(): void {
    this.startPolling();
  }

  ngOnDestroy(): void {
    this.stopPolling$.next();
  }

  startPolling() {
    timer(0, POLLING_INTERVAL)
      .pipe(
        switchMap(() =>
          forkJoin([
            this.clientSessionService.getClientSessionsStats(),
            this.clientCredentialsService.getClientCredentialsStatsInfo()
          ])),
        retry(),
        takeUntil(this.stopPolling$),
        shareReplay())
      .subscribe(data => [this.sessionsLatest, this.credentialsLatest] = [...data]);
  }
}
