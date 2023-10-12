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

import { Injectable } from '@angular/core';

import { Resolve } from '@angular/router';
import {
  CellActionDescriptor,
  DateEntityTableColumn,
  EntityTableColumn,
  EntityTableConfig,
  GroupActionDescriptor
} from '@home/models/entity/entities-table-config.models';
import { TranslateService } from '@ngx-translate/core';
import { DatePipe } from '@angular/common';
import { EntityType, entityTypeResources, entityTypeTranslations } from '@shared/models/entity-type.models';
import { Store } from '@ngrx/store';
import { AppState } from '@core/core.state';
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

@Injectable()
export class RetainedMessagesTableConfigResolver implements Resolve<EntityTableConfig<RetainedMessage>> {

  private readonly config: EntityTableConfig<RetainedMessage> = new EntityTableConfig<RetainedMessage>();

  constructor(private store: Store<AppState>,
              private dialogService: DialogService,
              private retainedMsgService: RetainedMsgService,
              private translate: TranslateService,
              private dialog: MatDialog,
              private datePipe: DatePipe) {

    this.config.entityType = EntityType.MQTT_CLIENT_CREDENTIALS;
    this.config.detailsPanelEnabled = false;
    this.config.entityTranslations = entityTypeTranslations.get(EntityType.RETAINED_MESSAGE);
    this.config.entityResources = entityTypeResources.get(EntityType.RETAINED_MESSAGE);
    this.config.tableTitle = this.translate.instant('retained-message.retained-messages');
    this.config.entitiesDeleteEnabled = false;
    this.config.addEnabled = false;
    this.config.defaultCursor = true;

    this.config.entityTitle = (message) => message ? message.topic : '';
    this.config.detailsReadonly = () => true;

    this.config.groupActionDescriptors = this.configureGroupActions();
    this.config.cellActionDescriptors = this.configureCellActions();

    this.config.headerActionDescriptors.push({
      name: this.translate.instant('retained-message.clear-empty-retained-message-nodes'),
      icon: 'delete_forever',
      isEnabled: () => true,
      onAction: ($event) => {
        this.clearEmptyRetainedMsgNodes($event);
      }
    });

    this.config.columns.push(
      new DateEntityTableColumn<RetainedMessage>('createdTime', 'common.created-time', this.datePipe, '150px'),
      new EntityTableColumn<RetainedMessage>('topic', 'retained-message.topic', '50%'),
      new EntityTableColumn<RetainedMessage>('qos', 'retained-message.qos', '50%', (entity) => {
        return entity.qos + ' - ' + this.translate.instant(QoSTranslationMap.get(entity.qos));
      })
    );
  }

  resolve(): EntityTableConfig<RetainedMessage> {
    this.config.entitiesFetchFunction = pageLink => this.retainedMsgService.getRetainedMessages(pageLink);
    return this.config;
  }

  private configureGroupActions(): Array<GroupActionDescriptor<RetainedMessage>> {
    const actions: Array<GroupActionDescriptor<RetainedMessage>> = [];
    actions.push(
      {
        name: this.translate.instant('action.delete'),
        icon: 'mdi:trash-can-outline',
        isEnabled: true,
        onAction: ($event, entities) => this.deleteEntities($event, entities)
      }
    );
    return actions;
  }

  private configureCellActions(): Array<CellActionDescriptor<RetainedMessage>> {
    const actions: Array<CellActionDescriptor<RetainedMessage>> = [];
    actions.push(
      {
        name: this.translate.instant('retained-message.show-data'),
        icon: 'mdi:code-braces',
        isEnabled: () => true,
        onAction: ($event, entity) => this.showPayload($event, entity.payload, 'retained-message.show-data')
      },
      {
        name: this.translate.instant('retained-message.show-user-properties'),
        icon: 'mdi:code-brackets',
        isEnabled: (entity) => isDefinedAndNotNull(entity.userProperties),
        onAction: ($event, entity) => this.showPayload($event, JSON.stringify(entity.userProperties), 'retained-message.show-user-properties')
      },
      {
        name: this.translate.instant('action.delete'),
        icon: 'mdi:trash-can-outline',
        isEnabled: () => true,
        onAction: ($event, entity) => this.deleteEntity($event, entity)
      }
    );
    return actions;
  }

  private deleteEntities($event: Event, entities: Array<RetainedMessage>) {
    if ($event) {
      $event.stopPropagation();
    }
    this.dialogService.confirm(
      this.translate.instant('retained-message.delete-retained-messages-title', {count: entities.length}),
      this.translate.instant('retained-message.delete-retained-messages-text'),
      this.translate.instant('action.no'),
      this.translate.instant('action.yes'),
      true
    ).subscribe((res) => {
        if (res) {
          const tasks: Observable<any>[] = [];
          entities.forEach(
            (entity) => {
              tasks.push(this.retainedMsgService.deleteRetainedMessage(entity.topic));
            }
          );
          forkJoin(tasks).subscribe(
            () => {
              this.config.getTable().updateData();
            }
          );
        }
      }
    );
  }

  private deleteEntity($event: Event, entity: RetainedMessage) {
    if ($event) {
      $event.stopPropagation();
    }
    this.dialogService.confirm(
      this.translate.instant('retained-message.delete-retained-message-title', {topic: entity.topic}),
      this.translate.instant('retained-message.delete-retained-message-text'),
      this.translate.instant('action.no'),
      this.translate.instant('action.yes'),
      true
    ).subscribe((result) => {
      if (result) {
        this.retainedMsgService.deleteRetainedMessage(entity.topic).subscribe(
          () => {
            this.config.getTable().updateData();
          }
        );
      }
    });
  }

  private clearEmptyRetainedMsgNodes($event: Event) {
    if ($event) {
      $event.stopPropagation();
    }
    this.dialogService.confirm(
      this.translate.instant('retained-message.clear-empty-retained-message-nodes-title'),
      this.translate.instant('retained-message.clear-empty-retained-message-nodes-text'),
      this.translate.instant('action.no'),
      this.translate.instant('action.yes'),
      true
    ).subscribe((result) => {
      if (result) {
        this.retainedMsgService.clearEmptyRetainedMsgNodes().subscribe(
          () => {
            this.config.getTable().updateData();
          }
        );
      }
    });
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
