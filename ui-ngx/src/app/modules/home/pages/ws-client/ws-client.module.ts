///
/// Copyright © 2016-2023 The Thingsboard Authors
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
import { CommonModule } from '@angular/common';
import { SharedModule } from '@shared/shared.module';
import { HomeComponentsModule } from '@home/components/home-components.module';
import { WsClientComponent } from '@home/pages/ws-client/ws-client.component';
import { WsClientRoutingModule } from '@home/pages/ws-client/ws-client-routing.module';
import { MessangerComponent } from '@home/pages/ws-client/messages/messanger.component';
import { ConnectionsComponent } from '@home/pages/ws-client/connections/connections.component';
import { MessagesComponent } from '@home/pages/ws-client/messages/messages.component';
import { SubscriptionDialogComponent } from '@home/pages/ws-client/subscriptions/subscription-dialog.component';
import { ConnectionWizardDialogComponent } from "@home/components/wizard/connection-wizard-dialog.component";
import { SubscriptionsComponent } from "@home/pages/ws-client/subscriptions/subscriptions.component";
import { WsPublishMessagePropertiesDialogComponent } from '@home/pages/ws-client/messages/ws-publish-message-properties-dialog.component';
import { ConnectionControllerComponent } from '@home/pages/ws-client/connections/connection-controller.component';
import { ShowSelectConnectionPopoverComponent } from '@home/pages/ws-client/connections/show-select-connection-popover.component';
import { SelectConnectionComponent } from '@home/pages/ws-client/connections/select-connection.component';
import { ConnectionComponent } from '@home/pages/ws-client/connections/connection.component';
import { SubscriptionComponent } from "@home/pages/ws-client/subscriptions/subscription.component";
import { EntitiesTableWsComponent } from "@home/components/entity/entities-table-ws.component";
import { WsJsonObjectEditComponent } from '@home/pages/ws-client/messages/ws-json-object-edit.component';
import { LastWillComponent } from '@home/pages/ws-client/connections/last-will.component';
import { ShowConnectionLogsPopoverComponent } from '@home/pages/ws-client/connections/show-connection-logs-popover.component';
import { MessageFilterConfigComponent } from '@home/pages/ws-client/messages/message-filter-config.component';
import { WsMessagePayloadDialogComponent } from '@home/pages/ws-client/messages/ws-message-payload-dialog.component';
import { WsMessagePropertiesDialogComponent } from '@home/pages/ws-client/messages/ws-message-properties-dialog.component';

@NgModule({
  declarations: [
    WsClientComponent,
    MessangerComponent,
    ConnectionsComponent,
    ConnectionControllerComponent,
    SubscriptionDialogComponent,
    ConnectionWizardDialogComponent,
    MessagesComponent,
    SubscriptionsComponent,
    WsPublishMessagePropertiesDialogComponent,
    SelectConnectionComponent,
    ShowSelectConnectionPopoverComponent,
    ShowConnectionLogsPopoverComponent,
    ConnectionComponent,
    SubscriptionComponent,
    EntitiesTableWsComponent,
    WsJsonObjectEditComponent,
    LastWillComponent,
    MessageFilterConfigComponent,
    WsMessagePayloadDialogComponent,
    WsMessagePropertiesDialogComponent
  ],
  imports: [
    CommonModule,
    SharedModule,
    HomeComponentsModule,
    WsClientRoutingModule
  ],
  exports: [
    SubscriptionDialogComponent,
    ConnectionWizardDialogComponent,
    SubscriptionsComponent,
    WsPublishMessagePropertiesDialogComponent,
    ShowSelectConnectionPopoverComponent,
    ShowConnectionLogsPopoverComponent
  ]
})

export class WsClientModule {
}
