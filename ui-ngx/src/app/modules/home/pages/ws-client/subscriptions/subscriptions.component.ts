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

import { Component } from '@angular/core';
import { MqttJsClientService } from '@core/http/mqtt-js-client.service';
import { Connection, WebSocketConnection, WebSocketSubscription } from '@shared/models/ws-client.model';
import { MatDialog } from '@angular/material/dialog';
import { map } from 'rxjs/operators';
import {
  AddWsClientSubscriptionDialogData,
  SubscriptionDialogComponent
} from '@home/pages/ws-client/subscriptions/subscription-dialog.component';
import { isDefinedAndNotNull } from '@core/utils';
import { WebSocketSubscriptionService } from '@core/http/ws-subscription.service';

@Component({
  selector: 'tb-subscriptions',
  templateUrl: './subscriptions.component.html',
  styleUrls: ['./subscriptions.component.scss']
})
export class SubscriptionsComponent {

  subscriptions: WebSocketSubscription[];
  connection: WebSocketConnection;
  loadSubscriptions = false;

  constructor(private dialog: MatDialog,
              private mqttJsClientService: MqttJsClientService,
              private webSocketSubscriptionService: WebSocketSubscriptionService) {
  }

  ngOnInit() {
    this.mqttJsClientService.connectionUpdated$.subscribe(
      connection => {
        if (isDefinedAndNotNull(connection)) {
          this.loadSubscriptions = true;
          if (isDefinedAndNotNull(this.connection)) {
            if (this.connection.id !== connection.id) {
              this.fetchSubcriptions(connection);
              this.connection = connection;
            }
          } else {
            this.fetchSubcriptions(connection);
            this.connection = connection;
          }
        }
      }
    );
  }

  fetchSubcriptions(connection: WebSocketConnection) {
    this.webSocketSubscriptionService.getWebSocketSubscriptions(connection.id)
      .pipe(map(res => {
        this.subscriptions = res;
      }))
      .subscribe();
  }

  addSubscription($event: Event) {
    if ($event) {
      $event.stopPropagation();
    }
    this.dialog.open<SubscriptionDialogComponent, AddWsClientSubscriptionDialogData>(SubscriptionDialogComponent, {
      disableClose: true,
      panelClass: ['tb-dialog', 'tb-fullscreen-dialog'],
      data: {
        subscriptions: this.subscriptions,
        mqttVersion: this.connection.configuration.mqttVersion
      }
    }).afterClosed()
      .subscribe((subscriptionFormValue) => {
        if (isDefinedAndNotNull(subscriptionFormValue)) {
          subscriptionFormValue.webSocketConnectionId = this.connection.id;
          this.webSocketSubscriptionService.saveWebSocketSubscription(subscriptionFormValue).subscribe(
            (webSocketSubscription) => {
              this.mqttJsClientService.subscribeForTopicActiveMqttJsClient(webSocketSubscription);
              this.fetchSubcriptions(this.connection);
            }
          );
        }
      });
  }

  trackById(index: number, item: Connection): string {
    return item.id;
  }
}

