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
import { MqttSessionsComponent } from '@home/pages/mqtt-sessions/mqtt-sessions.component';
import { MqttClientSessionService } from '@core/http/mqtt-client-session.service';
import { EntityAction } from '@home/models/entity/entity-component.models';
import { MqttSubscriptionService } from '@core/http/mqtt-subscription.service';
import {
  ConnectionState,
  connectionStateColor,
  connectionStateTranslationMap, DetailedClientSessionInfo
} from '@shared/models/mqtt-session.model';
import { clientTypeTranslationMap } from '@shared/models/mqtt-client.model';

@Injectable()
export class MqttSessionsTableConfigResolver implements Resolve<EntityTableConfig<DetailedClientSessionInfo>> {

  private readonly config: EntityTableConfig<DetailedClientSessionInfo> = new EntityTableConfig<DetailedClientSessionInfo>();

  constructor(private store: Store<AppState>,
              private dialogService: DialogService,
              private mqttClientSessionService: MqttClientSessionService,
              private mqttSubscriptionService: MqttSubscriptionService,
              private translate: TranslateService) {

    this.config.entityComponent = MqttSessionsComponent;
    this.config.entityTranslations = entityTypeTranslations.get(EntityType.MQTT_SESSION);
    this.config.entityResources = entityTypeResources.get(EntityType.MQTT_SESSION);
    this.config.tableTitle = this.translate.instant('mqtt-client-session.sessions');

    this.config.addEnabled = false;
    this.config.entitiesDeleteEnabled = false;
    this.config.deleteEnabled = () => false;

    this.config.entityTitle = (mqttClient) => mqttClient ? mqttClient.clientId : '';

    this.config.columns.push(
      new EntityTableColumn<DetailedClientSessionInfo>('clientId', 'mqtt-client.client-id', '25%'),
      new EntityTableColumn<DetailedClientSessionInfo>('connectionState', 'mqtt-client-session.connect', '25%',
        (entity) => this.translate.instant(connectionStateTranslationMap.get(entity.connectionState)),
        (entity) => (this.setCellStyle(entity.connectionState))
      ),
      new EntityTableColumn<DetailedClientSessionInfo>('nodeId', 'mqtt-client-session.node-id', '25%'),
      new EntityTableColumn<DetailedClientSessionInfo>('clientType', 'mqtt-client.client-type', '25%',
        (entity) => this.translate.instant(clientTypeTranslationMap.get(entity.clientType))
      )
    );

    this.config.loadEntity = id => this.loadEntity(id);
    this.config.saveEntity = session => this.mqttSubscriptionService.updateClientSubscriptions(session);
    this.config.onEntityAction = action => this.onClientSessionAction(action);
  }

  resolve(): EntityTableConfig<DetailedClientSessionInfo> {
    this.config.entitiesFetchFunction = pageLink => this.mqttClientSessionService.getShortClientSessionInfos(pageLink);
    return this.config;
  }

  loadEntity(id) {
    return this.mqttClientSessionService.getDetailedClientSessionInfo(id);
  }

  onClientSessionAction(action: EntityAction<DetailedClientSessionInfo>): boolean {
    switch (action.action) {
      case 'remove':
        this.removeSession(action.event, action.entity);
        return true;
      case 'disconnect':
        this.disconnectClient(action.event, action.entity);
        return true;
    }
    return false;
  }

  removeSession($event: Event, clientSession: DetailedClientSessionInfo) {
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
    this.mqttClientSessionService.disconnectClientSession(clientSession.clientId, clientSession.sessionId).subscribe();
  }

  private setCellStyle(connectionState: ConnectionState): any {
    const style: any = {
      color: connectionStateColor.get(connectionState)
    };
    if (connectionState === ConnectionState.CONNECTED) {
      style.fontWeight = 'bold';
    }
    return style;
  }
}
