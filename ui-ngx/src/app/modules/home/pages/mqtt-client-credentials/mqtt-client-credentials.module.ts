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
import { MqttClientCredentialsComponent } from '@home/pages/mqtt-client-credentials/mqtt-client-credentials.component';
import {
  MqttClientCredentialsRoutingModule
} from '@home/pages/mqtt-client-credentials/mqtt-client-credentials-routing.module';
import {
  ChangeMqttBasicPasswordDialogComponent
} from '@home/pages/mqtt-client-credentials/change-mqtt-basic-password-dialog.component';
import { MqttCredentialsSslComponent } from '@home/components/client-credentials-templates/ssl/ssl.component';
import { MqttCredentialsBasicComponent } from '@home/components/client-credentials-templates/basic/basic.component';
import { AuthRulesComponent } from '@home/components/client-credentials-templates/ssl/auth-rules.component';
import { ClientCredentialsTableHeaderComponent } from '@home/pages/mqtt-client-credentials/client-credentials-table-header.component';
import { ClientCredentialsFilterConfigComponent } from '@home/pages/mqtt-client-credentials/client-credentials-filter-config.component';

@NgModule({
  declarations: [
    MqttClientCredentialsComponent,
    MqttCredentialsSslComponent,
    MqttCredentialsBasicComponent,
    AuthRulesComponent,
    ChangeMqttBasicPasswordDialogComponent,
    ClientCredentialsTableHeaderComponent,
    ClientCredentialsFilterConfigComponent
  ],
  imports: [
    CommonModule,
    SharedModule,
    HomeComponentsModule,
    MqttClientCredentialsRoutingModule
  ]
})

export class MqttClientCredentialsModule {
}
