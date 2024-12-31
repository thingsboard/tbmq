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
  CellActionDescriptorType,
  DateEntityTableColumn,
  EntityTableColumn,
  EntityTableConfig,
  GroupActionDescriptor
} from '@home/models/entity/entities-table-config.models';
import { TranslateService } from '@ngx-translate/core';
import { DatePipe } from '@angular/common';
import { EntityType, entityTypeResources, entityTypeTranslations } from '@shared/models/entity-type.models';
import { DialogService } from '@core/services/dialog.service';
import {
  RetainedMessage,
  RetainedMessagesFilterConfig,
  RetainedMessagesQuery
} from '@shared/models/retained-message.model';
import { RetainedMsgService } from '@core/http/retained-msg.service';
import { forkJoin, Observable } from 'rxjs';
import { MatDialog } from '@angular/material/dialog';
import { WsQoSTranslationMap } from '@shared/models/session.model';
import { deepClone, isDefinedAndNotNull } from '@core/utils';
import { TimePageLink } from '@shared/models/page/page-link';
import { PageData } from '@shared/models/page/page-data';
import { ActivatedRoute } from '@angular/router';
import {
  RetainedMessagesTableHeaderComponent
} from '@home/pages/retained-messages/retained-messages-table-header.component';
import { forAllTimeInterval } from '@shared/models/time/time.models';
import {
  EventContentDialogV2ComponentDialogData,
  EventContentDialogV2Component
} from '@home/components/event/event-content-dialog-v2.component';

export class RetainedMessagesTableConfig extends EntityTableConfig<RetainedMessage> {

  retainedMessagesFilterConfig: RetainedMessagesFilterConfig = {};

  constructor(private dialogService: DialogService,
              private retainedMsgService: RetainedMsgService,
              private translate: TranslateService,
              private dialog: MatDialog,
              private datePipe: DatePipe,
              public entityId: string = null,
              private route: ActivatedRoute) {
    super();

    this.entityType = EntityType.RETAINED_MESSAGE;
    this.entityComponent = null;
    this.headerComponent = RetainedMessagesTableHeaderComponent;
    this.useTimePageLink = true;
    this.forAllTimeEnabled = true;
    this.defaultTimewindowInterval = forAllTimeInterval();

    this.detailsPanelEnabled = false;
    this.entityTranslations = entityTypeTranslations.get(EntityType.RETAINED_MESSAGE);
    this.entityResources = entityTypeResources.get(EntityType.RETAINED_MESSAGE);
    this.tableTitle = this.translate.instant('retained-message.retained-messages');
    this.entitiesDeleteEnabled = false;
    this.addEnabled = false;
    this.defaultCursor = true;

    this.entityTitle = (message) => message ? message.topic : '';
    this.detailsReadonly = () => true;

    this.groupActionDescriptors = this.configureGroupActions();
    this.cellActionDescriptors = this.configureCellActions();

    this.headerActionDescriptors.push({
      name: this.translate.instant('retained-message.clear-empty-retained-message-nodes'),
      icon: 'delete_forever',
      isEnabled: () => true,
      onAction: ($event) => {
        this.clearEmptyRetainedMsgNodes($event);
      }
    });

    this.columns.push(
      new DateEntityTableColumn<RetainedMessage>('createdTime', 'common.created-time', this.datePipe, '150px'),
      new EntityTableColumn<RetainedMessage>('topic', 'retained-message.topic', '50%',
        undefined, () => undefined,
        true, () => ({}), () => undefined, false,
        {
          name: this.translate.instant('action.copy'),
          nameFunction: (entity) => this.translate.instant('action.copy') + ' ' + entity.topic,
          icon: 'content_copy',
          style: {
            padding: '0px',
            'font-size': '16px',
            'line-height': '16px',
            height: '16px',
            color: 'rgba(0,0,0,.87)'
          },
          isEnabled: (entity) => !!entity.topic?.length,
          onAction: ($event, entity) => entity.topic,
          type: CellActionDescriptorType.COPY_BUTTON
        }),
      new EntityTableColumn<RetainedMessage>('qos', 'retained-message.qos', '50%',
        (entity) => this.translate.instant(WsQoSTranslationMap.get(entity.qos))
      )
    );

    this.entitiesFetchFunction = pageLink => this.fetchRetainedMessages(pageLink as TimePageLink);
  }

  private fetchRetainedMessages(pageLink: TimePageLink): Observable<PageData<RetainedMessage>> {
    const routerQueryParams: RetainedMessagesFilterConfig = this.route.snapshot.queryParams;
    if (routerQueryParams) {
      const queryParams = deepClone(routerQueryParams);
      if (routerQueryParams?.topicName) {
        this.retainedMessagesFilterConfig.topicName = routerQueryParams?.topicName;
        delete queryParams.topicName;
      }
      if (routerQueryParams?.payload) {
        this.retainedMessagesFilterConfig.payload = routerQueryParams?.payload;
        delete queryParams.payload;
      }
      if (routerQueryParams?.qosList) {
        this.retainedMessagesFilterConfig.qosList = routerQueryParams?.qosList;
        delete queryParams.qosList;
      }
    }
    const filter = this.resolveSubscriptionsFilter(this.retainedMessagesFilterConfig);
    const query = new RetainedMessagesQuery(pageLink, filter);
    return this.retainedMsgService.getRetainedMessagesV2(query);
  }

  private resolveSubscriptionsFilter(subscriptionsFilterConfig?: RetainedMessagesFilterConfig): RetainedMessagesFilterConfig {
    const filter: RetainedMessagesFilterConfig = {};
    if (subscriptionsFilterConfig) {
      filter.topicName = subscriptionsFilterConfig.topicName;
      filter.payload = subscriptionsFilterConfig.payload;
      filter.qosList = subscriptionsFilterConfig.qosList;
    }
    return filter;
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
        onAction: ($event, entity) => this.showPayload($event, entity.payload, 'retained-message.show-data', 'mdi:code-braces')
      },
      {
        name: this.translate.instant('retained-message.user-properties'),
        icon: 'mdi:code-brackets',
        isEnabled: (entity) => isDefinedAndNotNull(entity.userProperties),
        onAction: ($event, entity) => this.showPayload($event, JSON.stringify(entity.userProperties), 'retained-message.user-properties', 'mdi:code-brackets')
      },
      {
        name: this.translate.instant('action.delete'),
        icon: 'mdi:trash-can-outline',
        isEnabled: () => true,
        onAction: ($event, entity) => this.deleteMessage($event, entity)
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
              this.getTable().updateData();
            }
          );
        }
      }
    );
  }

  private deleteMessage($event: Event, entity: RetainedMessage) {
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
            this.getTable().updateData();
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
            this.getTable().updateData();
          }
        );
      }
    });
  }

  private showPayload($event: MouseEvent, content: string, title: string, icon: string): void {
    if ($event) {
      $event.stopPropagation();
    }
    this.dialog.open<EventContentDialogV2Component, EventContentDialogV2ComponentDialogData>(EventContentDialogV2Component, {
      disableClose: true,
      panelClass: ['tb-dialog', 'tb-fullscreen-dialog'],
      data: {
        content,
        title,
        icon,
      }
    });
  }
}
