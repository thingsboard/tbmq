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

import { Component, Input, OnInit, ViewChild } from '@angular/core';
import { SessionsTableConfig } from "@home/pages/sessions/sessions-table-config";
import { EntitiesTableComponent } from "@home/components/entity/entities-table.component";
import { MqttClientSessionService } from "@core/http/mqtt-client-session.service";
import { TranslateService } from "@ngx-translate/core";
import { DatePipe } from "@angular/common";
import { MatDialog } from "@angular/material/dialog";

@Component({
  selector: 'tb-sessions-table',
  templateUrl: './sessions-table.component.html',
  styleUrls: ['./sessions-table.component.scss']
})
export class SessionsTableComponent implements OnInit {

  @Input()
  detailsMode: boolean;

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
    if (this.sessionsTableConfig && this.sessionsTableConfig.entityId !== entityId) {
      this.sessionsTableConfig.entityId = entityId;
      this.entitiesTable.resetSortAndFilter(this.activeValue);
      if (!this.activeValue) {
        this.dirtyValue = true;
      }
    }
  }

  @ViewChild(EntitiesTableComponent, {static: true}) entitiesTable: EntitiesTableComponent;

  sessionsTableConfig: SessionsTableConfig

  constructor(private mqttClientSessionService: MqttClientSessionService,
              private translate: TranslateService,
              private datePipe: DatePipe,
              private dialog: MatDialog) {
  }

  ngOnInit(): void {
    this.dirtyValue = !this.activeValue;
    this.sessionsTableConfig = new SessionsTableConfig(
      this.mqttClientSessionService,
      this.translate,
      this.datePipe,
      this.dialog,
      this.entityIdValue
    );
  }

}
