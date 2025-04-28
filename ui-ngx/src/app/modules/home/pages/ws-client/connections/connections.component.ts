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

import { Component, OnDestroy } from '@angular/core';
import { MqttJsClientService } from '@core/http/mqtt-js-client.service';
import { ConnectionStatus, WebSocketConnection } from '@shared/models/ws-client.model';
import { Observable, Subject } from 'rxjs';
import { ClientSessionService } from '@core/http/client-session.service';
import { MatTooltip } from '@angular/material/tooltip';
import { MatIconButton } from '@angular/material/button';
import { MatIcon } from '@angular/material/icon';
import { SelectConnectionComponent } from './select-connection.component';
import { AsyncPipe } from '@angular/common';
import { TranslateModule } from '@ngx-translate/core';
import { takeUntil } from 'rxjs/operators';

@Component({
    selector: 'tb-connections',
    templateUrl: './connections.component.html',
    styleUrls: ['./connections.component.scss'],
    imports: [MatTooltip, MatIconButton, MatIcon, SelectConnectionComponent, AsyncPipe, TranslateModule]
})
export class ConnectionsComponent implements OnDestroy{

  isConnected: boolean;

  private connectionValue: WebSocketConnection;
  private destroy$ = new Subject<void>();

  connection: Observable<WebSocketConnection> = this.mqttJsClientService.connection$.pipe(takeUntil(this.destroy$));

  constructor(private mqttJsClientService: MqttJsClientService,
              private clientSessionService: ClientSessionService) {
    this.connection.subscribe(connection => {
      this.connectionValue = connection;
      this.updateConnectionStatus();
    });
  }

  ngOnDestroy() {
    this.destroy$.next();
    this.destroy$.complete();
  }

  openSessions($event: Event) {
    this.clientSessionService.openSessionDetailsDialog($event, this.connectionValue.configuration.clientId).subscribe(
      (dialog) => {
        dialog.afterClosed().subscribe();
      }
    );
  }

  updateConnectionStatus() {
    this.mqttJsClientService.connectionStatus$.subscribe(value => this.isConnected = value?.status === ConnectionStatus.CONNECTED);
  }
}
