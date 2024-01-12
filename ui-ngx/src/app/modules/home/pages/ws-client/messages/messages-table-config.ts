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
  CellActionDescriptorType, cellWithBackground, colorIcon,
  DateEntityTableColumn,
  EntityTableColumn,
  EntityTableConfig,
  GroupActionDescriptor
} from '@home/models/entity/entities-table-config.models';
import { TranslateService } from '@ngx-translate/core';
import { DatePipe } from '@angular/common';
import { EntityType, entityTypeResources, entityTypeTranslations } from '@shared/models/entity-type.models';
import { DialogService } from '@core/services/dialog.service';
import { RetainedMessage } from '@shared/models/retained-message.model';
import { RetainedMsgService } from '@core/http/retained-msg.service';
import { forkJoin, Observable } from 'rxjs';
import {
  EventContentDialogComponent,
  EventContentDialogData
} from '@home/components/event/event-content-dialog.component';
import { ContentType } from '@shared/models/constants';
import { MatDialog } from '@angular/material/dialog';
import { QoSTranslationMap } from '@shared/models/session.model';
import { isDefinedAndNotNull } from '@core/utils';
import { WsMessage } from "@shared/models/ws-client.model";
import { WsClientService } from "@core/http/ws-client.service";
import { coerceBoolean } from "@shared/decorators/coercion";

export class MessagesTableConfig extends EntityTableConfig<WsMessage> {

  constructor(private dialogService: DialogService,
              private wsClientService: WsClientService,
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
      new EntityTableColumn<WsMessage>('color', null, '30px', (entity) => {
        const messageReceived = !!entity?.color?.length;
        const icon = messageReceived ? 'download' : 'publish';
        const color = entity?.color || 'rgba(0, 0, 0, 0.38)';
        return colorIcon(icon, color);
      }, () => undefined, false),
      new DateEntityTableColumn<WsMessage>('createdTime', 'common.created-time', this.datePipe, '150px'),
      new EntityTableColumn<WsMessage>('topic', 'retained-message.topic', '40%'),
      new EntityTableColumn<WsMessage>('qos', 'retained-message.qos', '10%'),
      new EntityTableColumn<WsMessage>('retain', 'ws-client.messages.retained', '10%',
          entity => entity.retain ? cellWithBackground('True', 'rgba(0, 0, 0, 0.08)') : ''
      ),
      new EntityTableColumn<WsMessage>('payload', 'retained-message.payload', '40%')
    );

    this.entitiesFetchFunction = pageLink => this.wsClientService.getMessages();
  }

  private configureCellActions(): Array<CellActionDescriptor<WsMessage>> {
    const actions: Array<CellActionDescriptor<WsMessage>> = [];
    actions.push(
      {
        name: this.translate.instant('retained-message.payload'),
        icon: 'mdi:code-braces',
        isEnabled: (entity) => isDefinedAndNotNull(entity.payload),
        onAction: ($event, entity) => this.showPayload($event, entity.payload, 'retained-message.show-data')
      },
      {
        name: this.translate.instant('ws-client.connections.properties'),
        icon: 'mdi:information-outline',
        isEnabled: (entity) => isDefinedAndNotNull(entity.userProperties),
        onAction: ($event, entity) => this.showPayload($event, JSON.stringify(entity.userProperties), 'retained-message.user-properties')
      }
    );
    return actions;
  }

  private showPayload($event: MouseEvent, content: string, title: string): void {
    if ($event) {
      $event.stopPropagation();
    }
    this.dialog.open<EventContentDialogComponent, EventContentDialogData>(EventContentDialogComponent, {
      disableClose: true,
      panelClass: ['tb-dialog', 'tb-fullscreen-dialog'],
      data: {
        content,
        title,
        contentType: ContentType.JSON
      }
    });
  }
}
