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

import { Component, EventEmitter, Input, OnInit, Output } from '@angular/core';
import { CellActionDescriptor } from '@home/models/entity/entities-table-config.models';
import { TranslateService } from '@ngx-translate/core';
import { MqttJsClientService } from '@core/http/mqtt-js-client.service';
import { MatDialog } from '@angular/material/dialog';
import { DialogService } from '@core/services/dialog.service';
import { isDefinedAndNotNull } from '@core/utils';
import {
  AddWsClientSubscriptionDialogData,
  SubscriptionDialogComponent
} from '@home/pages/ws-client/subscriptions/subscription-dialog.component';
import { WebSocketConnection, WebSocketSubscription } from '@shared/models/ws-client.model';
import { WebSocketSubscriptionService } from '@core/http/ws-subscription.service';

@Component({
  selector: 'tb-subscription',
  templateUrl: './subscription.component.html',
  styleUrls: ['./subscription.component.scss']
})
export class SubscriptionComponent implements OnInit {

  @Input()
  subscription: WebSocketSubscription;

  @Input()
  subscriptions: WebSocketSubscription[];

  @Input()
  connection: WebSocketConnection;

  @Output()
  subscriptionUpdated = new EventEmitter<void>();

  showActions = false;
  hiddenActions = this.configureCellHiddenActions();

  constructor(private webSocketSubscriptionService: WebSocketSubscriptionService,
              private mqttJsClientService: MqttJsClientService,
              private dialog: MatDialog,
              private dialogService: DialogService,
              private translate: TranslateService) {

  }

  ngOnInit() {
  }

  onCommentMouseEnter(): void {
    this.showActions = true;
  }

  onCommentMouseLeave(): void {
    this.showActions = false;
  }

  private configureCellHiddenActions(): Array<CellActionDescriptor<WebSocketSubscription>> {
    const actions: Array<CellActionDescriptor<WebSocketSubscription>> = [];
    actions.push(
      {
        name: this.translate.instant('action.edit'),
        icon: 'edit',
        isEnabled: () => true,
        onAction: ($event, entity) => this.edit($event, entity)
      },
      {
        name: this.translate.instant('action.delete'),
        icon: 'delete',
        isEnabled: () => true,
        onAction: ($event, entity) => this.delete($event, entity)
      }
    );
    return actions;
  }

  private delete($event: Event, webSocketSubscription: WebSocketSubscription) {
    if ($event) {
      $event.stopPropagation();
    }
    this.dialogService.confirm(
      this.translate.instant('ws-client.subscriptions.delete-subscription-title', {topic: this.subscription.configuration.topicFilter}),
      this.translate.instant('ws-client.subscriptions.delete-subscription-text', {topic: this.subscription.configuration.topicFilter}),
      this.translate.instant('action.no'),
      this.translate.instant('action.yes'),
      true
    ).subscribe((result) => {
      if (result) {
        this.webSocketSubscriptionService.deleteWebSocketSubscription(webSocketSubscription.id).subscribe(() => {
          this.mqttJsClientService.unsubscribeForTopicActiveMqttJsClient(webSocketSubscription);
          this.updateData();
        });
      }
    });
  }

  private edit($event: Event, prevWebSocketSubscription: WebSocketSubscription) {
    if ($event) {
      $event.stopPropagation();
    }
    this.webSocketSubscriptionService.getWebSocketSubscriptionById(prevWebSocketSubscription.id).subscribe(
      subscription =>
        this.dialog.open<SubscriptionDialogComponent, AddWsClientSubscriptionDialogData>(SubscriptionDialogComponent, {
          disableClose: true,
          panelClass: ['tb-dialog', 'tb-fullscreen-dialog'],
          data: {
            mqttVersion: this.connection.configuration.mqttVersion,
            subscriptions: this.subscriptions,
            subscription
          }
        }).afterClosed()
          .subscribe((webSocketSubscriptionData) => {
            if (isDefinedAndNotNull(webSocketSubscriptionData)) {
              this.webSocketSubscriptionService.saveWebSocketSubscription(webSocketSubscriptionData).subscribe(
                (currentWebSocketSubscription) => {
                  this.mqttJsClientService.unsubscribeForTopicActiveMqttJsClient(prevWebSocketSubscription, currentWebSocketSubscription);
                  this.mqttJsClientService.subscribeForTopicActiveMqttJsClient(currentWebSocketSubscription);
                  this.updateData();
                }
              );
            }
          })
    );
  }

  private updateData() {
    this.subscriptionUpdated.emit();
  }

}
