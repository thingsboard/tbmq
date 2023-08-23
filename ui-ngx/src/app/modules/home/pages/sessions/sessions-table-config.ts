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
  checkBoxCell, clientTypeCell,
  DateEntityTableColumn,
  EntityTableColumn,
  EntityTableConfig, GroupActionDescriptor, defaultCellStyle
} from '@home/models/entity/entities-table-config.models';
import { TranslateService } from '@ngx-translate/core';
import { DatePipe } from '@angular/common';
import { MatDialog } from '@angular/material/dialog';
import { TimePageLink } from '@shared/models/page/page-link';
import { forkJoin, Observable, of } from 'rxjs';
import { PageData } from '@shared/models/page/page-data';
import { MqttClientSessionService } from '@core/http/mqtt-client-session.service';
import {
  SessionsDetailsDialogComponent,
  SessionsDetailsDialogData
} from '@home/pages/sessions/sessions-details-dialog.component';
import {
  ConnectionState,
  connectionStateColor,
  connectionStateTranslationMap,
  DetailedClientSessionInfo
} from '@shared/models/session.model';
import { clientTypeTranslationMap } from '@shared/models/client.model';
import { HelpLinks } from '@shared/models/constants';
import { Direction } from '@shared/models/page/sort-order';
import { DialogService } from '@core/services/dialog.service';

export class SessionsTableConfig extends EntityTableConfig<DetailedClientSessionInfo, TimePageLink> {

  constructor(private mqttClientSessionService: MqttClientSessionService,
              private translate: TranslateService,
              private datePipe: DatePipe,
              private dialog: MatDialog,
              private dialogService: DialogService,
              public entityId: string = null) {
    super();
    this.loadDataOnInit = true;
    this.detailsPanelEnabled = false;
    this.selectionEnabled = false;
    this.searchEnabled = true;
    this.addEnabled = false;
    this.entitiesDeleteEnabled = false;
    this.tableTitle = this.translate.instant('mqtt-client-session.type-sessions');
    this.entityTranslations = {
      noEntities: 'mqtt-client-session.no-session-text',
      search: 'mqtt-client-session.search'
    };
    this.defaultSortOrder = {property: 'connectedAt', direction: Direction.DESC};
    /*this.groupActionDescriptors = this.configureGroupActions();
    this.cellActionDescriptors = this.configureCellActions();*/

    this.entitiesFetchFunction = pageLink => this.fetchSessions(pageLink);
    this.handleRowClick = ($event, entity) => this.showSessionDetails($event, entity);

    /*this.headerActionDescriptors.push(
      {
        name: this.translate.instant('help.goto-help-page'),
        icon: 'help_outline',
        isEnabled: () => true,
        onAction: () => this.gotoHelpPage(),

      }
    );*/


    this.columns.push(
      new DateEntityTableColumn<DetailedClientSessionInfo>('connectedAt', 'mqtt-client-session.connected-at', this.datePipe, '160px'),
      new EntityTableColumn<DetailedClientSessionInfo>('connectionState', 'mqtt-client-session.connected-status', '10%',
        (entity) => {
          return '<span style="width: 8px; height: 8px; border-radius: 16px; display: inline-block; vertical-align: middle; background:' +
            connectionStateColor.get(entity.connectionState) +
            '"></span>' +
            '<span style="background: rgba(111, 116, 242, 0); border-radius: 16px; padding: 4px 8px; font-weight: 600">' +
            this.translate.instant(connectionStateTranslationMap.get(entity.connectionState)) +
            '</span>';
        },
        (entity) => ({color: connectionStateColor.get(entity.connectionState)})
      ),
      new EntityTableColumn<DetailedClientSessionInfo>('clientType', 'mqtt-client.client-type', '10%',
        (entity) => clientTypeCell(this.translate.instant(clientTypeTranslationMap.get(entity.clientType)))),
      new EntityTableColumn<DetailedClientSessionInfo>('clientId', 'mqtt-client.client-id', '25%',
        (entity) => defaultCellStyle(entity.clientId),
        () => ({'font-weight': 500})),
      new EntityTableColumn<DetailedClientSessionInfo>('clientIpAdr', 'mqtt-client-session.client-ip', '15%',
        (entity) => defaultCellStyle(entity.clientIpAdr)),
      new EntityTableColumn<DetailedClientSessionInfo>('subscriptionsCount', 'mqtt-client-session.subscriptions-count', '10%',
        (entity) => defaultCellStyle(entity.subscriptionsCount)),
      new EntityTableColumn<DetailedClientSessionInfo>('nodeId', 'mqtt-client-session.node-id', '10%',
        (entity) => defaultCellStyle(entity.nodeId)),
      new DateEntityTableColumn<DetailedClientSessionInfo>('disconnectedAt', 'mqtt-client-session.disconnected-at', this.datePipe, '160px'),
      new EntityTableColumn<DetailedClientSessionInfo>('cleanStart', 'mqtt-client-session.clean-start', '60px',
        entity => checkBoxCell(entity?.cleanStart))
    );
  }

  private fetchSessions(pageLink: TimePageLink): Observable<PageData<DetailedClientSessionInfo>> {
    return this.mqttClientSessionService.getShortClientSessionInfos(pageLink);
  }

  private showSessionDetails($event: Event, entity: DetailedClientSessionInfo) {
    if ($event) {
      $event.stopPropagation();
    }
    this.mqttClientSessionService.getDetailedClientSessionInfo(entity.clientId).subscribe(
      session => {
        this.dialog.open<SessionsDetailsDialogComponent, SessionsDetailsDialogData>(SessionsDetailsDialogComponent, {
          disableClose: true,
          panelClass: ['tb-dialog', 'tb-fullscreen-dialog'],
          data: {
            session
          }
        }).afterClosed().subscribe(() => {
          this.table.updateData();
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
        icon: 'portable_wifi_off',
        isEnabled: true,
        onAction: ($event, entities) => this.disconnectClientSessions($event, entities.map((entity) => entity))
      }
    );
    return actions;
  }

  configureCellActions(): Array<CellActionDescriptor<DetailedClientSessionInfo>> {
    const actions: Array<CellActionDescriptor<DetailedClientSessionInfo>> = [];
    actions.push(
      {
        name: this.translate.instant('mqtt-client-session.disconnect-client-sessions'),
        icon: 'portable_wifi_off',
        isEnabled: (entity) => (entity.connectionState === ConnectionState.CONNECTED),
        onAction: ($event, entity) => this.disconnectClientSession($event, entity)
      },
      {
        name: this.translate.instant('mqtt-client-session.remove-session'),
        mdiIcon: 'mdi:trash-can-outline',
        isEnabled: (entity) => (entity.connectionState === ConnectionState.DISCONNECTED),
        onAction: ($event, entity) => this.removeClientSession($event, entity)
      }
    );
    return actions;
  }

  disconnectClientSessions($event: Event, sessions: Array<DetailedClientSessionInfo>) {
    if ($event) {
      $event.stopPropagation();
    }
    this.dialogService.confirm(
      this.translate.instant('mqtt-client-session.disconnect-client-sessions-title', {count: sessions.length}),
      this.translate.instant('mqtt-client-session.disconnect-client-sessions-text'),
      this.translate.instant('action.no'),
      this.translate.instant('action.yes'),
      true
    ).subscribe((res) => {
        if (res) {
          const tasks: Observable<any>[] = [];
          sessions.forEach(
            (session) => {
              tasks.push(this.mqttClientSessionService.disconnectClientSession(session.clientId, session.sessionId));
            }
          );
          forkJoin(tasks).subscribe(
            () => {
              this.table.updateData();
            }
          );
        }
      }
    );
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
          this.mqttClientSessionService.disconnectClientSession(session.clientId, session.sessionId).subscribe(
            () => {
              this.table.updateData();
            }
          );
        }
      }
    );
  }

  removeClientSession($event: Event, session: DetailedClientSessionInfo) {
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
          this.mqttClientSessionService.disconnectClientSession(session.clientId, session.sessionId).subscribe(
            () => {
              this.table.updateData();
            }
          );
        }
      }
    );
  }

  private gotoHelpPage(): void {
    const helpUrl = HelpLinks.linksMap.sessions;
    if (helpUrl) {
      window.open(helpUrl, '_blank');
    }
  }
}
