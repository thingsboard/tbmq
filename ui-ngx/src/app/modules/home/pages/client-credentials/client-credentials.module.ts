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
import { CommonModule } from '@angular/common';
import { SharedModule } from '@shared/shared.module';
import { HomeComponentsModule } from '@home/components/home-components.module';
import { ClientCredentialsComponent } from '@home/pages/client-credentials/client-credentials.component';
import {
  ClientCredentialsRoutingModule
} from '@home/pages/client-credentials/client-credentials-routing.module';
import {
  ChangeBasicPasswordDialogComponent
} from '@home/pages/client-credentials/change-basic-password-dialog.component';
import { MqttCredentialsSslComponent } from '@home/components/client-credentials-templates/ssl/ssl.component';
import { MqttCredentialsBasicComponent } from '@home/components/client-credentials-templates/basic/basic.component';
import { AuthRulesComponent } from '@home/components/client-credentials-templates/ssl/auth-rules.component';
import { ClientCredentialsTableHeaderComponent } from '@home/pages/client-credentials/client-credentials-table-header.component';
import { ClientCredentialsFilterConfigComponent } from '@home/pages/client-credentials/client-credentials-filter-config.component';
import { ClientCredentialsTableComponent } from '@home/pages/client-credentials/client-credentials-table.component';
import {
  ClientCredentialsWizardDialogComponent
} from "@home/components/wizard/client-credentials-wizard-dialog.component";
import { CheckConnectivityDialogComponent } from '@home/pages/client-credentials/check-connectivity-dialog.component';

@NgModule({
  declarations: [
    ClientCredentialsComponent,
    MqttCredentialsSslComponent,
    MqttCredentialsBasicComponent,
    AuthRulesComponent,
    ChangeBasicPasswordDialogComponent,
    ClientCredentialsTableHeaderComponent,
    ClientCredentialsFilterConfigComponent,
    ClientCredentialsTableComponent,
    ClientCredentialsWizardDialogComponent,
    CheckConnectivityDialogComponent
  ],
  imports: [
    CommonModule,
    SharedModule,
    HomeComponentsModule,
    ClientCredentialsRoutingModule
  ]
})

export class ClientCredentialsModule {
}
