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
import { MatDialog } from '@angular/material/dialog';
import { Connection } from '@shared/models/ws-client.model';
import { tap } from 'rxjs/operators';

@Component({
  selector: 'tb-connection-controller',
  templateUrl: './connection-controller.component.html',
  styleUrls: ['./connection-controller.component.scss']
})
export class ConnectionControllerComponent implements OnInit, OnDestroy {

  connection: Connection;

  isConnected: boolean;
  actionLabel: string;
  password: string;
  isPasswordRequired: boolean;

  private destroy$ = new Subject<void>();

  constructor(protected store: Store<AppState>,
              private translate: TranslateService,
              private wsClientService: WsClientService,
              private dialog: MatDialog,
              public fb: UntypedFormBuilder) {
  }

  ngOnInit() {
    this.wsClientService.getConnections().pipe(
      tap(res => {
        if (res.data?.length) {
          const targetConnection = res.data[0];
          this.wsClientService.getConnection(targetConnection.id).subscribe(
            connection => {
              this.wsClientService.selectConnection(connection);
            }
          )
        }
      })
    ).subscribe();

    this.wsClientService.selectedConnection$.subscribe(
      res => {
        this.connection = res;
        this.isConnected = res.connected;
        this.isPasswordRequired = !!res.password?.length;
        this.update(res);
      }
    )
  }

  ngOnDestroy() {
    this.destroy$.complete();
  }

  onAction() {
    if (this.isConnected) {
      this.disconnect();
    }
    const data = {
      connection: null
    };
    /*this.dialog.open<WsClientConnectionDialogComponent, AddWsClientConnectionDialogData>(WsClientConnectionDialogComponent, {
      disableClose: true,
      panelClass: ['tb-dialog', 'tb-fullscreen-dialog'],
      data
    }).afterClosed()
      .subscribe((res) => {
        if (isDefinedAndNotNull(res)) {
          /!*this.wsClientService.saveConnection(res).subscribe(
            res => {
              this.wsConnectionsTableConfig.getTable().updateData();
            }
          );*!/
        }
      });*/
  }

  update(connection) {
    if (this.connection.connected) {
      this.isConnected = true;
      this.actionLabel = 'ws-client.connections.disconnect';
    } else {
      this.isConnected = false;
      this.actionLabel = 'ws-client.connections.connect';
    }
  }

  connect() {

  }

  disconnect() {

  }
}
