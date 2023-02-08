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
import { RouterModule, Routes } from '@angular/router';

import { MailServerComponent } from '@modules/home/pages/mail-server/mail-server.component';
import { ConfirmOnExitGuard } from '@core/guards/confirm-on-exit.guard';
import { Authority } from '@shared/models/authority.enum';

const routes: Routes = [
  {
    path: 'settings',
    data: {
      auth: [Authority.SYS_ADMIN],
      breadcrumb: {
        label: 'admin.system-settings',
        icon: 'settings'
      }
    },
    children: [
      {
        path: '',
        data: {
          auth: [Authority.SYS_ADMIN],
          redirectTo: {
            SYS_ADMIN: '/settings/outgoing-mail'
          }
        }
      },
      {
        path: 'outgoing-mail',
        component: MailServerComponent,
        canDeactivate: [ConfirmOnExitGuard],
        data: {
          auth: [Authority.SYS_ADMIN],
          title: 'admin.outgoing-mail-settings',
          breadcrumb: {
            label: 'admin.outgoing-mail',
            icon: 'mdi:email',
            isMdiIcon: true
          }
        }
      }
    ]
  }
];

@NgModule({
  imports: [RouterModule.forChild(routes)], exports: [RouterModule]
})
export class MailServerRoutingModule {
}
