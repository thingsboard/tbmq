///
/// Copyright © 2016-2020 The Thingsboard Authors
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

import { Injectable } from '@angular/core';

import { Resolve } from '@angular/router';
import { EntityTableColumn, EntityTableConfig } from '@home/models/entity/entities-table-config.models';
import { TranslateService } from '@ngx-translate/core';
import { EntityType, entityTypeResources, entityTypeTranslations } from '@shared/models/entity-type.models';
import { Store } from '@ngrx/store';
import { AppState } from '@core/core.state';
import { DialogService } from '@core/services/dialog.service';
import {
  ClientSessionInfo,
  clientTypeTranslationMap,
  ConnectionState,
  connectionStateColor,
  connectionStateTranslationMap
} from '@shared/models/mqtt.models';
import { MqttClientsComponent } from '@home/pages/mqtt-clients/mqtt-clients.component';
import { MqttClientSessionService } from '@core/http/mqtt-client-session.service';
import { EntityAction } from '@home/models/entity/entity-component.models';
import { MqttSubscriptionService } from '@core/http/mqtt-subscription.service';
import { ActionNotificationShow } from '@core/notification/notification.actions';
import { of } from 'rxjs';

@Injectable()
export class MqttClientsTableConfigResolver implements Resolve<EntityTableConfig<ClientSessionInfo>> {

  private readonly config: EntityTableConfig<ClientSessionInfo> = new EntityTableConfig<ClientSessionInfo>();

  constructor(private store: Store<AppState>,
              private dialogService: DialogService,
              private mqttClientSessionService: MqttClientSessionService,
              private mqttSubscriptionService: MqttSubscriptionService,
              private translate: TranslateService) {

    this.config.entityComponent = MqttClientsComponent;
    this.config.entityTranslations = entityTypeTranslations.get(EntityType.MQTT_CLIENT);
    this.config.entityResources = entityTypeResources.get(EntityType.MQTT_CLIENT);
    this.config.tableTitle = this.translate.instant('mqtt-client-session.sessions');

    this.config.addEnabled = false;
    this.config.entitiesDeleteEnabled = false;
    this.config.deleteEnabled = () => false;

    this.config.entityTitle = (mqttClient) => mqttClient ? mqttClient.clientId : '';

    this.config.columns.push(
      new EntityTableColumn<ClientSessionInfo>('clientId', 'mqtt-client-session.client-id', '25%'),
      new EntityTableColumn<ClientSessionInfo>('connectionState', 'mqtt-client-session.connect', '25%',
        (entity) => this.translate.instant(connectionStateTranslationMap.get(entity.connectionState)),
        (entity) => (this.setCellStyle(entity.connectionState))
      ),
      new EntityTableColumn<ClientSessionInfo>('nodeId', 'mqtt-client-session.node-id', '25%'),
      new EntityTableColumn<ClientSessionInfo>('clientType', 'mqtt-client-session.client-type', '25%',
        (entity) => this.translate.instant(clientTypeTranslationMap.get(entity.clientType))
      )
    );

    this.config.loadEntity = id => this.loadEntity(id);
    this.config.saveEntity = session => this.saveEntity(session);
    this.config.onEntityAction = action => this.onClientSessionAction(action);
  }

  private saveEntity(session: ClientSessionInfo) {
    this.mqttSubscriptionService.updateClientSubscriptions(session).subscribe();
    return of(session);
  }

  resolve(): EntityTableConfig<ClientSessionInfo> {
    this.config.entitiesFetchFunction = pageLink => this.mqttClientSessionService.getShortClientSessionInfos(pageLink);
    return this.config;
  }

  loadEntity(id) {
    return this.mqttClientSessionService.getDetailedClientSessionInfo(id);
  }

  onClientSessionAction(action: EntityAction<ClientSessionInfo>): boolean {
    switch (action.action) {
      case 'remove':
        this.removeSession(action.event, action.entity);
        return true;
      case 'disconnect':
        this.disconnectClient(action.event, action.entity);
        return true;
      case 'refresh':
        this.refreshPage(action.event, action.entity);
        return true;
    }
    return false;
  }

  removeSession($event: Event, clientSession: ClientSessionInfo) {
    if ($event) {
      $event.stopPropagation();
    }
    let title = this.translate.instant('mqtt-client-session.remove-session-title', {clientId: clientSession.clientId});
    let content = this.translate.instant('mqtt-client-session.remove-session-text');
    this.dialogService.confirm(
      title,
      content,
      this.translate.instant('action.no'),
      this.translate.instant('action.yes'),
      true
    ).subscribe((res) => {
        if (res) {
          this.mqttClientSessionService.removeClientSession(clientSession.clientId, clientSession.sessionId).subscribe(
            () => {
              this.config.table.updateData();
            }
          );
        }
      }
    );
  }

  disconnectClient($event, clientSession) {
    if ($event) {
      $event.stopPropagation();
    }
    this.mqttClientSessionService.disconnectClientSession(clientSession.clientId, clientSession.sessionId).subscribe(
      () => {
        this.store.dispatch(
          new ActionNotificationShow(
            {
              message: this.translate.instant('mqtt-client-session.disconnected-notification'),
              type: 'success'
            }
          )
        );
      }
    );
  }

  refreshPage($event, entity) {
    this.config.table.updateData(true);
  }

  private setCellStyle(connectionState: ConnectionState): any {
    const color = connectionStateColor.get(connectionState);
    const fontWeight = connectionState === ConnectionState.CONNECTED ? 500 : 400;
    return { ...{color}, ...{fontWeight} };
  }
}
