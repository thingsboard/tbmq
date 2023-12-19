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

import {
  CellActionDescriptor,
  EntityTableColumn,
  EntityTableConfig
} from '@home/models/entity/entities-table-config.models';
import { TimePageLink } from '@shared/models/page/page-link';
import { EntityType, entityTypeResources, entityTypeTranslations } from '@shared/models/entity-type.models';
import { TranslateService } from '@ngx-translate/core';
import { WsClientService } from '@core/http/ws-client.service';
import { SubscriptionTopicFilter } from '@shared/models/ws-client.model';
import { isDefinedAndNotNull } from '@core/utils';
import { MatDialog } from '@angular/material/dialog';
import { of } from 'rxjs';
import {
  AddWsClientSubscriptionDialogData,
  WsClientSubscriptionDialogComponent
} from "@home/pages/ws-client/ws-client-subscription-dialog.component";

export class WsSubscriptionsTableConfig extends EntityTableConfig<SubscriptionTopicFilter, TimePageLink> {

  constructor(private wsClientService: WsClientService,
              private translate: TranslateService,
              private dialog: MatDialog,
              public entityId: string = null) {
    super();
    this.entityType = EntityType.WS_SUBSCRIPTION;
    this.entityTranslations = entityTypeTranslations.get(EntityType.WS_SUBSCRIPTION);
    this.entityResources = entityTypeResources.get(EntityType.WS_SUBSCRIPTION);
    this.tableTitle = this.translate.instant('ws-client.subscriptions.subscriptions');
    this.entityComponent = null;
    this.detailsPanelEnabled = false;
    this.selectionEnabled = false;
    this.addEnabled = true;
    this.showColorBadge = true;
    this.entitiesDeleteEnabled = false;
    this.displayPagination = false;
    this.columns.push(new EntityTableColumn<SubscriptionTopicFilter>('topic', 'mqtt-client-session.topic'));
    this.entitiesFetchFunction = pageLink => this.fetchSubscriptions();
    this.handleRowClick = (event, entity) => false;

    this.cellHiddenActionDescriptors = this.configureCellHiddenActions();
    this.addEntity = () => {
      this.addSubscription(null);
      return of(null);
    };
  }

  fetchSubscriptions() {
    return this.wsClientService.getSubscriptions(this.entityId);
  }

  addSubscription($event: Event) {
    if ($event) {
      $event.stopPropagation();
    }
    const data = {
      subscription: null
    };
    this.dialog.open<WsClientSubscriptionDialogComponent, AddWsClientSubscriptionDialogData>(WsClientSubscriptionDialogComponent, {
      disableClose: true,
      panelClass: ['tb-dialog', 'tb-fullscreen-dialog'],
      data
    }).afterClosed()
      .subscribe((res) => {
        if (isDefinedAndNotNull(res)) {
          this.wsClientService.saveConnection(res).subscribe(
            () => {
              this.updateData()
            }
          );
        }
      });
  }

  configureCellHiddenActions(): Array<CellActionDescriptor<SubscriptionTopicFilter>> {
    const actions: Array<CellActionDescriptor<SubscriptionTopicFilter>> = [];
    actions.push(
      {
        name: this.translate.instant('mqtt-client-session.remove-session'),
        icon: 'edit',
        isEnabled: () => true,
        onAction: ($event, entity) => this.edit($event, entity)
      },
      {
        name: this.translate.instant('mqtt-client-session.remove-session'),
        icon: 'delete',
        isEnabled: () => true,
        onAction: ($event, entity) => this.remove($event, entity)
      }
    );
    return actions;
  }

  disconnect($event, entity) {
    if ($event) {
      $event.stopPropagation();
    }
  }

  remove($event, entity) {
    if ($event) {
      $event.stopPropagation();
    }

  }

  edit($event, entity) {
    if ($event) {
      $event.stopPropagation();
    }

  }
}
