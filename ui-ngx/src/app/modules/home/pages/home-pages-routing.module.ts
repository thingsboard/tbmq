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
import { AuthenticationRoutingModule } from '@home/pages/authentication/authentication-routing.module';
import { UsersRoutingModule } from '@home/pages/users/users-routing.module';
import { SessionsRoutingModule } from '@home/pages/sessions/sessions-routing.module';
import { SubscriptionsRoutingModule } from '@home/pages/subscriptions/subscriptions-routing.module';
import { RetainedMessagesRoutingModule } from '@home/pages/retained-messages/retained-messages-routing.module';
import { SharedSubscriptionsRoutingModule } from '@home/pages/shared-subscription-applications/shared-subscriptions-routing.module';
import { WsClientRoutingModule } from '@home/pages/ws-client/ws-client-routing.module';
import { AccountRoutingModule } from '@home/pages/account/account-routing.module';
import { SettingsRoutingModule } from '@home/pages/settings/settings-routing.module';
import { HomeOverviewRoutingModule } from '@home/pages/home-overview/home-overview-routing.module';
import { GettingStartedRoutingModule } from '@home/pages/getting-started/getting-started-routing.module';
import { KafkaManagementRoutingModule } from '@home/pages/kafka-management/kafka-management-routing.module';
import { MonitoringRoutingModule } from '@home/pages/monitoring/monitoring-routing.module';
import { IntegrationRoutingModule } from '@home/pages/integration/integration-routing.module';

@NgModule({
  exports: [
    HomeOverviewRoutingModule,
    SettingsRoutingModule,
    KafkaManagementRoutingModule,
    AuthenticationRoutingModule,
    UsersRoutingModule,
    SessionsRoutingModule,
    SubscriptionsRoutingModule,
    RetainedMessagesRoutingModule,
    SharedSubscriptionsRoutingModule,
    WsClientRoutingModule,
    AccountRoutingModule,
    GettingStartedRoutingModule,
    MonitoringRoutingModule,
    IntegrationRoutingModule,
  ]
})
export class HomePagesRoutingModule {
}
