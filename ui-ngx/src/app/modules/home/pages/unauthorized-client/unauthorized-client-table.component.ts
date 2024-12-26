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

import { Component, Input, OnInit, ViewChild } from '@angular/core';
import { EntitiesTableComponent } from '@home/components/entity/entities-table.component';
import { TranslateService } from '@ngx-translate/core';
import { DatePipe, NgClass } from '@angular/common';
import { DialogService } from '@core/services/dialog.service';
import { MatDialog } from '@angular/material/dialog';
import { UnauthorizedClientTableConfig } from '@home/pages/unauthorized-client/unauthorized-client-table-config';
import { UnauthorizedClientService } from '@core/http/unauthorized-client.service';
import { ActivatedRoute, Router } from '@angular/router';
import { EntitiesTableComponent as EntitiesTableComponent_1 } from '../../components/entity/entities-table.component';
import { ExtendedModule } from '@angular/flex-layout/extended';

@Component({
    selector: 'tb-unauthorized-client-table',
    templateUrl: './unauthorized-client-table.component.html',
    standalone: true,
    imports: [EntitiesTableComponent_1, NgClass, ExtendedModule]
})
export class UnauthorizedClientTableComponent implements OnInit {

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
    if (this.unauthorizedClientsTableConfig && this.unauthorizedClientsTableConfig.entityId !== entityId) {
      this.unauthorizedClientsTableConfig.entityId = entityId;
      this.entitiesTable.resetSortAndFilter(this.activeValue);
      if (!this.activeValue) {
        this.dirtyValue = true;
      }
    }
  }

  @ViewChild(EntitiesTableComponent, {static: true}) entitiesTable: EntitiesTableComponent;

  unauthorizedClientsTableConfig: UnauthorizedClientTableConfig;

  constructor(private dialogService: DialogService,
              private unauthorizedClientService: UnauthorizedClientService,
              private translate: TranslateService,
              private dialog: MatDialog,
              private route: ActivatedRoute,
              private router: Router,
              private datePipe: DatePipe) {
  }

  ngOnInit(): void {
    this.dirtyValue = !this.activeValue;
    this.unauthorizedClientsTableConfig = new UnauthorizedClientTableConfig(
      this.dialogService,
      this.unauthorizedClientService,
      this.translate,
      this.dialog,
      this.datePipe,
      this.entityIdValue,
      this.route,
      this.router
    );
  }

}
