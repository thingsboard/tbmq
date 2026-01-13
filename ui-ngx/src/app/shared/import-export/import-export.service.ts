///
/// Copyright © 2016-2026 The Thingsboard Authors
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

import { Injectable } from '@angular/core';
import { Observable } from 'rxjs';
import { BulkImportRequest, BulkImportResult } from './import-export.models';
import { EntityType } from '@shared/models/entity-type.models';
import { RequestConfig } from '@core/http/http-utils';
import { ClientCredentialsService } from '@core/http/client-credentials.service';

@Injectable()
export class ImportExportService {

  constructor(
    private clientCredentialsService: ClientCredentialsService
  ) {
  }

  public bulkImportEntities(entitiesData: BulkImportRequest, entityType: EntityType, config?: RequestConfig): Observable<BulkImportResult> {
    switch (entityType) {
      case EntityType.MQTT_CLIENT_CREDENTIALS:
        return this.clientCredentialsService.bulkImportCredentials(entitiesData, config);
    }
  }

}
