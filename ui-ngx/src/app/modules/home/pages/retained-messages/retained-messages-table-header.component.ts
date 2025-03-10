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

import { Component } from '@angular/core';
import { Store } from '@ngrx/store';
import { AppState } from '@core/core.state';
import { EntityTableHeaderComponent } from '../../components/entity/entity-table-header.component';
import { RetainedMessagesTableConfig } from '@home/pages/retained-messages/retained-messages-table-config';
import { RetainedMessage, RetainedMessagesFilterConfig } from '@shared/models/retained-message.model';
import { RetainedMessagesFilterConfigComponent } from './retained-messages-filter-config.component';
import { FormsModule } from '@angular/forms';

@Component({
    selector: 'tb-retained-messages-table-header',
    templateUrl: './retained-messages-table-header.component.html',
    styleUrls: ['./retained-messages-table-header.component.scss'],
    imports: [RetainedMessagesFilterConfigComponent, FormsModule]
})
export class RetainedMessagesTableHeaderComponent extends EntityTableHeaderComponent<RetainedMessage> {

  get retainedMessagesTableConfig(): RetainedMessagesTableConfig {
    return this.entitiesTableConfig as RetainedMessagesTableConfig;
  }

  constructor(protected store: Store<AppState>) {
    super(store);
  }

  filterChanged(retainedMessagesFilterConfig: RetainedMessagesFilterConfig) {
    this.retainedMessagesTableConfig.retainedMessagesFilterConfig = retainedMessagesFilterConfig;
    this.retainedMessagesTableConfig.getTable().resetSortAndFilter(true, true);
  }
}
