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
import { WsClientSubscriptionDialogComponent } from '@home/components/client-credentials-templates/ws-client-subscription-dialog.component';

@NgModule({
  declarations: [
    WsClientComponent,
    WsClientMessangerComponent,
    ConnectionsComponent,
    WsClientConnectionDialogComponent,
    WsClientSubscriptionDialogComponent,
    MessagesComponent,
    WsClientTableComponent
  ],
  imports: [
    CommonModule,
    SharedModule,
    HomeComponentsModule,
    WsClientRoutingModule
  ],
  exports: [
    WsClientConnectionDialogComponent,
    WsClientSubscriptionDialogComponent
  ]
})

export class WsClientModule {
}
