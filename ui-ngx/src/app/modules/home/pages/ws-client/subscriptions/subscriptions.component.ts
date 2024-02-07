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

import { ChangeDetectorRef, Component } from '@angular/core';
import { WsClientService } from '@core/http/ws-client.service';
import { Connection, WebSocketConnection, WebSocketSubscription, WsSubscription } from '@shared/models/ws-client.model';
import { MatDialog } from '@angular/material/dialog';
import { Observable, ReplaySubject } from 'rxjs';
import { map, share, tap } from 'rxjs/operators';
import {
  AddWsClientSubscriptionDialogData,
  SubscriptionDialogComponent
} from '@home/pages/ws-client/subscriptions/subscription-dialog.component';
import { isDefinedAndNotNull } from '@core/utils';

@Component({
  selector: 'tb-subscriptions',
  templateUrl: './subscriptions.component.html',
  styleUrls: ['./subscriptions.component.scss']
})
export class SubscriptionsComponent {

  subscriptions$: Observable<WebSocketSubscription[]>;
  subscriptions: WebSocketSubscription[];
  connection: WebSocketConnection;
  loadSubscriptions = false;

  constructor(private dialog: MatDialog,
              private wsClientService: WsClientService,
              private cd: ChangeDetectorRef) {
  }

  ngOnInit() {
    this.wsClientService.selectedConnection$.subscribe(
      connection => {
        if (connection) {
          this.loadSubscriptions = true;
          this.connection = connection;
          this.fetchSubcriptions();
        }
      }
    );
  }

  fetchSubcriptions() {
    this.subscriptions$ = this.wsClientService.getWebSocketSubscriptions(this.connection.id).pipe(
      map(res => {
        if (res.length) {
          this.subscriptions = res;
          return res;
        }
        return [];
      }),
      share({
        connector: () => new ReplaySubject(1)
      }),
      tap(() => setTimeout(() => this.cd.markForCheck()))
    );
  }

  addSubscription($event: Event) {
    if ($event) {
      $event.stopPropagation();
    }
    this.dialog.open<SubscriptionDialogComponent, AddWsClientSubscriptionDialogData>(SubscriptionDialogComponent, {
      disableClose: true,
      panelClass: ['tb-dialog', 'tb-fullscreen-dialog'],
      data: {
        mqttVersion: this.connection.configuration.mqttVersion
      }
    }).afterClosed()
      .subscribe(
        (subscription) => {
          if (isDefinedAndNotNull(subscription)) {
            subscription.webSocketConnectionId = this.connection.id;
            this.wsClientService.saveWebSocketSubscription(subscription).subscribe(
              (res) => {
                this.wsClientService.subscribeForTopicActiveMqttJsClient(res);
                this.fetchSubcriptions();
              }
            );
          }
        }
      );
  }

  trackById(index: number, item: Connection): string {
    return item.id;
  }
}

