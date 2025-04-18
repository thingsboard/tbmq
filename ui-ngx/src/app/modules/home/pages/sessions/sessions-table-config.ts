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

import {
  CellActionDescriptor,
  checkBoxCell,
  cellWithIcon,
  connectedStateCell,
  DateEntityTableColumn,
  EntityTableColumn,
  EntityTableConfig,
  GroupActionDescriptor, CellActionDescriptorType
} from '@home/models/entity/entities-table-config.models';
import { TranslateService } from '@ngx-translate/core';
import { DatePipe } from '@angular/common';
import { TimePageLink } from '@shared/models/page/page-link';
import { forkJoin, Observable } from 'rxjs';
import { PageData } from '@shared/models/page/page-data';
import { ClientSessionService } from '@core/http/client-session.service';
import {
  ConnectionState,
  connectionStateColor,
  connectionStateTranslationMap,
  DetailedClientSessionInfo,
  SessionFilterConfig,
  SessionQuery
} from '@shared/models/session.model';
import { clientTypeColor, clientTypeIcon, clientTypeTranslationMap, clientTypeValueColor } from '@shared/models/client.model';
import { Direction } from '@shared/models/page/sort-order';
import { DialogService } from '@core/services/dialog.service';
import { SessionTableHeaderComponent } from '@home/pages/sessions/session-table-header.component';
import { forAllTimeInterval } from '@shared/models/time/time.models';
import { ActivatedRoute, Router } from '@angular/router';
import { deepClone } from '@core/utils';
import { EntityType, entityTypeResources, entityTypeTranslations } from '@shared/models/entity-type.models';
import { tap } from 'rxjs/operators';
import { coerceBooleanProperty } from '@angular/cdk/coercion';

export class SessionsTableConfig extends EntityTableConfig<DetailedClientSessionInfo, TimePageLink> {

  sessionFilterConfig: SessionFilterConfig = {};

  constructor(private clientSessionService: ClientSessionService,
              private translate: TranslateService,
              private datePipe: DatePipe,
              private dialogService: DialogService,
              public entityId: string = null,
              private route: ActivatedRoute,
              private router: Router) {
    super();
    this.loadDataOnInit = true;
    this.detailsPanelEnabled = false;
    this.addEnabled = false;
    this.entitiesDeleteEnabled = false;
    this.tableTitle = this.translate.instant('mqtt-client-session.type-sessions');
    this.entityTranslations = entityTypeTranslations.get(EntityType.MQTT_SESSION);
    this.entityResources = entityTypeResources.get(EntityType.MQTT_SESSION);
    this.defaultSortOrder = {property: 'connectedAt', direction: Direction.DESC};

    this.groupActionDescriptors = this.configureGroupActions();
    this.cellActionDescriptors = this.configureCellActions();

    this.headerComponent = SessionTableHeaderComponent;
    this.useTimePageLink = true;
    this.forAllTimeEnabled = true;
    this.defaultTimewindowInterval = forAllTimeInterval();
    this.rowPointer = true;

    this.entitiesFetchFunction = pageLink => this.fetchSessions(pageLink);
    this.handleRowClick = ($event, entity) => this.showSessionDetails($event, entity);

    this.columns.push(
      new DateEntityTableColumn<DetailedClientSessionInfo>('connectedAt', 'mqtt-client-session.connected-at', this.datePipe, '150px'),
      new EntityTableColumn<DetailedClientSessionInfo>('connectionState', 'mqtt-client-session.connected-status', '100px',
        (entity) => connectedStateCell(this.translate.instant(connectionStateTranslationMap.get(entity.connectionState)), connectionStateColor.get(entity.connectionState))),
      new EntityTableColumn<DetailedClientSessionInfo>('clientType', 'mqtt-client.client-type', '100px',
        (entity) => {
        const clientType = entity.clientType;
        const clientTypeTranslation = this.translate.instant(clientTypeTranslationMap.get(clientType));
        const icon = clientTypeIcon.get(clientType);
        const color = clientTypeColor.get(clientType);
        const iconColor = clientTypeValueColor.get(clientType);
        return cellWithIcon(clientTypeTranslation, icon, color, iconColor, iconColor);
      }),
      new EntityTableColumn<DetailedClientSessionInfo>('clientId', 'mqtt-client.client-id', '50%',
        undefined, () => undefined,
        true, () => ({}), () => undefined, false,
        {
          name: this.translate.instant('action.copy'),
          nameFunction: (entity) => this.translate.instant('action.copy') + ' ' + entity.clientId,
          icon: 'content_copy',
          style: {
            padding: '0px',
            'font-size': '16px',
            'line-height': '16px',
            height: '16px',
            color: 'rgba(0,0,0,.87)'
          },
          isEnabled: (entity) => !!entity.clientId?.length,
          onAction: ($event, entity) => entity.clientId,
          type: CellActionDescriptorType.COPY_BUTTON
        }
      ),
      new EntityTableColumn<DetailedClientSessionInfo>('clientIpAdr', 'mqtt-client-session.client-ip', '10%',
        undefined, () => undefined,
        true, () => ({}), () => undefined, false,
        {
          name: this.translate.instant('action.copy'),
          nameFunction: (entity) => this.translate.instant('action.copy') + ' ' + entity.clientIpAdr,
          icon: 'content_copy',
          style: {
            padding: '0px',
            'font-size': '16px',
            'line-height': '16px',
            height: '16px',
            color: 'rgba(0,0,0,.87)'
          },
          isEnabled: (entity) => !!entity.clientIpAdr?.length,
          onAction: ($event, entity) => entity.clientIpAdr,
          type: CellActionDescriptorType.COPY_BUTTON
        }),
      new EntityTableColumn<DetailedClientSessionInfo>('subscriptionsCount', 'mqtt-client-session.subscriptions-count', '100px',
        (entity) => entity.subscriptionsCount.toString()),
      new EntityTableColumn<DetailedClientSessionInfo>('nodeId', 'mqtt-client-session.node-id', '100px'),
      new DateEntityTableColumn<DetailedClientSessionInfo>('disconnectedAt', 'mqtt-client-session.disconnected-at', this.datePipe, '150px'),
      new EntityTableColumn<DetailedClientSessionInfo>('cleanStart', 'mqtt-client-session.clean-start', '60px',
        entity => checkBoxCell(entity?.cleanStart))
    );
  }

  private fetchSessions(pageLink: TimePageLink): Observable<PageData<DetailedClientSessionInfo>> {
    const routerQueryParams: SessionFilterConfig = this.route.snapshot.queryParams;
    let openSession = false;
    if (routerQueryParams) {
      const queryParams = deepClone(routerQueryParams);
      let replaceUrl = false;
      if (routerQueryParams?.connectedStatusList) {
        this.sessionFilterConfig.connectedStatusList = routerQueryParams?.connectedStatusList;
        delete queryParams.connectedStatusList;
        replaceUrl = true;
      }
      if (routerQueryParams?.clientTypeList) {
        this.sessionFilterConfig.clientTypeList = routerQueryParams?.clientTypeList;
        delete queryParams.clientTypeList;
      }
      if (routerQueryParams?.clientId) {
        this.sessionFilterConfig.clientId = routerQueryParams?.clientId;
        delete queryParams.clientId;
      }
      if (routerQueryParams?.openSession) {
        openSession = coerceBooleanProperty(queryParams.openSession);
        delete queryParams.openSession;
      }
      if (replaceUrl) {
        this.router.navigate([], {
          relativeTo: this.route,
          queryParams,
          queryParamsHandling: '',
          replaceUrl: true
        });
      }
    }
    const sessionFilter = this.resolveSessionFilter(this.sessionFilterConfig);
    const query = new SessionQuery(pageLink, sessionFilter);
    return this.clientSessionService.getShortClientSessionInfosV2(query).pipe(
      tap((res) => {
        if (openSession) {
          const sessionId = res.data.find(
            el => el.clientId === this.sessionFilterConfig.clientId
          );
          if (sessionId) {
            this.showSessionDetails(null, sessionId);
          }
        }
      })
    );
  }

  private showSessionDetails($event: Event, entity: DetailedClientSessionInfo) {
    this.clientSessionService.openSessionDetailsDialog($event, entity.clientId).subscribe(
      (dialog) => {
        dialog.afterClosed().subscribe((res) => {
          if (res) {
            setTimeout(() => {
              this.getTable().updateData();
            }, 500)
          }
        });
      }
    );
    return false;
  }

  configureGroupActions(): Array<GroupActionDescriptor<DetailedClientSessionInfo>> {
    const actions: Array<GroupActionDescriptor<DetailedClientSessionInfo>> = [];
    actions.push(
      {
        name: this.translate.instant('mqtt-client-session.disconnect-client-sessions'),
        icon: 'mdi:link-off',
        isEnabled: true,
        onAction: ($event, entities) =>
          this.disconnectClientSessions($event, entities.filter(entity => entity.connectionState === ConnectionState.CONNECTED), entities)
      },
      {
        name: this.translate.instant('mqtt-client-session.remove-sessions'),
        icon: 'mdi:trash-can-outline',
        isEnabled: true,
        onAction: ($event, entities) =>
          this.removeClientSessions($event, entities.filter(entity => entity.connectionState === ConnectionState.DISCONNECTED), entities)
      }
    );
    return actions;
  }

  configureCellActions(): Array<CellActionDescriptor<DetailedClientSessionInfo>> {
    const actions: Array<CellActionDescriptor<DetailedClientSessionInfo>> = [];
    /*actions.push(
      {
        name: this.translate.instant('mqtt-client-session.disconnect-client-sessions'),
        icon: 'mdi:link-off',
        isEnabled: (entity) => (entity.connectionState === ConnectionState.CONNECTED),
        onAction: ($event, entity) => this.disconnectClientSession($event, entity)
      },
      {
        name: this.translate.instant('mqtt-client-session.remove-session'),
        icon: 'mdi:trash-can-outline',
        isEnabled: (entity) => (entity.connectionState === ConnectionState.DISCONNECTED),
        onAction: ($event, entity) => this.removeClientSession($event, entity)
      }
    );*/
    return actions;
  }

  disconnectClientSessions($event: Event, filteredSessions: Array<DetailedClientSessionInfo>, sessions: Array<DetailedClientSessionInfo>) {
    if ($event) {
      $event.stopPropagation();
    }
    if (!filteredSessions.length) {
      const title = this.translate.instant('mqtt-client-session.selected-sessions', {count: sessions.length});
      const content = this.translate.instant('mqtt-client-session.selected-sessions-are-disconnected');
      this.dialogService.alert(
        title,
        content).subscribe();
    } else {
      this.dialogService.confirm(
        this.translate.instant('mqtt-client-session.disconnect-client-sessions-title', {count: filteredSessions.length}),
        this.translate.instant('mqtt-client-session.disconnect-client-sessions-text'),
        this.translate.instant('action.no'),
        this.translate.instant('action.yes'),
        true
      ).subscribe((res) => {
          if (res) {
            const tasks: Observable<any>[] = [];
            filteredSessions.forEach(
              (session) => {
                tasks.push(this.clientSessionService.disconnectClientSession(session.clientId, session.sessionId));
              }
            );
            forkJoin(tasks).subscribe(() => {
              setTimeout(() => {
                this.updateTable();
              }, 1000)
            });
          }
        }
      );
    }
  }

  disconnectClientSession($event: Event, session: DetailedClientSessionInfo) {
    if ($event) {
      $event.stopPropagation();
    }
    const title = this.translate.instant('mqtt-client-session.disconnect-client-session-title', {clientId: session.clientId});
    const content = this.translate.instant('mqtt-client-session.disconnect-client-session-text');
    this.dialogService.confirm(
      title,
      content,
      this.translate.instant('action.no'),
      this.translate.instant('action.yes'),
      true
    ).subscribe((res) => {
        if (res) {
          this.clientSessionService.disconnectClientSession(session.clientId, session.sessionId).subscribe(() => this.updateTable());
        }
      }
    );
  }

  removeClientSession($event: Event, session: DetailedClientSessionInfo) {
    if ($event) {
      $event.stopPropagation();
    }
    const title = this.translate.instant('mqtt-client-session.remove-session-title', {clientId: session.clientId});
    const content = this.translate.instant('mqtt-client-session.remove-session-text');
    this.dialogService.confirm(
      title,
      content,
      this.translate.instant('action.no'),
      this.translate.instant('action.yes'),
      true
    ).subscribe((res) => {
        if (res) {
          this.clientSessionService.removeClientSession(session.clientId, session.sessionId).subscribe(() => this.updateTable());
        }
      }
    );
  }

  removeClientSessions($event: Event, filteredSessions: Array<DetailedClientSessionInfo>, sessions: Array<DetailedClientSessionInfo>) {
    if ($event) {
      $event.stopPropagation();
    }
    if (!filteredSessions.length) {
      const title = this.translate.instant('mqtt-client-session.selected-sessions', {count: sessions.length});
      const content = this.translate.instant('mqtt-client-session.selected-sessions-are-connected');
      this.dialogService.alert(
        title,
        content).subscribe();
    } else {
      this.dialogService.confirm(
        this.translate.instant('mqtt-client-session.remove-sessions-title', {count: filteredSessions.length}),
        this.translate.instant('mqtt-client-session.remove-sessions-text'),
        this.translate.instant('action.no'),
        this.translate.instant('action.yes'),
        true
      ).subscribe((res) => {
          if (res) {
            const tasks: Observable<any>[] = [];
            filteredSessions.forEach(
              (session) => {
                tasks.push(this.clientSessionService.removeClientSession(session.clientId, session.sessionId));
              }
            );
            forkJoin(tasks).subscribe(() =>
              setTimeout(() => {
                this.updateTable();
              }, 1000)
            );
          }
        }
      );
    }
  }

  private updateTable() {
    this.getTable().updateData();
  }

  private resolveSessionFilter(sessionFilterConfig?: SessionFilterConfig): SessionFilterConfig {
    const sessionFilter: SessionFilterConfig = {};
    if (sessionFilterConfig) {
      sessionFilter.clientId = sessionFilterConfig.clientId;
      sessionFilter.connectedStatusList = sessionFilterConfig.connectedStatusList;
      sessionFilter.clientTypeList = sessionFilterConfig.clientTypeList;
      sessionFilter.cleanStartList = sessionFilterConfig.cleanStartList;
      sessionFilter.nodeIdList = sessionFilterConfig.nodeIdList;
      sessionFilter.subscriptions = sessionFilterConfig.subscriptions;
      sessionFilter.subscriptionOperation = sessionFilterConfig.subscriptionOperation;
      sessionFilter.clientIpAddress = sessionFilterConfig.clientIpAddress;
    }
    return sessionFilter;
  }
}
