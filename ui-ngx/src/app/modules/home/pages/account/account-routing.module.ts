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

import { inject, NgModule } from '@angular/core';
import { ResolveFn, RouterModule, Routes } from '@angular/router';
import { Authority } from '@shared/models/authority.enum';
import { ConfirmOnExitGuard } from '@core/guards/confirm-on-exit.guard';
import { User } from '@shared/models/user.model';
import { AuthService } from '@core/http/auth.service';

const UserProfileResolver: ResolveFn<User> = () => inject(AuthService).getUser();

const routes: Routes = [
  {
    path: 'account',
    loadComponent: () => import('@home/components/router-tabs.component').then(m => m.RouterTabsComponent),
    data: {
      auth: [Authority.SYS_ADMIN],
      breadcrumb: {
        label: 'account.account',
        icon: 'account_circle'
      },
      useChildrenRoutesForTabs: true,
    },
    children: [
      {
        path: '',
        children: [],
        data: {
          redirectTo: {
            SYS_ADMIN: '/account/profile'
          }
        }
      },
      {
        path: 'profile',
        loadComponent: () => import('@home/pages/account/profile/profile.component').then(m => m.ProfileComponent),
        canDeactivate: [ConfirmOnExitGuard],
        data: {
          auth: [Authority.SYS_ADMIN],
          title: 'profile.profile',
          breadcrumb: {
            label: 'profile.profile',
            icon: 'account_circle'
          }
        },
        resolve: {
          user: UserProfileResolver
        }
      },
      {
        path: 'security',
        loadComponent: () => import('@home/pages/account/security/security.component').then(m => m.SecurityComponent),
        canDeactivate: [ConfirmOnExitGuard],
        data: {
          auth: [Authority.SYS_ADMIN],
          title: 'security.security',
          breadcrumb: {
            label: 'security.security',
            icon: 'lock'
          }
        },
        resolve: {
          user: UserProfileResolver
        }
      }
    ]
  }
];

@NgModule({
  imports: [RouterModule.forChild(routes)],
  exports: [RouterModule]
})
export class AccountRoutingModule { }
