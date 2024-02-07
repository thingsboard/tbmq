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

import {
  arrowIcon,
  CellActionDescriptor,
  cellWithBackground,
  DateEntityTableColumn,
  EntityTableColumn,
  EntityTableConfig
} from '@home/models/entity/entities-table-config.models';
import { TranslateService } from '@ngx-translate/core';
import { DatePipe } from '@angular/common';
import { EntityType, entityTypeResources, entityTypeTranslations } from '@shared/models/entity-type.models';
import { DialogService } from '@core/services/dialog.service';
import { ContentType } from '@shared/models/constants';
import { MatDialog } from '@angular/material/dialog';
import { WsQoSTranslationMap } from '@shared/models/session.model';
import { isDefinedAndNotNull } from '@core/utils';
import { PublishMessageProperties, WsClientMessageTypeTranslationMap, WsTableMessage } from '@shared/models/ws-client.model';
import { MqttJsClientService } from '@core/http/mqtt-js-client.service';
import {
  WsMessageContentDialogComponentDialogData,
  WsMessagePayloadDialogComponent
} from '@home/pages/ws-client/messages/ws-message-payload-dialog.component';
import {
  WsMessagePropertiesDialogComponent,
  WsMessagePropertiesDialogData
} from '@home/pages/ws-client/messages/ws-message-properties-dialog.component';

export class MessagesTableConfig extends EntityTableConfig<WsTableMessage> {

  constructor(private dialogService: DialogService,
              private mqttJsClientService: MqttJsClientService,
              private translate: TranslateService,
              private dialog: MatDialog,
              private datePipe: DatePipe,
              public entityId: string = null) {
    super();

    this.entityType = EntityType.WS_MESSAGE;
    this.entityComponent = null;

    this.detailsPanelEnabled = false;
    this.entityTranslations = entityTypeTranslations.get(EntityType.WS_MESSAGE);
    this.entityResources = entityTypeResources.get(EntityType.WS_MESSAGE);
    this.entitiesDeleteEnabled = false;
    this.addEnabled = false;
    this.defaultCursor = true;
    this.displayPagination = false;
    this.selectionEnabled = false;

    this.entityTitle = (message) => message ? message.topic : '';
    this.detailsReadonly = () => true;

    this.cellActionDescriptors = this.configureCellActions();

    this.columns.push(
      new EntityTableColumn<WsTableMessage>('type', undefined, '30px', (entity) => {
        const messageReceived = entity.type === 'received';
        const color = entity?.color || 'rgba(0, 0, 0, 0.38)';
        return arrowIcon(messageReceived, color);
      }, () => undefined, false, undefined, (entity) => {
        const messageReceived = entity.type === 'received';
        return this.translate.instant(WsClientMessageTypeTranslationMap.get(messageReceived));
      }),
      new DateEntityTableColumn<WsTableMessage>('createdTime', 'common.time', this.datePipe, '150px'),
      new EntityTableColumn<WsTableMessage>('topic', 'retained-message.topic', '75%'),
      new EntityTableColumn<WsTableMessage>('qos', 'retained-message.qos', '25px', entity => entity.qos.toString(),
        undefined, undefined, undefined, (entity) => this.translate.instant(WsQoSTranslationMap.get(entity.qos))),
      new EntityTableColumn<WsTableMessage>('retain', 'ws-client.messages.retained', '25%',
        entity => entity.retain ? cellWithBackground('True', 'rgba(0, 0, 0, 0.08)') : ''
      ),
      new EntityTableColumn<WsTableMessage>('payload', 'retained-message.payload', '200px', (entity) => {
        const content = entity.payload;
        try {
          const parsedContent = JSON.parse(content);
          return JSON.stringify(parsedContent, null, 2);
        } catch (e) {
          return content;
        }
      }, undefined, undefined, undefined, (entity) => entity.payload)
    );

    this.entitiesFetchFunction = (pageLink) => this.mqttJsClientService.getMessages(pageLink);

    this.mqttJsClientService.clientMessages$.subscribe(() => {
      this.updateData();
    });

    this.mqttJsClientService.selectedConnection$.subscribe(() => {
      this.updateData();
    });
  }

  private configureCellActions(): Array<CellActionDescriptor<WsTableMessage>> {
    const actions: Array<CellActionDescriptor<WsTableMessage>> = [];
    actions.push(
      {
        name: this.translate.instant('retained-message.payload'),
        icon: 'mdi:code-braces',
        isEnabled: (entity) => isDefinedAndNotNull(entity.payload),
        onAction: ($event, entity) => this.showPayload($event, entity.payload, 'retained-message.payload')
      },
      {
        name: this.translate.instant('ws-client.connections.properties'),
        icon: 'mdi:information-outline',
        isEnabled: (entity) => isDefinedAndNotNull(entity.properties),
        onAction: ($event, entity) => this.showPayloadProperties($event, entity.properties)
      }
    );
    return actions;
  }

  private showPayload($event: MouseEvent, content: string, title: string): void {
    if ($event) {
      $event.stopPropagation();
    }
    this.dialog.open<WsMessagePayloadDialogComponent, WsMessageContentDialogComponentDialogData>(WsMessagePayloadDialogComponent, {
      disableClose: false,
      panelClass: ['tb-dialog', 'tb-fullscreen-dialog'],
      data: {
        content,
        title,
        icon: 'mdi:code-braces',
        contentType: ContentType.JSON
      }
    });
  }

  private showPayloadProperties($event: MouseEvent, entity: PublishMessageProperties): void {
    if ($event) {
      $event.stopPropagation();
    }
    this.dialog.open<WsMessagePropertiesDialogComponent, WsMessagePropertiesDialogData>(WsMessagePropertiesDialogComponent, {
      disableClose: false,
      panelClass: ['tb-dialog', 'tb-fullscreen-dialog'],
      data: {
        entity
      }
    });
  }
}
