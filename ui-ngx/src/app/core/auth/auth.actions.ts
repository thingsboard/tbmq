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

import {Action} from '@ngrx/store';
import {User} from '@shared/models/user.model';
import {AuthPayload} from '@core/auth/auth.models';

export enum AuthActionTypes {
  AUTHENTICATED = '[Auth] Authenticated',
  UNAUTHENTICATED = '[Auth] Unauthenticated',
  LOAD_USER = '[Auth] Load User',
  UPDATE_USER_DETAILS = '[Auth] Update User Details'
}

export class ActionAuthAuthenticated implements Action {
  readonly type = AuthActionTypes.AUTHENTICATED;

  constructor(readonly payload: AuthPayload) {}
}

export class ActionAuthUnauthenticated implements Action {
  readonly type = AuthActionTypes.UNAUTHENTICATED;
}

export class ActionAuthLoadUser implements Action {
  readonly type = AuthActionTypes.LOAD_USER;

  constructor(readonly payload: { isUserLoaded: boolean }) {}
}

export class ActionAuthUpdateUserDetails implements Action {
  readonly type = AuthActionTypes.UPDATE_USER_DETAILS;

  constructor(readonly payload: { userDetails: User }) {}
}

export type AuthActions = ActionAuthAuthenticated | ActionAuthUnauthenticated |
  ActionAuthLoadUser | ActionAuthUpdateUserDetails;
