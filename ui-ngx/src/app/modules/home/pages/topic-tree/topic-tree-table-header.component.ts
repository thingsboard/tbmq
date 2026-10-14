///
/// ThingsBoard, Inc. ("COMPANY") CONFIDENTIAL
///
/// Copyright © 2016-2026 ThingsBoard, Inc. All Rights Reserved.
///
/// NOTICE: All information contained herein is, and remains
/// the property of ThingsBoard, Inc. and its suppliers,
/// if any.  The intellectual and technical concepts contained
/// herein are proprietary to ThingsBoard, Inc.
/// and its suppliers and may be covered by U.S. and Foreign Patents,
/// patents in process, and are protected by trade secret or copyright law.
///
/// Dissemination of this information or reproduction of this material is strictly forbidden
/// unless prior written permission is obtained from COMPANY.
///
/// Access to the source code contained herein is hereby forbidden to anyone except current COMPANY employees,
/// managers or contractors who have executed Confidentiality and Non-disclosure agreements
/// explicitly covering such access.
///
/// The copyright notice above does not evidence any actual or intended publication
/// or disclosure  of  this source code, which includes
/// information that is confidential and/or proprietary, and is a trade secret, of  COMPANY.
/// ANY REPRODUCTION, MODIFICATION, DISTRIBUTION, PUBLIC  PERFORMANCE,
/// OR PUBLIC DISPLAY OF OR THROUGH USE  OF THIS  SOURCE CODE  WITHOUT
/// THE EXPRESS WRITTEN CONSENT OF COMPANY IS STRICTLY PROHIBITED,
/// AND IN VIOLATION OF APPLICABLE LAWS AND INTERNATIONAL TREATIES.
/// THE RECEIPT OR POSSESSION OF THIS SOURCE CODE AND/OR RELATED INFORMATION
/// DOES NOT CONVEY OR IMPLY ANY RIGHTS TO REPRODUCE, DISCLOSE OR DISTRIBUTE ITS CONTENTS,
/// OR TO MANUFACTURE, USE, OR SELL ANYTHING THAT IT  MAY DESCRIBE, IN WHOLE OR IN PART.
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
