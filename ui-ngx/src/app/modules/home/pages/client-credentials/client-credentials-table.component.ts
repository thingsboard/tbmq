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
import { DatePipe } from '@angular/common';
import { ActivatedRoute, Router } from '@angular/router';
import {
  ClientCredentialsTableConfig
} from '@home/pages/client-credentials/client-credentials-table-config';
import { Store } from '@ngrx/store';
import { AppState } from '@core/core.state';
import { ClientCredentialsService } from '@core/http/client-credentials.service';
import { MatDialog } from "@angular/material/dialog";

@Component({
  selector: 'tb-client-credentials-table',
  templateUrl: './client-credentials-table.component.html'
})
export class ClientCredentialsTableComponent implements OnInit {

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
    if (this.clientCredentialsTableConfig && this.clientCredentialsTableConfig.entityId !== entityId) {
      this.clientCredentialsTableConfig.entityId = entityId;
      this.entitiesTable.resetSortAndFilter(this.activeValue);
      if (!this.activeValue) {
        this.dirtyValue = true;
      }
    }
  }

  @ViewChild(EntitiesTableComponent, {static: true}) entitiesTable: EntitiesTableComponent;

  clientCredentialsTableConfig: ClientCredentialsTableConfig;

  constructor(private store: Store<AppState>,
              private clientCredentialsService: ClientCredentialsService,
              private translate: TranslateService,
              private datePipe: DatePipe,
              private route: ActivatedRoute,
              private router: Router,
              private dialog: MatDialog) {
  }

  ngOnInit(): void {
    this.dirtyValue = !this.activeValue;
    this.clientCredentialsTableConfig = new ClientCredentialsTableConfig(
      this.store,
      this.clientCredentialsService,
      this.translate,
      this.datePipe,
      this.entityIdValue,
      this.route,
      this.router,
      this.dialog
    );
  }

}
