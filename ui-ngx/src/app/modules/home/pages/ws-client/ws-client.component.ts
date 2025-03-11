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

import { Component, OnInit } from '@angular/core';
import { PageComponent } from '@shared/components/page.component';
import { AppState } from '@core/core.state';
import { Store } from '@ngrx/store';
import { MqttJsClientService } from '@core/http/mqtt-js-client.service';
import { MatDialog } from '@angular/material/dialog';
import { ConnectionDialogData, ConnectionWizardDialogComponent } from '@home/components/wizard/connection-wizard-dialog.component';
import { WebSocketConnectionDto } from '@shared/models/ws-client.model';
import { Observable } from 'rxjs';
import { map } from 'rxjs/operators';
import { WebSocketConnectionService } from '@core/http/ws-connection.service';
import { MediaBreakpoints } from '@shared/models/constants';
import { BreakpointObserver } from '@angular/cdk/layout';
import { SettingsService } from '@core/http/settings.service';
import { NgTemplateOutlet, AsyncPipe } from '@angular/common';
import { MatIcon } from '@angular/material/icon';
import { TranslateModule } from '@ngx-translate/core';
import { ConnectionsComponent } from './connections/connections.component';
import { SubscriptionsComponent } from './subscriptions/subscriptions.component';
import { HelpPageComponent } from '@shared/components/help-page.component';
import { ConnectionControllerComponent } from './connections/connection-controller.component';
import { MessangerComponent } from './messages/messanger.component';

@Component({
    selector: 'tb-ws-client',
    templateUrl: './ws-client.component.html',
    styleUrls: ['./ws-client.component.scss'],
    imports: [NgTemplateOutlet, MatIcon, TranslateModule, ConnectionsComponent, SubscriptionsComponent, HelpPageComponent, ConnectionControllerComponent, MessangerComponent, AsyncPipe]
})
export class WsClientComponent extends PageComponent implements OnInit {

  connections: Observable<WebSocketConnectionDto[]>;
  layoutSingleColumn = this.breakpointObserver.observe(MediaBreakpoints['lt-lg']).pipe(map(({matches}) => !!matches));

  private connectConnection = false;

  constructor(protected store: Store<AppState>,
              private dialog: MatDialog,
              private mqttJsClientService: MqttJsClientService,
              private webSocketConnectionService: WebSocketConnectionService,
              private settingsService: SettingsService,
              private breakpointObserver: BreakpointObserver) {
    super(store);
  }

  ngOnInit() {
    this.mqttJsClientService.connections$.subscribe((res) => {
      this.updateData(res);
    });
    this.settingsService.getWebSocketSettings().subscribe(settings => this.mqttJsClientService.setWebSocketSettings(settings.jsonValue));
  }

  private selectFirstConnection(connectionId: string) {
    this.webSocketConnectionService.getWebSocketConnectionById(connectionId).subscribe(
      connection => {
        this.mqttJsClientService.selectConnection(connection);
        if (this.connectConnection) {
          this.mqttJsClientService.connectClient(connection, connection?.configuration?.password);
        }
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
      autoFocus: false,
      data: {
        connectionsTotal: 0
      }
    }).afterClosed()
      .subscribe(() => {
        this.connectConnection = true;
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
      })
    );
  }
}
