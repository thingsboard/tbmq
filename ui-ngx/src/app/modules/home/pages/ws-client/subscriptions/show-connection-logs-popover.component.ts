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

import { ChangeDetectorRef, Component, Input, NgZone, OnDestroy, OnInit } from '@angular/core';
import { PageComponent } from '@shared/components/page.component';
import { TbPopoverComponent } from '@shared/components/popover.component';
import { Store } from '@ngrx/store';
import { AppState } from '@core/core.state';
import { BehaviorSubject, Observable, ReplaySubject, Subscription } from 'rxjs';
import { map, share, tap } from 'rxjs/operators';
import { Router } from '@angular/router';
import { StatusLog, WsClientService } from '@core/http/ws-client.service';
import { Connection, ConnectionStatus, ConnectionStatusTranslationMap } from '@shared/models/ws-client.model';
import { ConnectionDialogData, ConnectionWizardDialogComponent } from '@home/components/wizard/connection-wizard-dialog.component';
import { isDefinedAndNotNull } from "@core/utils";
import { MatDialog } from "@angular/material/dialog";

@Component({
  selector: 'tb-show-connection-logs-popover',
  templateUrl: './show-connection-logs-popover.component.html',
  styleUrls: []
})
export class ShowConnectionLogsPopoverComponent extends PageComponent implements OnDestroy, OnInit {

  @Input()
  onClose: () => void;

  @Input()
  connectionId: string;

  @Input()
  popoverComponent: TbPopoverComponent;

  statusLogs: StatusLog[] = [];
  connectionStatus = ConnectionStatus;
  connectionStatusTranslationMap = ConnectionStatusTranslationMap;

  connections$: Observable<Connection[]>;
  loadConnection = true;
  connectionsTotal: number;

  constructor(protected store: Store<AppState>,
              private wsClientService: WsClientService) {
    super(store);
  }

  ngOnInit() {
    // console.log();
    this.statusLogs = this.wsClientService.connectionStatusLogMap.get(this.connectionId);
  }

  ngOnDestroy() {
    super.ngOnDestroy();
    this.onClose();
  }

  close() {
    this.onClose();
  }

  setStyle(status: ConnectionStatus) {
    switch (status) {
      case ConnectionStatus.CONNECTED:
        return {
          color: '#198038',
        }
      case ConnectionStatus.CONNECTING:
      case ConnectionStatus.RECONNECTING:
        return {
          color: '#FAA405',
        }
      case ConnectionStatus.DISCONNECTED:
        return {
          color: 'rgba(0, 0, 0, 0.38)',
        }
      case ConnectionStatus.CONNECTION_FAILED:
        return {
          color: '#D12730',
        }
    }
  }
}
