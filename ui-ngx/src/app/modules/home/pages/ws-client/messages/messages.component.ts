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

import { Component, Input, ViewChild } from '@angular/core';
import { TranslateService } from '@ngx-translate/core';
import { MqttJsClientService } from '@core/http/mqtt-js-client.service';
import { EntitiesTableComponent } from '@home/components/entity/entities-table.component';
import { MessagesTableConfig } from '@home/pages/ws-client/messages/messages-table-config';
import { MatDialog } from '@angular/material/dialog';
import { DatePipe } from '@angular/common';
import { BreakpointObserver } from '@angular/cdk/layout';
import { EntitiesTableWsComponent } from '../../../components/entity/entities-table-ws.component';

@Component({
    selector: 'tb-messages',
    templateUrl: './messages.component.html',
    styleUrls: ['./messages.component.scss'],
    standalone: true,
    imports: [EntitiesTableWsComponent]
})
export class MessagesComponent {
  activeValue = false;
  dirtyValue = false;
  entityIdValue: string;

  @Input()
  set active(active: boolean) {
    if (this.activeValue !== active) {
      this.activeValue = active;
      if (this.activeValue && this.dirtyValue) {
        this.dirtyValue = false;
        this.entitiesTable.updateData();
      }
    }
  }

  @Input()
  set entityId(entityId: string) {
    this.entityIdValue = entityId;
    if (this.messagesTableConfig && this.messagesTableConfig.entityId !== entityId) {
      this.messagesTableConfig.entityId = entityId;
      this.entitiesTable.resetSortAndFilter(this.activeValue);
      if (!this.activeValue) {
        this.dirtyValue = true;
      }
    }
  }

  @ViewChild(EntitiesTableComponent, {static: true}) entitiesTable: EntitiesTableComponent;

  messagesTableConfig: MessagesTableConfig;

  constructor(private mqttJsClientService: MqttJsClientService,
              public dialog: MatDialog,
              public datePipe: DatePipe,
              private breakpointObserver: BreakpointObserver,
              private translate: TranslateService) {
    this.messagesTableConfig = new MessagesTableConfig(
      this.mqttJsClientService,
      this.translate,
      this.dialog,
      this.datePipe,
      this.breakpointObserver,
      this.entityIdValue
    );
  }
}
