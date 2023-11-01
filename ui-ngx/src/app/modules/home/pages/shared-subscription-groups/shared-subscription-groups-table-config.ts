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
  CellActionDescriptorType,
  ChipsTableColumn,
  EntityTableColumn,
  EntityTableConfig
} from '@home/models/entity/entities-table-config.models';
import { TranslateService } from '@ngx-translate/core';
import { EntityType, entityTypeResources, entityTypeTranslations } from '@shared/models/entity-type.models';
import {
  SharedSubscriptionFilterConfig,
  SharedSubscriptionGroup,
  SharedSubscriptionQuery
} from '@shared/models/shared-subscription.model';
import { SharedSubscriptionService } from '@core/http/shared-subscription.service';
import { ConnectionState, connectionStateColor, connectionStateTranslationMap } from '@shared/models/session.model';
import { SessionsDetailsDialogComponent, SessionsDetailsDialogData } from '@home/pages/sessions/sessions-details-dialog.component';
import { ClientSessionService } from '@core/http/client-session.service';
import { MatDialog } from '@angular/material/dialog';
import { clientTypeColor, clientTypeIcon, clientTypeTranslationMap } from '@shared/models/client.model';
import {
  SharedSubscriptionGroupsTableHeaderComponent
} from '@home/pages/shared-subscription-groups/shared-subscription-groups-table-header.component';
import { TimePageLink } from '@shared/models/page/page-link';
import { Observable } from 'rxjs';
import { PageData } from '@shared/models/page/page-data';
import { Direction } from '@shared/models/page/sort-order';

export class SharedSubscriptionGroupsTableConfig extends EntityTableConfig<SharedSubscriptionGroup, TimePageLink> {

  sharedSubscriptionFilterConfig: SharedSubscriptionFilterConfig = {};

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
    this.tableTitle = this.translate.instant('shared-subscription.groups');
    this.entitiesDeleteEnabled = false;
    this.addEnabled = false;
    this.deleteEnabled = () => false;
    this.headerComponent = SharedSubscriptionGroupsTableHeaderComponent;
    this.defaultSortOrder = { property: 'shareName', direction: Direction.ASC };

    this.columns.push(
      new EntityTableColumn<SharedSubscriptionGroup>('shareName', 'shared-subscription.share-name', '25%',
        undefined, () => ({'vertical-align': 'inherit'}),
        true, () => ({}), () => undefined, false,
        {
          name: this.translate.instant('action.copy'),
          nameFunction: (entity) => this.translate.instant('action.copy') + ' ' + entity.shareName,
          icon: 'content_copy',
          style: {
            padding: '0px',
            'font-size': '16px',
            'line-height': '16px',
            height: '16px',
            color: 'rgba(0,0,0,.87)'
          },
          isEnabled: (entity) => !!entity.shareName?.length,
          onAction: ($event, entity) => entity.shareName,
          type: CellActionDescriptorType.COPY_BUTTON
        }),
      new EntityTableColumn<SharedSubscriptionGroup>('topicFilter', 'shared-subscription.topic-filter', '25%',
        undefined, () => ({'vertical-align': 'inherit'}),
        true, () => ({}), () => undefined, false,
        {
          name: this.translate.instant('action.copy'),
          nameFunction: (entity) => this.translate.instant('action.copy') + ' ' + entity.topicFilter,
          icon: 'content_copy',
          style: {
            padding: '0px',
            'font-size': '16px',
            'line-height': '16px',
            height: '16px',
            color: 'rgba(0,0,0,.87)'
          },
          isEnabled: (entity) => !!entity.topicFilter?.length,
          onAction: ($event, entity) => entity.topicFilter,
          type: CellActionDescriptorType.COPY_BUTTON
        }),
      new ChipsTableColumn<SharedSubscriptionGroup>('clients', 'shared-subscription.client-ids', '50%',
        entity => entity?.clients?.map(e => e.clientId)?.join(','),
        (entity, value) => this.showSessionDetails(value),
        (entity, value) => {
          if (entity) {
            const session = entity.clients.find(e => e.clientId === value);
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
            const session = entity.clients.find(e => e.clientId === value);
            if (session) {
              const connectionState = session.connected ? ConnectionState.CONNECTED : ConnectionState.DISCONNECTED;
              return {
                fontSize: '32px',
                color: connectionStateColor.get(connectionState)
              };
            }
          }
          return {};
        },(entity, value) => {
          if (entity) {
            const session = entity.clients.find(e => e.clientId === value);
            if (session) {
              const clientType = session.clientType;
              const connectionState = session.connected ? ConnectionState.CONNECTED : ConnectionState.DISCONNECTED;
              return this.translate.instant(clientTypeTranslationMap.get(clientType)) + ' - ' + this.translate.instant(connectionStateTranslationMap.get(connectionState));
            }
          }
          return '';
        }
      )
    );

    this.entitiesFetchFunction = pageLink => this.fetchSharedSubscriptions(pageLink);
  }

  private fetchSharedSubscriptions(pageLink: TimePageLink): Observable<PageData<SharedSubscriptionGroup>> {
    const sessionFilter = this.resolveSessionFilter(this.sharedSubscriptionFilterConfig);
    const query = new SharedSubscriptionQuery(pageLink, sessionFilter);
    return this.sharedSubscriptionService.getSharedSubscriptionsV2(query);
  }

  private resolveSessionFilter(sharedSubscriptionFilterConfig?: SharedSubscriptionFilterConfig): SharedSubscriptionFilterConfig {
    const sharedSubscriptionFilter: SharedSubscriptionFilterConfig = {};
    if (sharedSubscriptionFilterConfig) {
      sharedSubscriptionFilter.shareNameSearch = sharedSubscriptionFilterConfig.shareNameSearch;
      sharedSubscriptionFilter.topicFilter = sharedSubscriptionFilterConfig.topicFilter;
      sharedSubscriptionFilter.clientIdSearch = sharedSubscriptionFilterConfig.clientIdSearch;
    }
    return sharedSubscriptionFilter;
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
