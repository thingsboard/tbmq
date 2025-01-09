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

import { Component, Input } from '@angular/core';
import { Store } from '@ngrx/store';
import { AppState } from '@core/core.state';
import {
  EntityColumn,
  EntityTableColumn
} from '@home/models/entity/entities-table-config.models';
import { DomSanitizer } from '@angular/platform-browser';
import { EntitiesTableHomeNoPagination } from '../entity/entities-table-no-pagination.component';
import { ClientSessionService } from '@core/http/client-session.service';
import {
  DetailedClientSessionInfo,
  SessionMetricsTable,
  SessionMetricsTranslationMap
} from '@shared/models/session.model';
import { FlexModule } from '@angular/flex-layout/flex';
import { MatIconButton } from '@angular/material/button';
import { MatTooltip } from '@angular/material/tooltip';
import { MatIcon } from '@angular/material/icon';
import { MatTable, MatColumnDef, MatHeaderCellDef, MatHeaderCell, MatCellDef, MatCell, MatHeaderRowDef, MatHeaderRow, MatRowDef, MatRow } from '@angular/material/table';
import { MatSort, MatSortHeader } from '@angular/material/sort';
import { NgFor, NgStyle, NgIf, DatePipe } from '@angular/common';
import { ExtendedModule } from '@angular/flex-layout/extended';
import { TranslateModule } from '@ngx-translate/core';

@Component({
    selector: 'tb-session-metrics',
    templateUrl: './session-metrics.component.html',
    styleUrls: ['./session-metrics.component.scss'],
    imports: [FlexModule, MatIconButton, MatTooltip, MatIcon, MatTable, MatSort, NgFor, MatColumnDef, MatHeaderCellDef, MatHeaderCell, MatSortHeader, MatCellDef, MatCell, NgStyle, ExtendedModule, NgIf, TranslateModule, MatHeaderRowDef, MatHeaderRow, MatRowDef, MatRow, DatePipe]
})
export class SessionMetricsComponent extends EntitiesTableHomeNoPagination<SessionMetricsTable> {

  @Input()
  entity: DetailedClientSessionInfo;

  sessionMetricsTranslationMap = SessionMetricsTranslationMap;

  fetchEntities$ = () => this.clientSessionService.getSessionMetrics(this.entity.clientId);

  constructor(protected store: Store<AppState>,
              private clientSessionService: ClientSessionService,
              protected domSanitizer: DomSanitizer) {
    super(domSanitizer);
  }

  getColumns() {
    const columns: Array<EntityColumn<SessionMetricsTable>> = [];
    columns.push(
      new EntityTableColumn<SessionMetricsTable>('ts', 'common.update-time', '170px'),
      new EntityTableColumn<SessionMetricsTable>('key', 'config.key', '50%'),
      new EntityTableColumn<SessionMetricsTable>('value', 'config.value', '30%'),
    );
    return columns;
  }

  deleteSessionMetrics() {
    this.clientSessionService.deleteSessionMetrics(this.entity.clientId).subscribe(() => {
      this.updateData();
    })
  }
}
