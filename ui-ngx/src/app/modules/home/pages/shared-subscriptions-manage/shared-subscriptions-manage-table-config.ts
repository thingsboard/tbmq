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
  ChipsTableColumn,
  EntityTableColumn,
  EntityTableConfig
} from '@home/models/entity/entities-table-config.models';
import { TranslateService } from '@ngx-translate/core';
import { EntityType, entityTypeResources, entityTypeTranslations } from '@shared/models/entity-type.models';
import { SharedSubscriptionManage } from "@shared/models/shared-subscription.model";
import { SharedSubscriptionService } from "@core/http/shared-subscription.service";
import { connectionStateColor } from "@shared/models/session.model";
import {
  SessionsDetailsDialogComponent,
  SessionsDetailsDialogData
} from "@home/pages/sessions/sessions-details-dialog.component";
import { ClientSessionService } from "@core/http/client-session.service";
import { MatDialog } from "@angular/material/dialog";
import { clientTypeColor, clientTypeIcon, clientTypeTranslationMap } from "@shared/models/client.model";

export class SharedSubscriptionsManageTableConfig extends EntityTableConfig<SharedSubscriptionManage> {

  constructor(private sharedSubscriptionService: SharedSubscriptionService,
              private translate: TranslateService,
              private dialog: MatDialog,
              private clientSessionService: ClientSessionService,
              public entityId: string = null) {
    super();

    this.entityType = EntityType.SHARED_SUBSCRIPTION;
    this.entityComponent = null;
    this.detailsPanelEnabled = false;
    this.entityTranslations = entityTypeTranslations.get(EntityType.SHARED_SUBSCRIPTION);
    this.entityResources = entityTypeResources.get(EntityType.SHARED_SUBSCRIPTION);
    this.tableTitle = this.translate.instant('shared-subscription.manage');
    this.entitiesDeleteEnabled = false;
    this.addEnabled = false;
    this.deleteEnabled = () => false;

    this.columns.push(
      new EntityTableColumn<SharedSubscriptionManage>('shareName', 'shared-subscription.share-name', '25%'),
      new EntityTableColumn<SharedSubscriptionManage>('topicFilter', 'shared-subscription.topic-filter', '25%'),
      new ChipsTableColumn<SharedSubscriptionManage>('clientIds', 'shared-subscription.client-ids', '50%',
        entity => entity?.clientSessionInfo?.map(e => e.clientId)?.join(','),
        (entity, value) => this.showSessionDetails(value),
        (entity, value) => {
          if (entity) {
            const session = entity.clientSessionInfo.find(e => e.clientId === value);
            if (session) {
              const clientType = session.clientType;
              const icon = clientTypeIcon.get(clientType);
              const color = clientTypeColor.get(clientType);
              return icon;
            }
          }
          return '';
        },(entity, value) => {
          if (entity) {
            const session = entity.clientSessionInfo.find(e => e.clientId === value);
            if (session) {
              const connectionState = session.connectionState;
              return {
                fontSize: '3em',
                color: connectionStateColor.get(connectionState)
              };
            }
          }
          return {};
        },(entity, value) => {
          if (entity) {
            const session = entity.clientSessionInfo.find(e => e.clientId === value);
            if (session) {
              const clientType = session.clientType;
              return this.translate.instant(clientTypeTranslationMap.get(clientType));
            }
          }
          return '';
        }
      )
    );

    this.entitiesFetchFunction = pageLink => this.sharedSubscriptionService.getSharedSubscriptionsManage(pageLink);
  }

  private showSessionDetails(clientId: string) {
    this.clientSessionService.getDetailedClientSessionInfo(clientId).subscribe(
      session => {
        this.dialog.open<SessionsDetailsDialogComponent, SessionsDetailsDialogData>(SessionsDetailsDialogComponent, {
          disableClose: true,
          panelClass: ['tb-dialog', 'tb-fullscreen-dialog'],
          data: {
            session
          }
        }).afterClosed()
          .subscribe();
      }
    );
  }
}
