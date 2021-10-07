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
import { CommonModule } from '@angular/common';
import { SharedModule } from '@app/shared/shared.module';
import { AssignToCustomerDialogComponent } from '@modules/home/dialogs/assign-to-customer-dialog.component';
import { AddEntitiesToCustomerDialogComponent } from '@modules/home/dialogs/add-entities-to-customer-dialog.component';
import { HomeDialogsService } from './home-dialogs.service';
import { AddEntitiesToEdgeDialogComponent } from '@home/dialogs/add-entities-to-edge-dialog.component';
import { EditMqttClientProfileDialogComponent } from '@home/dialogs/edit-mqtt-client-profile-dialog.component';
import { EditMqttClientCredentialsProfileDialogComponent } from '@home/dialogs/edit-mqtt-client-credentials-profile-dialog.component';
import { ManageCredentialsDialogComponent } from '@home/dialogs/manage-credentials-dialog.component';
import { MqttCredentialsComponent } from '@home/components/mqtt-credentials/mqtt-credentials.component';
import { MqttCredentialsBasicComponent } from '@home/components/mqtt-credentials/basic/basic.component';
import { MqttCredentialsSslComponent } from '@home/components/mqtt-credentials/ssl/ssl.component';
import { AuthRulesComponent } from '@home/components/mqtt-credentials/ssl/auth-rules.component';

@NgModule({
  declarations:
  [
    AssignToCustomerDialogComponent,
    AddEntitiesToCustomerDialogComponent,
    AddEntitiesToEdgeDialogComponent,
    EditMqttClientProfileDialogComponent,
    EditMqttClientCredentialsProfileDialogComponent,
    ManageCredentialsDialogComponent,
    MqttCredentialsComponent,
    MqttCredentialsBasicComponent,
    MqttCredentialsSslComponent,
    AuthRulesComponent
  ],
  imports: [
    CommonModule,
    SharedModule
  ],
  exports: [
    AssignToCustomerDialogComponent,
    AddEntitiesToCustomerDialogComponent,
    AddEntitiesToEdgeDialogComponent,
    EditMqttClientProfileDialogComponent,
    EditMqttClientCredentialsProfileDialogComponent,
    ManageCredentialsDialogComponent,
    MqttCredentialsComponent,
    MqttCredentialsBasicComponent,
    MqttCredentialsSslComponent,
    AuthRulesComponent
  ],
  providers: [
    HomeDialogsService
  ]
})
export class HomeDialogsModule { }
