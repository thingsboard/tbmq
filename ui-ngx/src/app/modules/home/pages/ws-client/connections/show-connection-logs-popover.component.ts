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

import { ChangeDetectorRef, Component, Input, OnDestroy, OnInit } from '@angular/core';
import { PageComponent } from '@shared/components/page.component';
import { TbPopoverComponent } from '@shared/components/popover.component';
import { Store } from '@ngrx/store';
import { AppState } from '@core/core.state';
import { MqttJsClientService } from '@core/http/mqtt-js-client.service';
import { ConnectionStatus, ConnectionStatusLog, ConnectionStatusTranslationMap } from '@shared/models/ws-client.model';
import { Subscription } from 'rxjs';
import { TranslateModule } from '@ngx-translate/core';
import { MatIconButton } from '@angular/material/button';
import { MatIcon } from '@angular/material/icon';
import { DatePipe } from '@angular/common';
import { MatTooltip } from '@angular/material/tooltip';

@Component({
    selector: 'tb-show-connection-logs-popover',
    templateUrl: './show-connection-logs-popover.component.html',
    styleUrls: ['./show-connection-logs-popover.component.scss'],
    imports: [TranslateModule, MatIconButton, MatIcon, MatTooltip, DatePipe]
})
export class ShowConnectionLogsPopoverComponent extends PageComponent implements OnDestroy, OnInit {

  @Input()
  onClose: () => void;

  @Input()
  connectionId: string;

  @Input()
  popoverComponent: TbPopoverComponent;

  statusLogs: ConnectionStatusLog[] = [];
  connectionStatusTranslationMap = ConnectionStatusTranslationMap;
  logsSubscription: Subscription;

  constructor(protected store: Store<AppState>,
              private cd: ChangeDetectorRef,
              private mqttJsClientService: MqttJsClientService) {
    super(store);
  }

  ngOnInit() {
    this.updateView();
    this.logsSubscription = this.mqttJsClientService.logsUpdated.subscribe(() => {
      this.updateView();
    });
  }

  ngOnDestroy() {
    this.logsSubscription.unsubscribe();
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
        };
      case ConnectionStatus.CONNECTING:
      case ConnectionStatus.RECONNECTING:
        return {
          color: '#FAA405',
        };
      case ConnectionStatus.DISCONNECTED:
        return {
          color: 'rgba(0, 0, 0, 0.38)',
        };
      case ConnectionStatus.CONNECTION_FAILED:
        return {
          color: '#D12730',
        };
    }
  }

  private updateView() {
    this.statusLogs = this.mqttJsClientService.connectionStatusLogMap.get(this.connectionId) || [];
    this.cd.detectChanges();
  }
}
