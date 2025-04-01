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

import { NgModule } from '@angular/core';
import { RouterModule, Routes } from '@angular/router';
import { Authority } from '@shared/models/authority.enum';
import { EntityDetailsPageComponent } from '@home/components/entity/entity-details-page.component';
import { ConfirmOnExitGuard } from '@core/guards/confirm-on-exit.guard';
import { entityDetailsPageBreadcrumbLabelFunction } from '@home/pages/home-pages.models';
import { BreadCrumbConfig } from '@shared/components/breadcrumb';
import {
  SharedSubscriptionsTableConfigResolver
} from '@home/pages/shared-subscription-applications/shared-subscriptions-table-config.resolver';

const routes: Routes = [
  {
    path: 'shared-subscriptions',
    loadComponent: () => import('@home/components/router-tabs.component').then(m => m.RouterTabsComponent),
    data: {
      auth: [Authority.SYS_ADMIN],
      breadcrumb: {
        label: 'shared-subscription.shared-subscriptions',
        icon: 'mediation'
      }
    },
    children: [
      {
        path: '',
        children: [],
        data: {
          auth: [Authority.SYS_ADMIN],
          redirectTo: {
            SYS_ADMIN: '/shared-subscriptions/manage'
          }
        }
      },
      {
        path: 'manage',
        loadComponent: () => import('@home/pages/shared-subscription-groups/shared-subsription-groups-table.component').then(m => m.SharedSubsriptionGroupsTableComponent),
        data: {
          auth: [Authority.SYS_ADMIN],
          title: 'shared-subscription.groups',
          breadcrumb: {
            label: 'shared-subscription.groups',
            icon: 'lan'
          }
        }
      },
      {
        path: 'applications',
        data: {
          auth: [Authority.SYS_ADMIN],
          breadcrumb: {
            label: 'shared-subscription.application-shared-subscriptions',
            icon: 'mdi:monitor-share'
          }
        },
        children: [
          {
            path: '',
            loadComponent: () => import('@home/components/entity/entities-table.component').then(m => m.EntitiesTableComponent),
            data: {
              auth: [Authority.SYS_ADMIN],
              title: 'shared-subscription.application-shared-subscriptions'
            },
            resolve: {
              entitiesTableConfig: SharedSubscriptionsTableConfigResolver
            }
          },
          {
            path: ':entityId',
            loadComponent: () => import('@home/components/entity/entity-details-page.component').then(m => m.EntityDetailsPageComponent),
            canDeactivate: [ConfirmOnExitGuard],
            data: {
              breadcrumb: {
                labelFunction: entityDetailsPageBreadcrumbLabelFunction,
                icon: 'mdi:monitor-share'
              } as BreadCrumbConfig<EntityDetailsPageComponent>,
              auth: [Authority.SYS_ADMIN],
              title: 'shared-subscription.application-shared-subscriptions',
            },
            resolve: {
              entitiesTableConfig: SharedSubscriptionsTableConfigResolver
            }
          }
        ]
      }
    ]
  }
];

@NgModule({
  imports: [RouterModule.forChild(routes)],
  exports: [RouterModule],
  providers: [
    SharedSubscriptionsTableConfigResolver
  ]
})

export class SharedSubscriptionsRoutingModule { }
