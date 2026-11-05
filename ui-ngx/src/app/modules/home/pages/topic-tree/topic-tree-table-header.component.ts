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
import { EntityTableHeaderComponent } from '@home/components/entity/entity-table-header.component';
import { TopicTreeFilter, TopicTreeRow } from '@shared/models/topic-tree.models';
import { TopicTreeTableConfig } from './topic-tree-table-config';
import { TopicTreeFilterComponent } from './topic-tree-filter.component';

@Component({
  selector: 'tb-topic-tree-table-header',
  template: '<tb-topic-tree-filter [filter]="topicTreeTableConfig.query" (filterChange)="filterChanged($event)"></tb-topic-tree-filter>',
  imports: [TopicTreeFilterComponent]
})
export class TopicTreeTableHeaderComponent extends EntityTableHeaderComponent<TopicTreeRow> {

  get topicTreeTableConfig(): TopicTreeTableConfig {
    return this.entitiesTableConfig as TopicTreeTableConfig;
  }

  constructor(protected store: Store<AppState>) {
    super(store);
  }

  // Not resetSortAndFilter(): that drops the search text, which combines with the filter, and reads the default
  // sort column, which the column picker may have hidden.
  filterChanged(filter: TopicTreeFilter): void {
    this.topicTreeTableConfig.query = filter;
    const table = this.topicTreeTableConfig.getTable();
    if (table.displayPagination) {
      table.paginator.pageIndex = 0;
    }
    table.updateData();
  }
}
