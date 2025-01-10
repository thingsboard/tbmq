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

import { Component, Input, OnDestroy, OnInit } from '@angular/core';
import { PageComponent } from '@shared/components/page.component';
import { TbPopoverComponent } from '@shared/components/popover.component';
import { Store } from '@ngrx/store';
import { AppState } from '@core/core.state';
import { Observable } from 'rxjs';
import { map } from 'rxjs/operators';
import { ConnectionDialogData, ConnectionWizardDialogComponent } from '@home/components/wizard/connection-wizard-dialog.component';
import { MatDialog } from '@angular/material/dialog';
import { WebSocketConnection, WebSocketConnectionDto } from '@shared/models/ws-client.model';
import { WebSocketConnectionService } from '@core/http/ws-connection.service';
import { MqttJsClientService } from '@core/http/mqtt-js-client.service';
import { TranslateModule } from '@ngx-translate/core';
import { MatIconButton, MatButton } from '@angular/material/button';
import { MatIcon } from '@angular/material/icon';
import { AsyncPipe } from '@angular/common';
import { ConnectionComponent } from './connection.component';
import { FlexModule } from '@angular/flex-layout/flex';

@Component({
    selector: 'tb-show-connections-popover',
    templateUrl: './show-select-connection-popover.component.html',
    styleUrls: [],
    imports: [TranslateModule, MatIconButton, MatIcon, ConnectionComponent, FlexModule, MatButton, AsyncPipe]
})
export class ShowSelectConnectionPopoverComponent extends PageComponent implements OnDestroy, OnInit {

  @Input()
  onClose: () => void;

  @Input()
  popoverComponent: TbPopoverComponent;

  connections$: Observable<WebSocketConnectionDto[]>;
  connectionsTotal: number;

  constructor(protected store: Store<AppState>,
              private webSocketConnectionService: WebSocketConnectionService,
              private mqttJsClientService: MqttJsClientService,
              private dialog: MatDialog) {
    super(store);
  }

  ngOnInit() {
    this.updateData();
  }

  updateData(close = false, connection: WebSocketConnection = null) {
    if (connection) {
      this.connectAndSelect(connection);
    }
    if (close) {
      this.onClose();
    }
    this.connections$ = this.webSocketConnectionService.getWebSocketConnections().pipe(
      map(res => {
        if (res.data?.length) {
          this.connectionsTotal = res.data?.length;
          return res.data;
        }
        return [];
      })
    );
  }

  private connectAndSelect(connection: WebSocketConnection) {
    this.mqttJsClientService.connectClient(connection, connection?.configuration?.password);
    this.mqttJsClientService.selectConnection(connection);
  }

  ngOnDestroy() {
    super.ngOnDestroy();
    this.onClose();
  }

  addConnection($event: Event) {
    if ($event) {
      $event.stopPropagation();
    }
    this.dialog.open<ConnectionWizardDialogComponent, ConnectionDialogData>(ConnectionWizardDialogComponent, {
      disableClose: true,
      panelClass: ['tb-dialog', 'tb-fullscreen-dialog'],
      data: {
        connectionsTotal: this.connectionsTotal
      }
    }).afterClosed()
      .subscribe(connection => {
        if (connection) {
          this.updateData(true, connection);
        } else {
          this.onClose();
        }
      });
  }

  trackById(item: WebSocketConnectionDto): string {
    return item.id;
  }

  close() {
    this.onClose();
  }
}
