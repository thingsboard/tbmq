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
import {
  ClientSubscription,
  ClientSubscriptionFilterConfig,
} from '@shared/models/subscription.model';
import { SubscriptionsTableConfig } from '@home/pages/subscriptions/subscriptions-table-config';

@Component({
  selector: 'tb-subscriptions-table-header',
  templateUrl: './subscriptions-table-header.component.html',
  styleUrls: ['./subscriptions-table-header.component.scss']
})
export class SubscriptionsTableHeaderComponent extends EntityTableHeaderComponent<ClientSubscription> {

  get subscriptionsTableConfig(): SubscriptionsTableConfig {
    return this.entitiesTableConfig as SubscriptionsTableConfig;
  }

  constructor(protected store: Store<AppState>) {
    super(store);
  }

  filterChanged(subscriptionsFilterConfig: ClientSubscriptionFilterConfig) {
    this.subscriptionsTableConfig.subscriptionsFilterConfig = subscriptionsFilterConfig;
    this.subscriptionsTableConfig.getTable().resetSortAndFilter(true, true);
  }
}
