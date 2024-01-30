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

import { Component, OnDestroy, OnInit, Renderer2, ViewContainerRef } from '@angular/core';
import { Store } from '@ngrx/store';
import { AppState } from '@core/core.state';
import { UntypedFormBuilder } from '@angular/forms';
import { Subject } from 'rxjs';
import { WsClientService } from '@core/http/ws-client.service';
import { Connection, ConnectionStatus, ConnectionStatusTranslationMap } from '@shared/models/ws-client.model';
import { TbPopoverService } from '@shared/components/popover.service';
import { ShowConnectionLogsPopoverComponent } from '@home/pages/ws-client/subscriptions/show-connection-logs-popover.component';

@Component({
  selector: 'tb-connection-controller',
  templateUrl: './connection-controller.component.html',
  styleUrls: ['./connection-controller.component.scss']
})
export class ConnectionControllerComponent implements OnInit, OnDestroy {

  connection: Connection;

  isConnected: boolean;
  isDisabled: boolean;
  actionLabel: string = 'ws-client.connections.connect';
  password: string;
  isPasswordRequired: boolean;
  status: ConnectionStatus;
  connectionStatusTranslationMap = ConnectionStatusTranslationMap;
  errorMessage: string;
  passwordVisible: boolean;

  private destroy$ = new Subject<void>();

  constructor(protected store: Store<AppState>,
              private wsClientService: WsClientService,
              public fb: UntypedFormBuilder,
              private popoverService: TbPopoverService,
              private renderer: Renderer2,
              private viewContainerRef: ViewContainerRef) {
  }

  ngOnInit() {
    this.wsClientService.selectedConnectionStatus$.subscribe(
      status => {
        this.status = status;
        this.updateLabels();
        this.setDisabledState();
      }
    );

    this.wsClientService.selectedConnection$.subscribe(
      entity => {
        this.connection = entity;
        this.updateLabels();
        this.isPasswordRequired = !!entity.password?.length;
      }
    );

    this.wsClientService.selectedConnectionFailedError$.subscribe(
      error => {
        if (error) {
          this.errorMessage = error;
        } else {
          this.errorMessage = null;
        }
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
          connectionId: this.connection.id
        },
        {maxHeight: '90vh', height: '324px', padding: '10px'},
        {width: '500px', minWidth: '100%', maxWidth: '100%'},
        {height: '100%', flexDirection: 'column', boxSizing: 'border-box', display: 'flex', margin: '0 -16px'}, false);
      showNotificationPopover.tbComponentRef.instance.popoverComponent = showNotificationPopover;
    }
  }

  private updateLabels() {
    if (this.status === ConnectionStatus.CONNECTED) {
      this.isConnected = true;
      this.actionLabel = 'ws-client.connections.disconnect';
    } else {
      this.isConnected = false;
      this.actionLabel = 'ws-client.connections.connect';
    }
    if (this.status !== ConnectionStatus.CONNECTION_FAILED) {
      this.errorMessage = null;
    }
  }

  private setDisabledState() {
    this.isDisabled = this.status === ConnectionStatus.CONNECTED || this.status === ConnectionStatus.RECONNECTING;
  }

  connect() {
    this.status = ConnectionStatus.CONNECTING;
    const password = this.isPasswordRequired ? this.password : null;
    this.wsClientService.connectClient(this.connection, password);
  }

  disconnect() {
    this.wsClientService.disconnectClient(true, this.connection);
  }

/*  cancel() {
    this.wsClientService.disconnectClient(true);
  }*/

  displayCancelButton(): boolean {
    return this.isDisabled && (this.status === ConnectionStatus.RECONNECTING || this.status === ConnectionStatus.CONNECTING);
  }

  getStatus() {
    return this.connectionStatusTranslationMap.get(this.status) || 'ws-client.connections.not-connected';
  }

  getStateDetails() {
    return this.errorMessage ? `: ${this.errorMessage}` : null;
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
