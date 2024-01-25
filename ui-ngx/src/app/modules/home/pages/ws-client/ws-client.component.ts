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

// @ts-nocheck

import { Component, OnInit } from '@angular/core';
import { PageComponent } from '@shared/components/page.component';
import { AppState } from '@core/core.state';
import { Store } from '@ngrx/store';
import { WsClientService } from '@core/http/ws-client.service';
import { MatDialog } from '@angular/material/dialog';
import { ConnectionDialogData, ConnectionWizardDialogComponent } from '@home/components/wizard/connection-wizard-dialog.component';
import { ConnectionShortInfo } from '@shared/models/ws-client.model';

@Component({
  selector: 'tb-ws-client',
  templateUrl: './ws-client.component.html',
  styleUrls: ['./ws-client.component.scss']
})
export class WsClientComponent extends PageComponent implements OnInit {

  connections: ConnectionShortInfo[] = [];

  constructor(protected store: Store<AppState>,
              private dialog: MatDialog,
              private wsClientService: WsClientService) {
    super(store);
  }

  ngOnInit() {
    this.wsClientService.getConnections().subscribe(data => {
      if (data?.length) {
        this.connections = data;
        this.wsClientService.selectConnection(data[0]);
        this.wsClientService.setConnectionsInitState(data);
      }
    });
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
      .subscribe();
  }
}
