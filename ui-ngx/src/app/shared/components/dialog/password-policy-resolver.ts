///
/// Copyright © 2016-2026 The Thingsboard Authors
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

import { inject } from '@angular/core';
import { ResolveFn, ActivatedRouteSnapshot, RouterStateSnapshot, Router } from '@angular/router';
import { AuthService } from '@core/http/auth.service';
import { UserPasswordPolicy } from '@shared/models/settings.models';
import { catchError } from 'rxjs/operators';
import { of } from 'rxjs';

export const passwordPolicyResolver: ResolveFn<UserPasswordPolicy> = (route: ActivatedRouteSnapshot,
                                                                      state: RouterStateSnapshot,
                                                                      router = inject(Router),
                                                                      authService = inject(AuthService)) => {
  return authService.getUserPasswordPolicy({ignoreErrors: true}).pipe(
    catchError(() => {
      return of({} as UserPasswordPolicy);
    })
  );
};
