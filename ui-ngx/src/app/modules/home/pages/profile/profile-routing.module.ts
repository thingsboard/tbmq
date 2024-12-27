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

import { inject, NgModule } from '@angular/core';
import { ResolveFn, RouterModule, Routes } from '@angular/router';

import { ConfirmOnExitGuard } from '@core/guards/confirm-on-exit.guard';
import { Authority } from '@shared/models/authority.enum';
import { User } from '@shared/models/user.model';
import { AuthService } from '@core/http/auth.service';

export const UserProfileResolver: ResolveFn<User> = () => inject(AuthService).getUser();

export const profileRoutes: Routes = [
  {
    path: 'profile',
    loadComponent: () => import('./profile.component').then(m => m.ProfileComponent),
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
  }
];

const routes: Routes = [
  {
    path: 'profile',
    redirectTo: 'account/profile'
  }
];

@NgModule({
  imports: [RouterModule.forChild(routes)],
  exports: [RouterModule]
})
export class ProfileRoutingModule {
}
