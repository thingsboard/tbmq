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
import { SharedSubsriptionsTableComponent } from '@home/pages/shared-subscription-applications/shared-subsriptions-table.component';
import {
  SharedSubsriptionGroupsTableComponent
} from "@home/pages/shared-subscription-groups/shared-subsription-groups-table.component";

const routes: Routes = [
  {
    path: 'shared-subscriptions',
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
            SYS_ADMIN: '/shared-subscriptions/applications'
          }
        }
      },
      {
        path: 'manage',
        component: SharedSubsriptionGroupsTableComponent,
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
        component: SharedSubsriptionsTableComponent,
        data: {
          auth: [Authority.SYS_ADMIN],
          title: 'shared-subscription.application-shared-subscriptions',
          breadcrumb: {
            label: 'shared-subscription.application-shared-subscriptions',
            icon: 'mdi:monitor-share'
          }
        }
      }
    ]
  }
];

@NgModule({
  imports: [RouterModule.forChild(routes)],
  exports: [RouterModule]
})

export class SharedSubscriptionsRoutingModule { }
