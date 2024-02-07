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

// @ts-nocheck

import { ChangeDetectorRef, Component, OnInit } from '@angular/core';
import { PageComponent } from '@shared/components/page.component';
import { AppState } from '@core/core.state';
import { Store } from '@ngrx/store';
import { MqttJsClientService } from '@core/http/mqtt-js-client.service';
import { MatDialog } from '@angular/material/dialog';
import { ConnectionDialogData, ConnectionWizardDialogComponent } from '@home/components/wizard/connection-wizard-dialog.component';
import { WebSocketConnectionDto } from '@shared/models/ws-client.model';
import { Observable, ReplaySubject } from 'rxjs';
import { map, share, tap } from 'rxjs/operators';
import { WebSocketConnectionService } from '@core/http/ws-connection.service';

@Component({
  selector: 'tb-ws-client',
  templateUrl: './ws-client.component.html',
  styleUrls: ['./ws-client.component.scss']
})
export class WsClientComponent extends PageComponent implements OnInit {

  connections: Observable<WebSocketConnectionDto[]>;

  constructor(protected store: Store<AppState>,
              private dialog: MatDialog,
              private mqttJsClientService: MqttJsClientService,
              private webSocketConnectionService: WebSocketConnectionService,
              private cd: ChangeDetectorRef) {
    super(store);
  }

  ngOnInit() {
    this.mqttJsClientService.connectionsUpdated$.subscribe((res) => {
      this.updateData(res);
    });
  }

  private selectFirstConnection(connectionId: string) {
    this.webSocketConnectionService.getWebSocketConnectionById(connectionId).subscribe(
      connection => {
        this.mqttJsClientService.selectConnection(connection);
      }
    );
  }

  addConnection($event: Event) {
    if ($event) {
      $event.stopPropagation();
    }
    this.dialog.open<ConnectionWizardDialogComponent, ConnectionDialogData>(ConnectionWizardDialogComponent, {
      disableClose: true,
      panelClass: ['tb-dialog', 'tb-fullscreen-dialog'],
      data: {
        connectionsTotal: 0
      }
    }).afterClosed()
      .subscribe(() => {
        this.mqttJsClientService.onConnectionsUpdated();
      });
  }

  private updateData(selectFirst: boolean = true) {
    this.connections = this.webSocketConnectionService.getWebSocketConnections().pipe(
      map((res) => {
        if (res.data?.length) {
          if (selectFirst) {
            this.selectFirstConnection(res.data[0].id);
          }
          return res.data;
        }
        return [];
      }),
      share({
        connector: () => new ReplaySubject(1)
      }),
      tap(() => setTimeout(() => this.cd.markForCheck()))
    );
  }
}
