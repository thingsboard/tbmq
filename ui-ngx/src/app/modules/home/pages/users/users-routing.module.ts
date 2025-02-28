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
import { ConfirmOnExitGuard } from '@core/guards/confirm-on-exit.guard';
import { EntityDetailsPageComponent } from '@home/components/entity/entity-details-page.component';
import { BreadCrumbConfig } from '@shared/components/breadcrumb';
import { entityDetailsPageBreadcrumbLabelFunction } from '@home/pages/home-pages.models';
import { UsersTableConfigResolver } from '@home/pages/users/users-table-config.resolver';

const routes: Routes = [
  {
    path: 'users',
    data: {
      auth: [Authority.SYS_ADMIN],
      breadcrumb: {
        label: 'user.users',
        icon: 'mdi:account-multiple'
      }
    },
    children: [
      {
        path: '',
        loadComponent: () => import('@home/components/entity/entities-table.component').then(m => m.EntitiesTableComponent),
        data: {
          auth: [Authority.SYS_ADMIN],
          title: 'user.users'
        },
        resolve: {
          entitiesTableConfig: UsersTableConfigResolver
        }
      },
      {
        path: ':entityId',
        loadComponent: () => import('@home/components/entity/entity-details-page.component').then(m => m.EntityDetailsPageComponent),
        canDeactivate: [ConfirmOnExitGuard],
        data: {
          breadcrumb: {
            labelFunction: entityDetailsPageBreadcrumbLabelFunction,
            icon: 'mdi:account-multiple'
          } as BreadCrumbConfig<EntityDetailsPageComponent>,
          auth: [Authority.SYS_ADMIN],
          title: 'user.user',
        },
        resolve: {
          entitiesTableConfig: UsersTableConfigResolver
        }
      }
    ]
  }
];

@NgModule({
  imports: [RouterModule.forChild(routes)],
  exports: [RouterModule],
  providers: [
    UsersTableConfigResolver
  ]
})

export class UsersRoutingModule {
}
