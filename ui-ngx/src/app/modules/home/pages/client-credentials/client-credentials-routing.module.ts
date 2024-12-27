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

import { NgModule } from '@angular/core';
import { RouterModule, Routes } from '@angular/router';
import { Authority } from '@shared/models/authority.enum';
import { EntityDetailsPageComponent } from '@home/components/entity/entity-details-page.component';
import { ConfirmOnExitGuard } from '@core/guards/confirm-on-exit.guard';
import { entityDetailsPageBreadcrumbLabelFunction } from '@home/pages/home-pages.models';
import { BreadCrumbConfig } from '@shared/components/breadcrumb';
import {
  ClientCredentialsTableConfigResolver
} from '@home/pages/client-credentials/client-credentials-table-config.resolver';

const routes: Routes = [
  {
    path: 'client-credentials',
    data: {
      auth: [Authority.SYS_ADMIN],
      breadcrumb: {
        label: 'mqtt-client-credentials.client-credentials',
        icon: 'mdi:shield-lock'
      },
      isPage: true
    },
    children: [
      {
        path: '',
        loadComponent: () => import('@home/components/entity/entities-table.component').then(m => m.EntitiesTableComponent),
        data: {
          auth: [Authority.SYS_ADMIN],
          title: 'mqtt-client-credentials.client-credentials'
        },
        resolve: {
          entitiesTableConfig: ClientCredentialsTableConfigResolver
        }
      },
      {
        path: ':entityId',
        loadComponent: () => import('@home/components/entity/entity-details-page.component').then(m => m.EntityDetailsPageComponent),
        canDeactivate: [ConfirmOnExitGuard],
        data: {
          breadcrumb: {
            labelFunction: entityDetailsPageBreadcrumbLabelFunction,
            icon: 'mdi:shield-lock'
          } as BreadCrumbConfig<EntityDetailsPageComponent>,
          auth: [Authority.SYS_ADMIN],
          title: 'mqtt-client-credentials.client-credentials',
        },
        resolve: {
          entitiesTableConfig: ClientCredentialsTableConfigResolver
        }
      }
    ]
  }
];

@NgModule({
  imports: [RouterModule.forChild(routes)],
  exports: [RouterModule],
  providers: [
    ClientCredentialsTableConfigResolver
  ]
})

export class ClientCredentialsRoutingModule {
}
