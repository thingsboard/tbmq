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

import { ClientCredentialsModule } from '@home/pages/client-credentials/client-credentials.module';
import { ProfileModule } from './profile/profile.module';
import { SettingsModule } from '@home/pages/settings/settings.module';
import { SessionsModule } from '@home/pages/sessions/sessions.module';
import { UsersModule } from '@home/pages/users/users.module';
import { SharedSubscriptionsModule } from '@home/pages/shared-subscription-applications/shared-subscriptions.module';
import { HomeOverviewModule } from '@home/pages/home-overview/home-overview.module';
import { RetainedMessagesModule } from '@home/pages/retained-messages/retained-messages.module';
import { MonitoringModule } from '@home/pages/monitoring/monitoring.module';
import { KafkaManagementModule } from '@home/pages/kafka-management/kafka-management.module';
import {
  SharedSubscriptionGroupsModule
} from "@home/pages/shared-subscription-groups/shared-subscription-groups.module";
import { WsClientModule } from '@home/pages/ws-client/ws-client.module';

@NgModule({
  exports: [
    SettingsModule,
    ProfileModule,
    ClientCredentialsModule,
    SessionsModule,
    UsersModule,
    SharedSubscriptionsModule,
    SharedSubscriptionGroupsModule,
    HomeOverviewModule,
    RetainedMessagesModule,
    MonitoringModule,
    KafkaManagementModule,
    WsClientModule
  ]
})
export class HomePagesModule {
}
