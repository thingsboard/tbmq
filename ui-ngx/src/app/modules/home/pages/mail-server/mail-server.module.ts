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

import { MailServerRoutingModule } from './mail-server-routing.module';
import { SharedModule } from '@app/shared/shared.module';
import { MailServerComponent } from '@modules/home/pages/mail-server/mail-server.component';
import { HomeComponentsModule } from '@modules/home/components/home-components.module';

@NgModule({
  declarations:
    [
      MailServerComponent
    ],
  imports: [
    CommonModule,
    SharedModule,
    HomeComponentsModule,
    MailServerRoutingModule
  ]
})
export class MailServerModule {
}
