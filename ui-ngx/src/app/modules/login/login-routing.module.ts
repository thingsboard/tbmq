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

import { NgModule } from '@angular/core';
import { RouterModule, Routes } from '@angular/router';
import { AuthGuard } from '@core/guards/auth.guard';

const routes: Routes = [
  {
    path: 'login',
    loadComponent: () => import('./pages/login/login.component').then(m => m.LoginComponent),
    data: {
      title: 'login.login',
      module: 'public'
    },
    canActivate: [AuthGuard]
  },
  {
    path: 'login/resetPasswordRequest',
    loadComponent: () => import('@modules/login/pages/login/reset-password-request.component').then(m => m.ResetPasswordRequestComponent),
    data: {
      title: 'login.request-password-reset',
      module: 'public'
    },
    canActivate: [AuthGuard]
  },
  {
    path: 'login/resetPassword',
    loadComponent: () => import('@modules/login/pages/login/reset-password.component').then(m => m.ResetPasswordComponent),
    data: {
      title: 'login.reset-password',
      module: 'public'
    },
    canActivate: [AuthGuard]
  },
  {
    path: 'login/resetExpiredPassword',
    loadComponent: () => import('@modules/login/pages/login/reset-password.component').then(m => m.ResetPasswordComponent),
    data: {
      title: 'login.reset-password',
      module: 'public',
      expiredPassword: true
    },
    canActivate: [AuthGuard]
  },
  {
    path: 'login/createPassword',
    loadComponent: () => import('@modules/login/pages/login/create-password.component').then(m => m.CreatePasswordComponent),
    data: {
      title: 'login.create-password',
      module: 'public'
    },
    canActivate: [AuthGuard]
  }
];

@NgModule({
  imports: [RouterModule.forChild(routes)],
  exports: [RouterModule]
})
export class LoginRoutingModule { }
