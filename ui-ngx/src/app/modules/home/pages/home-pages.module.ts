///
/// Copyright © 2016-2022 The Thingsboard Authors
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

import { MODULES_MAP } from '@shared/public-api';
import { modulesMap } from '../../common/modules-map';
import { MqttSessionsModule } from './mqtt-sessions/mqtt-sessions.module';
import { MqttClientCredentialsModule } from './mqtt-client-credentials/mqtt-client-credentials.module';
import { ProfileModule } from './profile/profile.module';

@NgModule({
  exports: [
    ProfileModule,
    MqttSessionsModule,
    MqttClientCredentialsModule
  ],
  providers: [
    {
      provide: MODULES_MAP,
      useValue: modulesMap
    }
  ]
})
export class HomePagesModule { }
