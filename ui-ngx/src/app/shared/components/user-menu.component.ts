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

import { ChangeDetectionStrategy, Component, input } from '@angular/core';
import { User } from '@shared/models/user.model';
import { Authority } from '@shared/models/authority.enum';
import { select, Store } from '@ngrx/store';
import { AppState } from '@core/core.state';
import { selectAuthUser, selectUserDetails } from '@core/auth/auth.selectors';
import { map } from 'rxjs/operators';
import { Router } from '@angular/router';
import { AuthService } from "@core/http/auth.service";
import { FlexModule } from '@angular/flex-layout/flex';
import { AsyncPipe } from '@angular/common';
import { ExtendedModule } from '@angular/flex-layout/extended';
import { MatIcon } from '@angular/material/icon';
import { MatIconButton } from '@angular/material/button';
import { MatMenuTrigger, MatMenu, MatMenuItem } from '@angular/material/menu';
import { TranslateModule } from '@ngx-translate/core';

@Component({
    selector: 'tb-user-menu',
    templateUrl: './user-menu.component.html',
    styleUrls: ['./user-menu.component.scss'],
    changeDetection: ChangeDetectionStrategy.OnPush,
    imports: [FlexModule, ExtendedModule, MatIcon, MatIconButton, MatMenuTrigger, MatMenu, MatMenuItem, TranslateModule, AsyncPipe]
})
export class UserMenuComponent {

  readonly displayUserInfo = input<boolean>();

  authority$ = this.store.pipe(
    select(selectAuthUser),
    map((authUser) => authUser ? authUser.authority : Authority.ANONYMOUS)
  );

  authorityName$ = this.store.pipe(
    select(selectUserDetails),
    map((user) => this.getAuthorityName(user))
  );

  userDisplayName$ = this.store.pipe(
    select(selectUserDetails),
    map((user) => this.getUserDisplayName(user))
  );

  constructor(private store: Store<AppState>,
              private router: Router,
              private authService: AuthService) {
  }

  getAuthorityName(user: User): string {
    let name = null;
    if (user) {
      const authority = user.authority;
      switch (authority) {
        case Authority.SYS_ADMIN:
          name = 'user.sys-admin';
          break;
      }
    }
    return name;
  }

  getUserDisplayName(user: User): string {
    let name = '';
    if (user) {
      if ((user.firstName && user.firstName.length > 0) ||
        (user.lastName && user.lastName.length > 0)) {
        if (user.firstName) {
          name += user.firstName;
        }
        if (user.lastName) {
          if (name.length > 0) {
            name += ' ';
          }
          name += user.lastName;
        }
      } else {
        name = user.email;
      }
    }
    return name;
  }

  openAccount(): void {
    this.router.navigate(['account']);
  }

  logout(): void {
    this.authService.logout();
  }

}
