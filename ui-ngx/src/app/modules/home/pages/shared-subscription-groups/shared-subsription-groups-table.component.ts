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

import { Component, Input, OnInit, ViewChild } from '@angular/core';
import { EntitiesTableComponent } from '@home/components/entity/entities-table.component';
import { TranslateService } from '@ngx-translate/core';
import { SharedSubscriptionService } from '@core/http/shared-subscription.service';
import {
  SharedSubscriptionGroupsTableConfig
} from "@home/pages/shared-subscription-groups/shared-subscription-groups-table-config";
import { ClientSessionService } from "@core/http/client-session.service";
import { MatDialog } from "@angular/material/dialog";
import { NgClass } from '@angular/common';
import { ExtendedModule } from '@angular/flex-layout/extended';

@Component({
    selector: 'tb-shared-subsriptions-groups-table',
    templateUrl: './shared-subsription-groups-table.component.html',
    standalone: true,
    imports: [EntitiesTableComponent, NgClass, ExtendedModule]
})
export class SharedSubsriptionGroupsTableComponent implements OnInit {

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

  sharedSubscriptionsTableConfig: SharedSubscriptionGroupsTableConfig;

  constructor(private sharedSubscriptionService: SharedSubscriptionService,
              private translate: TranslateService,
              private dialog: MatDialog,
              private clientSessionService: ClientSessionService) {
  }

  ngOnInit(): void {
    this.dirtyValue = !this.activeValue;
    this.sharedSubscriptionsTableConfig = new SharedSubscriptionGroupsTableConfig(
      this.sharedSubscriptionService,
      this.translate,
      this.dialog,
      this.clientSessionService,
      this.entityIdValue
    );
  }
}
