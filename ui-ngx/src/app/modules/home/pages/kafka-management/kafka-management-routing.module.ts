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
import { KafkaTopicsTableComponent } from '@home/pages/kafka-management/kafka-topics-table.component';
import { KafkaConsumerGroupsTableComponent } from '@home/pages/kafka-management/kafka-consumer-groups-table.component';
import { KafkaBrokersTableComponent } from '@home/pages/kafka-management/kafka-brokers-table.component';

const routes: Routes = [
  {
    path: 'kafka',
    data: {
      auth: [Authority.SYS_ADMIN],
      breadcrumb: {
        label: 'kafka.management',
        icon: 'apps'
      }
    },
    children: [
      {
        path: '',
        children: [],
        data: {
          auth: [Authority.SYS_ADMIN],
          redirectTo: {
            SYS_ADMIN: '/kafka/topics'
          }
        }
      },
      {
        path: 'topics',
        component: KafkaTopicsTableComponent,
        data: {
          auth: [Authority.SYS_ADMIN],
          title: 'kafka.topics-title',
          breadcrumb: {
            label: 'kafka.topics-title',
            icon: 'topic'
          }
        }
      },
      {
        path: 'consumer-groups',
        component: KafkaConsumerGroupsTableComponent,
        data: {
          auth: [Authority.SYS_ADMIN],
          title: 'kafka.consumer-groups-title',
          breadcrumb: {
            label: 'kafka.consumer-groups-title',
            icon: 'filter_alt'
          }
        }
      },
      {
        path: 'brokers',
        component: KafkaBrokersTableComponent,
        data: {
          auth: [Authority.SYS_ADMIN],
          title: 'kafka.brokers-title',
          breadcrumb: {
            label: 'kafka.brokers-title',
            icon: 'mdi:server'
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

export class KafkaManagementRoutingModule {
}
