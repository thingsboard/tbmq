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

import { Component } from '@angular/core';
import { Store } from '@ngrx/store';
import { AppState } from '@core/core.state';
import { EntityTableHeaderComponent } from '../../components/entity/entity-table-header.component';
import { SharedSubscriptionFilterConfig, SharedSubscriptionGroup } from '@shared/models/shared-subscription.model';
import { SharedSubscriptionGroupsTableConfig } from '@home/pages/shared-subscription-groups/shared-subscription-groups-table-config';

@Component({
  selector: 'tb-shared-subscription-groups-table-header',
  templateUrl: './shared-subscription-groups-table-header.component.html',
  styleUrls: ['./shared-subscription-groups-table-header.component.scss']
})
export class SharedSubscriptionGroupsTableHeaderComponent extends EntityTableHeaderComponent<SharedSubscriptionGroup> {

  get sharedSubscriptionsTableConfig(): SharedSubscriptionGroupsTableConfig {
    return this.entitiesTableConfig as SharedSubscriptionGroupsTableConfig;
  }

  constructor(protected store: Store<AppState>) {
    super(store);
  }

  alarmFilterChanged(sharedSubscriptionFilterConfig: SharedSubscriptionFilterConfig) {
    this.sharedSubscriptionsTableConfig.sharedSubscriptionFilterConfig = sharedSubscriptionFilterConfig;
    this.sharedSubscriptionsTableConfig.getTable().resetSortAndFilter(true, true);
  }
}
