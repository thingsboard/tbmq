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
import { Store } from '@ngrx/store';
import { AppState } from '@core/core.state';
import { EntityTableHeaderComponent } from '../../components/entity/entity-table-header.component';
import { SessionsTableConfig } from '@home/pages/sessions/sessions-table-config';
import { DetailedClientSessionInfo, SessionFilterConfig } from '@shared/models/session.model';
import { SessionFilterConfigComponent } from './session-filter-config.component';
import { FormsModule } from '@angular/forms';

@Component({
    selector: 'tb-session-table-header',
    templateUrl: './session-table-header.component.html',
    styleUrls: ['./session-table-header.component.scss'],
    imports: [SessionFilterConfigComponent, FormsModule]
})
export class SessionTableHeaderComponent extends EntityTableHeaderComponent<DetailedClientSessionInfo> {

  get sessionTableConfig(): SessionsTableConfig {
    return this.entitiesTableConfig as SessionsTableConfig;
  }

  constructor(protected store: Store<AppState>) {
    super(store);
  }

  filterChanged(sessionFilterConfig: SessionFilterConfig) {
    this.sessionTableConfig.sessionFilterConfig = sessionFilterConfig;
    this.sessionTableConfig.getTable().resetSortAndFilter(true, true);
  }
}
