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
import { ClientCredentialsTableConfigResolver } from '@home/pages/client-credentials/client-credentials-table-config.resolver';
import { MqttAuthProviderTableConfigResolver } from '@home/pages/authentication/mqtt-auth-provider-table-config-resolver.service';

const routes: Routes = [
  {
    path: 'authentication',
    loadComponent: () => import('@home/components/router-tabs.component').then(m => m.RouterTabsComponent),
    data: {
      auth: [Authority.SYS_ADMIN],
      breadcrumb: {
        label: 'authentication.authentication',
        icon: 'mdi:shield-lock'
      }
    },
    children: [
      {
        path: '',
        children: [],
        data: {
          auth: [Authority.SYS_ADMIN],
          redirectTo: {
            SYS_ADMIN: '/authentication/client-credentials'
          }
        }
      },
      {
        path: 'client-credentials',
        data: {
          auth: [Authority.SYS_ADMIN],
          breadcrumb: {
            label: 'mqtt-client-credentials.credentials',
            icon: 'mdi:account-lock-outline'
          }
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
            },
          },
          {
            path: ':entityId',
            loadComponent: () => import('@home/components/entity/entity-details-page.component').then(m => m.EntityDetailsPageComponent),
            canDeactivate: [ConfirmOnExitGuard],
            data: {
              breadcrumb: {
                labelFunction: entityDetailsPageBreadcrumbLabelFunction,
                icon: 'mdi:account-lock-outline'
              } as BreadCrumbConfig<EntityDetailsPageComponent>,
              auth: [Authority.SYS_ADMIN],
              title: 'mqtt-client-credentials.client-credentials',
            },
            resolve: {
              entitiesTableConfig: ClientCredentialsTableConfigResolver
            }
          },
        ]
      },
      {
        path: 'providers',
        data: {
          auth: [Authority.SYS_ADMIN],
          breadcrumb: {
            label: 'authentication.providers',
            icon: 'settings'
          }
        },
        children: [
          {
            path: '',
            loadComponent: () => import('@home/components/entity/entities-table.component').then(m => m.EntitiesTableComponent),
            data: {
              auth: [Authority.SYS_ADMIN],
              title: 'authentication.providers'
            },
            resolve: {
              entitiesTableConfig: MqttAuthProviderTableConfigResolver
            },
          },
        ]
      }
    ]
  }
];

@NgModule({
  imports: [RouterModule.forChild(routes)],
  exports: [RouterModule],
  providers: [
    ClientCredentialsTableConfigResolver,
    MqttAuthProviderTableConfigResolver
  ]
})

export class ClientCredentialsRoutingModule {
}
