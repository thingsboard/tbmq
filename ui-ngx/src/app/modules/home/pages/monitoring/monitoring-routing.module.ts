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

import { inject, NgModule } from '@angular/core';
import { ResolveFn, RouterModule, Routes } from '@angular/router';
import { Authority } from '@shared/models/authority.enum';
import { of } from 'rxjs';
import { ConfigService } from '@core/http/config.service';
import { TOTAL_KEY } from '@shared/models/chart.model';
import { mergeMap } from 'rxjs/operators';
import { ResourceUsageTableConfigResolver } from '@home/pages/resource-usage/resource-usage-table-config.resolver';

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
    loadComponent: () => import('@home/components/router-tabs.component').then(m => m.RouterTabsComponent),
    data: {
      auth: [Authority.SYS_ADMIN],
      breadcrumb: {
        label: 'monitoring.monitoring',
        icon: 'mdi:monitor-dashboard'
      }
    },
    children: [
      {
        path: '',
        children: [],
        data: {
          auth: [Authority.SYS_ADMIN],
          redirectTo: {
            SYS_ADMIN: '/monitoring/state-health'
          }
        }
      },
      {
        path: 'state-health',
        loadComponent: () => import('@home/pages/monitoring/monitoring-state-health.component').then(m => m.MonitoringStateHealthComponent),
        data: {
          auth: [Authority.SYS_ADMIN],
          title: 'monitoring.state-health',
          breadcrumb: {
            label: 'monitoring.state-health',
            icon: 'insert_chart'
          }
        },
        resolve: {
          brokerIds: BrokerIdsResolver
        }
      },
      {
        path: 'traffic-performance',
        loadComponent: () => import('@home/pages/monitoring/monitoring-traffic-performance.component').then(m => m.MonitoringTrafficPerformanceComponent),
        data: {
          auth: [Authority.SYS_ADMIN],
          title: 'monitoring.traffic-performance',
          breadcrumb: {
            label: 'monitoring.traffic-performance',
            icon: 'speed'
          }
        },
        resolve: {
          brokerIds: BrokerIdsResolver
        }
      },
      {
        path: 'resource-usage',
        loadComponent: () => import('@home/components/entity/entities-table.component').then(m => m.EntitiesTableComponent),
        data: {
          auth: [Authority.SYS_ADMIN],
          title: 'monitoring.resource-usage.resource-usage',
          breadcrumb: {
            label: 'monitoring.resource-usage.resource-usage',
            icon: 'memory'
          }
        },
        resolve: {
          entitiesTableConfig: ResourceUsageTableConfigResolver
        }
      },
    ]
  }
];

@NgModule({
  imports: [RouterModule.forChild(routes)],
  exports: [RouterModule],
  providers: [
    ResourceUsageTableConfigResolver
  ]
})

export class MonitoringRoutingModule {
}
