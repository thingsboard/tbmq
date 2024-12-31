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

import { Component, OnDestroy, OnInit, Renderer2, ViewContainerRef } from '@angular/core';
import { UntypedFormBuilder } from '@angular/forms';
import { Subject } from 'rxjs';
import { MqttJsClientService } from '@core/http/mqtt-js-client.service';
import { ConnectionStatus, ConnectionStatusTranslationMap, WebSocketConnection } from '@shared/models/ws-client.model';
import { TbPopoverService } from '@shared/components/popover.service';
import { ShowConnectionLogsPopoverComponent } from '@home/pages/ws-client/connections/show-connection-logs-popover.component';
import { WebSocketConnectionService } from '@core/http/ws-connection.service';
import { isDefinedAndNotNull } from '@core/utils';

@Component({
  selector: 'tb-connection-controller',
  templateUrl: './connection-controller.component.html',
  styleUrls: ['./connection-controller.component.scss']
})
export class ConnectionControllerComponent implements OnInit, OnDestroy {

  isConnected: boolean;
  isPasswordRequired: boolean;
  isPasswordVisible: boolean;
  password: string;
  errorMessage: string;
  actionLabel: string = 'ws-client.connections.connect';
  reconnecting: boolean;
  connecting: boolean;

  private connection: WebSocketConnection;
  private status: ConnectionStatus;
  private destroy$ = new Subject<void>();
  private timeout;

  constructor(private mqttJsClientService: MqttJsClientService,
              private webSocketConnectionService: WebSocketConnectionService,
              public fb: UntypedFormBuilder,
              private popoverService: TbPopoverService,
              private renderer: Renderer2,
              private viewContainerRef: ViewContainerRef) {
  }

  ngOnInit() {
    this.mqttJsClientService.connectionStatus$.subscribe(
      statusObject => {
        const status = statusObject?.status;
        const details = statusObject?.details?.trim();
        this.updateLabels(status, details);
      }
    );

    this.mqttJsClientService.connection$.subscribe(
      entity => {
        if (entity) {
          this.reconnecting = false;
          this.connection = entity;
          this.isPasswordRequired = entity.configuration.passwordRequired;
          this.resetPassword();
        }
      }
    );

    this.mqttJsClientService.clientConnecting.subscribe(
      () => {
        this.connecting = true;
      }
    );
  }

  ngOnDestroy() {
    this.destroy$.complete();
  }

  onClick() {
    if (this.isConnected) {
      this.disconnect();
    } else {
      this.connect();
    }
  }

  showStatusLogs($event: Event, element: HTMLElement) {
    if ($event) {
      $event.stopPropagation();
    }
    this.timeout = setTimeout(() => {
      const trigger = element;
      if (this.popoverService.hasPopover(trigger)) {
        this.popoverService.hidePopover(trigger);
      } else {
        const showNotificationPopover = this.popoverService.displayPopover(trigger, this.renderer,
          this.viewContainerRef, ShowConnectionLogsPopoverComponent, 'bottom', true, null,
          {
            onClose: () => {
              showNotificationPopover.hide();
            },
            connectionId: this.connection?.id
          },
          {maxHeight: '90vh', height: '324px', padding: '10px'},
          {width: '500px', minWidth: '100%', maxWidth: '100%'},
          {height: '100%', flexDirection: 'column', boxSizing: 'border-box', display: 'flex', margin: '0 -16px'}, false);
        showNotificationPopover.tbComponentRef.instance.popoverComponent = showNotificationPopover;
      }
    }, 200);
  }

  clearTimeout() {
    clearTimeout(this.timeout);
  }

  private updateLabels(status: ConnectionStatus, error: string) {
    this.status = status;
    if (isDefinedAndNotNull(status) && status !== ConnectionStatus.RECONNECTING) {
      this.connecting = false;
    }
    this.isConnected = status === ConnectionStatus.CONNECTED;
    this.actionLabel = this.isConnected ? 'ws-client.connections.disconnect' : 'ws-client.connections.connect';
    this.reconnecting = status === ConnectionStatus.RECONNECTING;
    this.errorMessage = this.getErrorMessage(status, error);
    if (status === ConnectionStatus.CONNECTED) {
      this.resetPassword();
    }
  }

  private getErrorMessage(status: ConnectionStatus, error: string): string {
    if (status === ConnectionStatus.CONNECTION_FAILED || status === ConnectionStatus.DISCONNECTED)  {
      return error?.length ? (': ' + error) : null;
    }
    return null;
  }

  private resetPassword() {
    this.password = null;
  }

  connect() {
    const password = this.isPasswordRequired ? this.password : null;
    this.webSocketConnectionService.getWebSocketConnectionById(this.connection.id).subscribe(
      connection => {
        this.mqttJsClientService.connectClient(connection, password);
      }
    );
  }

  disconnect() {
    this.mqttJsClientService.disconnectClient();
  }

  getStatus() {
    return ConnectionStatusTranslationMap.get(this.status) || 'ws-client.connections.disconnected';
  }

  setStyle() {
    switch (this.status) {
      case ConnectionStatus.CONNECTED:
        return {
          color: '#198038',
          backgroundColor: 'rgba(25, 128, 56, 0.06)'
        };
      case ConnectionStatus.CONNECTING:
      case ConnectionStatus.RECONNECTING:
        return {
          color: '#FAA405',
          backgroundColor: 'rgba(209, 39, 48, 0.06)'
        };
      case ConnectionStatus.CONNECTION_FAILED:
        return {
          color: '#D12730',
          backgroundColor: 'rgba(209, 39, 48, 0.06)'
        };
      case ConnectionStatus.DISCONNECTED:
      default:
        return {
          color: 'rgba(0, 0, 0, 0.54)',
          backgroundColor: 'rgba(0, 0, 0, 0.06)'
        };
    }
  }
}
