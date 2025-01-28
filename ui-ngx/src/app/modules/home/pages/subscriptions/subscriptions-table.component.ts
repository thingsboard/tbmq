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

import { Component, Input, OnInit, input, viewChild } from '@angular/core';
import { EntitiesTableComponent } from '@home/components/entity/entities-table.component';
import { TranslateService } from '@ngx-translate/core';
import { DialogService } from '@core/services/dialog.service';
import { ActivatedRoute, Router } from '@angular/router';
import { SubscriptionsTableConfig } from '@home/pages/subscriptions/subscriptions-table-config';
import { SubscriptionService } from '@core/http/subscription.service';
import { ClientSessionService } from '@core/http/client-session.service';

@Component({
    selector: 'tb-subscriptions-table',
    templateUrl: './subscriptions-table.component.html',
    imports: [EntitiesTableComponent]
})
export class SubscriptionsTableComponent implements OnInit {

  readonly detailsMode = input<boolean>();

  activeValue = false;
  dirtyValue = false;
  entityIdValue: string;
  @Input()
  set active(active: boolean) {
    if (this.activeValue !== active) {
      this.activeValue = active;
      if (this.activeValue && this.dirtyValue) {
        this.dirtyValue = false;
        this.entitiesTable().updateData();
      }
    }
  }

  @Input()
  set entityId(entityId: string) {
    this.entityIdValue = entityId;
    if (this.subscriptionsTableConfig && this.subscriptionsTableConfig.entityId !== entityId) {
      this.subscriptionsTableConfig.entityId = entityId;
      this.entitiesTable().resetSortAndFilter(this.activeValue);
      if (!this.activeValue) {
        this.dirtyValue = true;
      }
    }
  }

  readonly entitiesTable = viewChild(EntitiesTableComponent);

  subscriptionsTableConfig: SubscriptionsTableConfig;

  constructor(private dialogService: DialogService,
              private subscriptionService: SubscriptionService,
              private clientSessionService: ClientSessionService,
              private translate: TranslateService,
              private route: ActivatedRoute,
              private router: Router) {
  }

  ngOnInit(): void {
    this.dirtyValue = !this.activeValue;
    this.subscriptionsTableConfig = new SubscriptionsTableConfig(
      this.dialogService,
      this.subscriptionService,
      this.clientSessionService,
      this.translate,
      this.entityIdValue,
      this.route,
      this.router
    );
  }

}
