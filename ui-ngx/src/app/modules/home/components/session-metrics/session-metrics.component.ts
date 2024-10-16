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

@Component({
  selector: 'tb-session-metrics',
  templateUrl: './session-metrics.component.html',
  styleUrls: ['./session-metrics.component.scss']
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
