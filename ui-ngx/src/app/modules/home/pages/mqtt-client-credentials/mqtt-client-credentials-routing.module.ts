///
/// Copyright © 2016-2020 The Thingsboard Authors
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
import { EntitiesTableComponent } from '@home/components/entity/entities-table.component';
import { MqttClientCredentialsTableConfigResolver } from '@home/pages/mqtt-client-credentials/mqtt-client-credentials-table-config-resolver.service';

const routes: Routes = [
  {
    path: 'clientCredentials',
    data: {
      title: 'mqtt-client-credentials.client-credentials',
      breadcrumb: {
        label: 'mqtt-client-credentials.client-credentials',
        icon: 'mdi:shield-lock'
      }
    },
    children: [
      {
        path: '',
        component: EntitiesTableComponent,
        data: {
          auth: [Authority.SYS_ADMIN, Authority.TENANT_ADMIN],
          title: 'mqtt-client-credentials.client-credentials'
        },
        resolve: {
          entitiesTableConfig: MqttClientCredentialsTableConfigResolver
        }
      }
    ]
  }
];

@NgModule({
  imports: [RouterModule.forChild(routes)],
  exports: [RouterModule],
  providers: [
    MqttClientCredentialsTableConfigResolver
  ]
})

export class MqttClientCredentialsRoutingModule { }
