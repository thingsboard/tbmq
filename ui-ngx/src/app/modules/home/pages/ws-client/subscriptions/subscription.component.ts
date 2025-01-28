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

import { Component, input, output, viewChild } from '@angular/core';
import { CellActionDescriptor } from '@home/models/entity/entities-table-config.models';
import { TranslateService, TranslateModule } from '@ngx-translate/core';
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
import { ClipboardService } from 'ngx-clipboard';
import { MatMenuTrigger, MatMenu, MatMenuItem } from '@angular/material/menu';
import { ActionNotificationShow } from '@core/notification/notification.actions';
import { Store } from '@ngrx/store';
import { AppState } from '@core/core.state';
import { MatTooltip } from '@angular/material/tooltip';
import { MatIconButton } from '@angular/material/button';
import { MatIcon } from '@angular/material/icon';
import { TbIconComponent } from '@shared/components/icon.component';

@Component({
    selector: 'tb-subscription',
    templateUrl: './subscription.component.html',
    styleUrls: ['./subscription.component.scss'],
    imports: [MatTooltip, MatIconButton, MatIcon, MatMenuTrigger, MatMenu, MatMenuItem, TbIconComponent, TranslateModule]
})
export class SubscriptionComponent {

  readonly subscription = input<WebSocketSubscription>();
  readonly subscriptions = input<WebSocketSubscription[]>();
  readonly connection = input<WebSocketConnection>();

  readonly subscriptionUpdated = output<void>();

  readonly trigger = viewChild(MatMenuTrigger);

  showActions = false;
  hiddenActions = this.configureCellHiddenActions();

  constructor(private webSocketSubscriptionService: WebSocketSubscriptionService,
              private mqttJsClientService: MqttJsClientService,
              private clipboardService: ClipboardService,
              private dialog: MatDialog,
              private dialogService: DialogService,
              private store: Store<AppState>,
              private translate: TranslateService) {

  }

  onMouseEnter(): void {
    this.showActions = true;
  }

  onMouseLeave(): void {
    this.showActions = false;
  }

  openSubscriptionDetails($event: Event) {
    this.edit($event, this.subscription());
  }

  private configureCellHiddenActions(): Array<CellActionDescriptor<WebSocketSubscription>> {
    const actions: Array<CellActionDescriptor<WebSocketSubscription>> = [];
    actions.push(
      {
        name: this.translate.instant('subscription.copy-topic'),
        icon: 'mdi:content-copy',
        isEnabled: () => true,
        onAction: ($event, entity) => this.copyContent($event, entity),
        style: {
          color: 'rgba(0,0,0,0.54)'
        }
      },
      {
        name: this.translate.instant('action.edit'),
        icon: 'mdi:pencil',
        isEnabled: () => true,
        onAction: ($event, entity) => this.edit($event, entity),
        style: {
          color: 'rgba(0,0,0,0.54)'
        }
      },
      {
        name: this.translate.instant('action.delete'),
        icon: 'mdi:delete',
        isEnabled: () => true,
        onAction: ($event, entity) => this.delete($event, entity),
        style: {
          color: 'rgba(0,0,0,0.54)'
        }
      }
    );
    return actions;
  }

  private copyContent($event: Event, webSocketSubscription: WebSocketSubscription) {
    if ($event) {
      $event.stopPropagation();
    }
    this.clipboardService.copy(webSocketSubscription.configuration.topicFilter);
    this.store.dispatch(new ActionNotificationShow(
      {
        message: this.translate.instant('action.on-copied'),
        type: 'success',
        duration: 1000,
        verticalPosition: 'top',
        horizontalPosition: 'left'
      })
    );
  }

  private delete($event: Event, webSocketSubscription: WebSocketSubscription) {
    if ($event) {
      $event.stopPropagation();
    }
    this.dialogService.confirm(
      this.translate.instant('subscription.delete-subscription-title', {topic: this.subscription().configuration.topicFilter}),
      this.translate.instant('subscription.delete-subscription-text', {topic: this.subscription().configuration.topicFilter}),
      this.translate.instant('action.no'),
      this.translate.instant('action.yes'),
      true
    ).subscribe((result) => {
      if (result) {
        this.closeMenu();
        this.webSocketSubscriptionService.deleteWebSocketSubscription(webSocketSubscription.id).subscribe(() => {
          this.mqttJsClientService.unsubscribeWebSocketSubscription(webSocketSubscription);
          this.updateData();
        });
      }
    });
  }

  private edit($event: Event, initWebSocketSubscription: WebSocketSubscription) {
    if ($event) {
      $event.stopPropagation();
    }
    this.webSocketSubscriptionService.getWebSocketSubscriptionById(initWebSocketSubscription.id).subscribe(
      subscription =>
        this.dialog.open<SubscriptionDialogComponent, AddWsClientSubscriptionDialogData>(SubscriptionDialogComponent, {
          disableClose: true,
          panelClass: ['tb-dialog', 'tb-fullscreen-dialog'],
          data: {
            mqttVersion: this.connection().configuration.mqttVersion,
            subscriptions: this.subscriptions(),
            subscription
          }
        }).afterClosed()
          .subscribe((webSocketSubscriptionDialogData) => {
            if (isDefinedAndNotNull(webSocketSubscriptionDialogData)) {
              this.closeMenu();
              this.webSocketSubscriptionService.saveWebSocketSubscription(webSocketSubscriptionDialogData).subscribe(
                (currentWebSocketSubscription) => {
                  this.mqttJsClientService.unsubscribeWebSocketSubscription(initWebSocketSubscription, currentWebSocketSubscription);
                  this.mqttJsClientService.subscribeWebSocketSubscription(currentWebSocketSubscription);
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

  private closeMenu() {
    this.trigger().closeMenu();
  }

}
