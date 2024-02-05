///
/// Copyright © 2016-2024 The Thingsboard Authors
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
import { Store } from '@ngrx/store';
import { AppState } from '@core/core.state';
import { EntityTableHeaderComponent } from '../../components/entity/entity-table-header.component';
import { ClientCredentialsFilterConfig, ClientCredentials } from '@shared/models/credentials.model';
import { ClientCredentialsTableConfig } from '@home/pages/client-credentials/client-credentials-table-config';

@Component({
  selector: 'tb-client-credentials-table-header',
  templateUrl: './client-credentials-table-header.component.html',
  styleUrls: ['./client-credentials-table-header.component.scss']
})
export class ClientCredentialsTableHeaderComponent extends EntityTableHeaderComponent<ClientCredentials> {

  get clientCredentialsTableConfig(): ClientCredentialsTableConfig {
    return this.entitiesTableConfig as ClientCredentialsTableConfig;
  }

  constructor(protected store: Store<AppState>) {
    super(store);
  }

  alarmFilterChanged(clientCredentialsFilterConfig: ClientCredentialsFilterConfig) {
    this.clientCredentialsTableConfig.clientCredentialsFilterConfig = clientCredentialsFilterConfig;
    this.clientCredentialsTableConfig.getTable().resetSortAndFilter(true, false);
  }
}
