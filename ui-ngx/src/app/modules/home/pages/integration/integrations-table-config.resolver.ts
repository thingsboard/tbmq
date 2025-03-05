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

import { DestroyRef, Injectable } from '@angular/core';

import { ActivatedRouteSnapshot, Router } from '@angular/router';
import { EntityTableConfig } from '@home/models/entity/entities-table-config.models';
import { TranslateService } from '@ngx-translate/core';
import { DatePipe } from '@angular/common';
import { Integration, IntegrationInfo } from '@shared/models/integration.models';
import { IntegrationService } from '@core/http/integration.service';
import { UtilsService } from '@core/services/utils.service';
import { DialogService } from '@core/services/dialog.service';
import { MatDialog } from '@angular/material/dialog';
import { IntegrationsTableConfig } from '@home/pages/integration/integrations-table-config';
import { PageLink } from '@shared/models/page/page-link';

@Injectable()
export class IntegrationsTableConfigResolver  {

  constructor(private integrationService: IntegrationService,
              private translate: TranslateService,
              private datePipe: DatePipe,
              private router: Router,
              private utils: UtilsService,
              private dialogService: DialogService,
              private dialog: MatDialog) {
  }

  resolve(route: ActivatedRouteSnapshot): EntityTableConfig<Integration, PageLink, IntegrationInfo> {
    return this.resolveIntegrationsTableConfig();
  }

  resolveIntegrationsTableConfig(): EntityTableConfig<Integration, PageLink, IntegrationInfo> {
    return new IntegrationsTableConfig(
      this.integrationService,
      this.translate,
      this.datePipe,
      this.router,
      this.utils,
      this.dialogService,
      this.dialog,
    );
  }
}
