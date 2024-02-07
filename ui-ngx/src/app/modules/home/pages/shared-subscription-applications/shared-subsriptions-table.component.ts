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

import { Component, Input, OnInit, ViewChild } from '@angular/core';
import { EntitiesTableComponent } from '@home/components/entity/entities-table.component';
import { TranslateService } from '@ngx-translate/core';
import { DatePipe } from '@angular/common';
import { SharedSubscriptionsTableConfig } from '@home/pages/shared-subscription-applications/shared-subscriptions-table-config';
import { SharedSubscriptionService } from '@core/http/shared-subscription.service';

@Component({
  selector: 'tb-shared-subsriptions-table',
  templateUrl: './shared-subsriptions-table.component.html'
})
export class SharedSubsriptionsTableComponent implements OnInit {

  @Input()
  detailsMode: boolean;

  activeValue = false;
  dirtyValue = false;
  entityIdValue: string;

  @Input()
  set active(active: boolean) {
    if (this.activeValue !== active) {
      this.activeValue = active;
      if (this.activeValue && this.dirtyValue) {
        this.dirtyValue = false;
        this.entitiesTable.updateData();
      }
    }
  }

  @Input()
  set entityId(entityId: string) {
    this.entityIdValue = entityId;
    if (this.sharedSubscriptionsTableConfig && this.sharedSubscriptionsTableConfig.entityId !== entityId) {
      this.sharedSubscriptionsTableConfig.entityId = entityId;
      this.entitiesTable.resetSortAndFilter(this.activeValue);
      if (!this.activeValue) {
        this.dirtyValue = true;
      }
    }
  }

  @ViewChild(EntitiesTableComponent, {static: true}) entitiesTable: EntitiesTableComponent;

  sharedSubscriptionsTableConfig: SharedSubscriptionsTableConfig;

  constructor(private sharedSubscriptionService: SharedSubscriptionService,
              private translate: TranslateService,
              private datePipe: DatePipe) {
  }

  ngOnInit(): void {
    this.dirtyValue = !this.activeValue;
    this.sharedSubscriptionsTableConfig = new SharedSubscriptionsTableConfig(
      this.sharedSubscriptionService,
      this.translate,
      this.datePipe,
      this.entityIdValue
    );
  }

}
