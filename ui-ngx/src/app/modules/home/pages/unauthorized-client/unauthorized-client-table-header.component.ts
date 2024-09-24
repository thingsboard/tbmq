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
import { UnauthorizedClient } from '@shared/models/unauthorized-client.model';
import { UnauthorizedClientTableConfig } from '@home/pages/unauthorized-client/unauthorized-client-table-config';

@Component({
  selector: 'tb-session-table-header',
  templateUrl: './unauthorized-client-table-header.component.html',
  styleUrls: ['./unauthorized-client-table-header.component.scss']
})
export class UnauthorizedClientTableHeaderComponent extends EntityTableHeaderComponent<UnauthorizedClient> {

  get unauthorizedClientTableConfig(): UnauthorizedClientTableConfig {
    return this.entitiesTableConfig as UnauthorizedClientTableConfig;
  }

  constructor(protected store: Store<AppState>) {
    super(store);
  }

  filterChanged(unauthorizedClientFilterConfig: UnauthorizedClient) {
    this.unauthorizedClientTableConfig.unauthorizedClientFilterConfig = unauthorizedClientFilterConfig;
    this.unauthorizedClientTableConfig.getTable().resetSortAndFilter(true, true);
  }
}