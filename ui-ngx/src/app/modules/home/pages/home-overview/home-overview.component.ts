///
/// Copyright © 2016-2026 The Thingsboard Authors
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

import { Component } from '@angular/core';
import { ChartsComponent } from '@home/components/home/charts.component';
import { VersionCardComponent } from '@home/components/home/version-card.component';
import { PageComponent } from '@shared/components/page.component';
import { AppState } from '@core/core.state';
import { Store } from '@ngrx/store';
import { AdvancedSettingsComponent } from '@home/components/home/advanced-settings.component';
import { WelcomeComponent } from '@home/components/home/welcome.component';
import { CredentialsHomeCardConfig, HomePageTitleType, SessionsHomeCardConfig } from '@shared/models/home-page.model';
import { NetworkSettingsComponent } from '@home/components/home/network-settings.component';
import { ClientCredentialsService } from '@core/http/client-credentials.service';
import { ClientSessionService } from '@core/http/client-session.service';
import { SummaryPanelComponent } from '@home/components/home/summary-panel.component';

@Component({
    selector: 'tb-home-overview',
    templateUrl: './home-overview.component.html',
    styleUrls: ['./home-overview.component.scss'],
    imports: [ChartsComponent, VersionCardComponent, AdvancedSettingsComponent, WelcomeComponent, SummaryPanelComponent, NetworkSettingsComponent, SummaryPanelComponent]
})
export class HomeOverviewComponent extends PageComponent {

  cardTypeCredentials = HomePageTitleType.CLIENT_CREDENTIALS;
  cardTypeSession = HomePageTitleType.SESSION;
  sessions$ = this.clientSessionService.getClientSessionsStats();
  credentials$ = this.clientCredentialsService.getClientCredentialsStatsInfo();
  sessionConfig = SessionsHomeCardConfig;
  credentialsConfig = CredentialsHomeCardConfig;

  constructor(
    protected store: Store<AppState>,
    private clientSessionService: ClientSessionService,
    private clientCredentialsService: ClientCredentialsService,
  ) {
    super(store);
  }
}
