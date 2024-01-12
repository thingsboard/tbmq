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
import { WsClientMessangerComponent } from '@home/pages/ws-client/ws-client-messanger.component';
import { ConnectionsComponent } from '@home/pages/ws-client/connections.component';
import { WsClientConnectionDialogComponent } from '@home/pages/ws-client/ws-client-connection-dialog.component';
import { MessagesComponent } from '@home/pages/ws-client/messages.component';
import { WsClientTableComponent } from '@home/pages/ws-client/ws-client-table.component';
import { WsClientSubscriptionDialogComponent } from '@home/pages/ws-client/ws-client-subscription-dialog.component';
import { ConnectionWizardDialogComponent } from "@home/components/wizard/connection-wizard-dialog.component";
import { WsClientSubscriptionsComponent } from "@home/pages/ws-client/ws-client-subscriptions.component";
import { PropertiesDialogComponent } from '@home/pages/ws-client/properties-dialog.component';
import { ConnectionControllerComponent } from '@home/pages/ws-client/connection-controller.component';
import { ShowSelectConnectionPopoverComponent } from '@home/pages/ws-client/show-select-connection-popover.component';
import { SelectConnectionComponent } from '@home/pages/ws-client/select-connection.component';
import { ConnectionComponent } from '@home/pages/ws-client/connection.component';
import { SubscriptionComponent } from "@home/pages/ws-client/subscriptions/subscription.component";
import { EntitiesTableWsComponent } from "@home/components/entity/entities-table-ws.component";

@NgModule({
  declarations: [
    WsClientComponent,
    WsClientMessangerComponent,
    ConnectionsComponent,
    ConnectionControllerComponent,
    WsClientConnectionDialogComponent,
    WsClientSubscriptionDialogComponent,
    ConnectionWizardDialogComponent,
    MessagesComponent,
    WsClientTableComponent,
    WsClientSubscriptionsComponent,
    PropertiesDialogComponent,
    SelectConnectionComponent,
    ShowSelectConnectionPopoverComponent,
    ConnectionComponent,
    SubscriptionComponent,
    EntitiesTableWsComponent
  ],
  imports: [
    CommonModule,
    SharedModule,
    HomeComponentsModule,
    WsClientRoutingModule
  ],
  exports: [
    WsClientConnectionDialogComponent,
    WsClientSubscriptionDialogComponent,
    ConnectionWizardDialogComponent,
    WsClientTableComponent,
    WsClientSubscriptionsComponent,
    PropertiesDialogComponent,
    ShowSelectConnectionPopoverComponent
  ]
})

export class WsClientModule {
}
