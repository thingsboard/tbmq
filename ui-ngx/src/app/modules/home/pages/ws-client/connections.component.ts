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

import { Component, OnDestroy, OnInit } from '@angular/core';
import { Store } from '@ngrx/store';
import { AppState } from '@core/core.state';
import { UntypedFormBuilder } from '@angular/forms';
import { TranslateService } from '@ngx-translate/core';
import { Subject } from 'rxjs';
import { isDefinedAndNotNull } from '@core/utils';
import { WsClientService } from '@core/http/ws-client.service';
import {
  WsClientConnectionDialogComponent,
  AddWsClientConnectionDialogData
} from '@home/pages/ws-client/ws-client-connection-dialog.component';
import { MatDialog } from '@angular/material/dialog';
import { WsConnectionsTableConfig } from '@home/pages/ws-client/ws-connections-table-config';

@Component({
  selector: 'tb-ws-client-connections',
  templateUrl: './connections.component.html',
  styleUrls: ['./connections.component.scss']
})
export class ConnectionsComponent implements OnInit, OnDestroy {

  connections: any;
  wsConnectionsTableConfig: WsConnectionsTableConfig;

  private destroy$ = new Subject<void>();

  constructor(protected store: Store<AppState>,
              private translate: TranslateService,
              private wsClientService: WsClientService,
              private dialog: MatDialog,
              public fb: UntypedFormBuilder) {
  }

  ngOnInit() {
    this.wsConnectionsTableConfig = new WsConnectionsTableConfig(this.wsClientService, this.translate, this.dialog);
  }

  ngOnDestroy() {
    this.destroy$.complete();
  }

  addConnection() {
    const data = {
      connection: null
    };
    this.dialog.open<WsClientConnectionDialogComponent, AddWsClientConnectionDialogData>(WsClientConnectionDialogComponent, {
      disableClose: true,
      panelClass: ['tb-dialog', 'tb-fullscreen-dialog'],
      data
    }).afterClosed()
      .subscribe((res) => {
        if (isDefinedAndNotNull(res)) {
          /*this.wsClientService.saveConnection(res).subscribe(
            res => {
              this.wsConnectionsTableConfig.getTable().updateData();
            }
          );*/
        }
      });
  }
}
