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

import { inject, NgModule } from '@angular/core';
import { ResolveFn, RouterModule, Routes } from '@angular/router';
import { Authority } from '@shared/models/authority.enum';
import { MonitoringComponent } from '@home/pages/monitoring/monitoring.component';
import { of } from 'rxjs';
import { ConfigService } from '@core/http/config.service';
import { TOTAL_KEY } from '@shared/models/chart.model';
import { mergeMap } from 'rxjs/operators';

export const BrokerIdsResolver: ResolveFn<string[]> = () =>
  inject(ConfigService).getBrokerServiceIds().pipe(
    mergeMap((brokerIds) => {
      let ids = [TOTAL_KEY];
      ids = brokerIds.length <= 1 ? ids : ids.concat(brokerIds);
      return of(ids);
    })
  );

const routes: Routes = [
  {
    path: 'monitoring',
    component: MonitoringComponent,
    data: {
      auth: [Authority.SYS_ADMIN],
      title: 'monitoring.monitoring',
      breadcrumb: {
        label: 'monitoring.monitoring',
        icon: 'mdi:monitor-dashboard'
      }
    },
    resolve: {
      brokerIds: BrokerIdsResolver
    }
  }
];

@NgModule({
  imports: [RouterModule.forChild(routes)],
  exports: [RouterModule]
})

export class MonitoringRoutingModule {
}
