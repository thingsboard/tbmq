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

import { Component, EventEmitter, Input, OnInit, Output } from '@angular/core';
import { WebSocketConnection, WebSocketConnectionDto } from '@shared/models/ws-client.model';
import { CellActionDescriptor } from '@home/models/entity/entities-table-config.models';
import { TranslateService } from '@ngx-translate/core';
import { MqttJsClientService } from '@core/http/mqtt-js-client.service';
import { MatDialog } from '@angular/material/dialog';
import { DialogService } from '@core/services/dialog.service';
import { ConnectionDialogData, ConnectionWizardDialogComponent } from '@home/components/wizard/connection-wizard-dialog.component';
import { WebSocketConnectionService } from '@core/http/ws-connection.service';
import { ClientSessionService } from '@core/http/client-session.service';
import { FlexModule } from '@angular/flex-layout/flex';
import { NgClass, NgStyle, NgFor, NgIf } from '@angular/common';
import { ExtendedModule } from '@angular/flex-layout/extended';
import { MatTooltip } from '@angular/material/tooltip';
import { MatIconButton } from '@angular/material/button';
import { TbIconComponent } from '@shared/components/icon.component';

@Component({
    selector: 'tb-connection',
    templateUrl: './connection.component.html',
    styleUrls: ['./connection.component.scss'],
    imports: [FlexModule, NgClass, ExtendedModule, NgStyle, NgFor, MatTooltip, NgIf, MatIconButton, TbIconComponent]
})
export class ConnectionComponent implements OnInit {

  @Input()
  connection: WebSocketConnectionDto;

  @Output()
  connectionUpdated = new EventEmitter<void>();

  selectedConnection: WebSocketConnectionDto;
  showActions = false;
  hiddenActions = this.configureCellHiddenActions();

  constructor(private mqttJsClientService: MqttJsClientService,
              private webSocketConnectionService: WebSocketConnectionService,
              private dialog: MatDialog,
              private dialogService: DialogService,
              private clientSessionService: ClientSessionService,
              private translate: TranslateService) {

  }

  ngOnInit() {
    this.mqttJsClientService.connection$.subscribe(
      res => {
        this.selectedConnection = res;
      }
    );
  }

  onCommentMouseEnter(): void {
    this.showActions = true;
  }

  onCommentMouseLeave(): void {
    this.showActions = false;
  }

  selectConnection() {
    this.webSocketConnectionService.getWebSocketConnectionById(this.connection.id).subscribe(connection => {
      this.mqttJsClientService.selectConnection(connection);
    });
  }

  setStyle(): { [key: string]: any } {
    return {
      background: this.isConnectionConnected() ? '#008A00' : 'rgba(0,0,0,0.2)'
    };
  }

  private isConnectionConnected() {
    return this.mqttJsClientService.isConnectionConnected(this.connection.id);
  }

  private configureCellHiddenActions(): Array<CellActionDescriptor<WebSocketConnectionDto>> {
    const actions: Array<CellActionDescriptor<WebSocketConnectionDto>> = [];
    actions.push(
      {
        name: this.translate.instant('mqtt-client-session.session-details'),
        icon: 'mdi:book-multiple',
        isEnabled: () => this.isConnectionConnected(),
        onAction: ($event, entity) => this.openSessions($event, entity),
        style: {
          paddingTop: '3px'
        }
      },
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
        onAction: ($event) => this.delete($event)
      }
    );
    return actions;
  }

  private delete($event: Event) {
    if ($event) {
      $event.stopPropagation();
    }
    this.dialogService.confirm(
      this.translate.instant('ws-client.connections.delete-connection-title', {connectionName: this.connection.name}),
      this.translate.instant('ws-client.connections.delete-connection-text', {connectionName: this.connection.name}),
      this.translate.instant('action.no'),
      this.translate.instant('action.yes'),
      true
    ).subscribe((result) => {
      if (result) {
        this.webSocketConnectionService.deleteWebSocketConnection(this.connection.id).subscribe(() => {
          this.mqttJsClientService.disconnectClient(this.connection);
          this.connectionUpdated.emit();
          this.mqttJsClientService.onConnectionsUpdated(true);
        });
      }
    });
  }

  private edit($event: Event, entity: WebSocketConnectionDto) {
    if ($event) {
      $event.stopPropagation();
    }
    this.webSocketConnectionService.getWebSocketConnectionById(entity.id).subscribe(connection => {
      this.dialog.open<ConnectionWizardDialogComponent, ConnectionDialogData>(ConnectionWizardDialogComponent, {
        disableClose: true,
        panelClass: ['tb-dialog', 'tb-fullscreen-dialog'],
        data: {
          entity: connection
        }
      }).afterClosed()
        .subscribe((connection) => {
          if (connection) {
            this.connectionUpdated.emit();
            this.connectAndSelect(connection);
          }
        });
    });
  }

  openSessions($event: Event, entity: WebSocketConnectionDto) {
    this.clientSessionService.openSessionDetailsDialog($event, entity.clientId).subscribe(
      (dialog) => {
        dialog.afterClosed().subscribe();
      }
    );
  }

  private connectAndSelect(connection: WebSocketConnection) {
    this.mqttJsClientService.notifyClientConnecting();
    this.mqttJsClientService.disconnectClient(connection);
    setTimeout(() => {
      this.mqttJsClientService.connectClient(connection, connection?.configuration?.password, false);
      this.mqttJsClientService.selectConnection(connection);
    }, 500);
  }
}
