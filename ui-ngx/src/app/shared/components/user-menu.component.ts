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

import {
  booleanAttribute,
  ChangeDetectionStrategy,
  Component,
  ElementRef,
  input,
  output,
  viewChild,
  ViewEncapsulation
} from '@angular/core';
import { User } from '@shared/models/user.model';
import { Authority } from '@shared/models/authority.enum';
import { select, Store } from '@ngrx/store';
import { AppState } from '@core/core.state';
import { selectAuthUser, selectUserDetails } from '@core/auth/auth.selectors';
import { map } from 'rxjs/operators';
import { Router } from '@angular/router';
import { AuthService } from '@core/http/auth.service';
import { AsyncPipe } from '@angular/common';
import { MatIcon } from '@angular/material/icon';
import { MatButton } from '@angular/material/button';
import { TranslateModule } from '@ngx-translate/core';
import { TbPopoverDirective } from '@shared/components/popover.component';
import { TbIconComponent } from '@shared/components/icon.component';
import { TruncateWithTooltipDirective } from '@shared/directives/truncate-with-tooltip.directive';

@Component({
    selector: 'tb-user-menu',
    templateUrl: './user-menu.component.html',
    styleUrls: ['./user-menu.component.scss'],
    changeDetection: ChangeDetectionStrategy.OnPush,
    encapsulation: ViewEncapsulation.None,
    imports: [MatIcon, MatButton, TranslateModule, AsyncPipe, TbPopoverDirective, TbIconComponent, TruncateWithTooltipDirective]
})
export class UserMenuComponent {

  readonly collapsed = input(false, { transform: booleanAttribute });

  readonly menuClicked = output<void>();

  readonly menuButtonElement = viewChild('menuButton', { read: ElementRef<HTMLElement> });

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

  userFullName$ = this.store.pipe(
    select(selectUserDetails),
    map((user) => this.getUserFullName(user))
  );

  userInitials$ = this.store.pipe(
    select(selectUserDetails),
    map((user) => this.getUserInitials(user))
  );

  userEmail$ = this.store.pipe(
    select(selectUserDetails),
    map((user) => user?.email)
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
    if (user) {
      return this.getUserFullName(user) || user.email;
    } else {
      return '';
    }
  }

  getUserFullName(user: User): string {
    if (user) {
      return [user.firstName, user.lastName].filter(Boolean).join(' ').trim();
    } else {
      return '';
    }
  }

  getUserInitials(user: User): string {
    if (user) {
      const first = (user.firstName || '').trim()[0] || '';
      const last = (user.lastName || '').trim()[0] || '';
      const nameInitials = (first + last).toUpperCase();
      if (nameInitials) {
        return nameInitials;
      }
      const [local, domain] = (user.email || '').split('@');
      const localInitial = (local || '')[0] || '';
      const domainInitial = (domain || '')[0] || '';
      return (localInitial + domainInitial).toUpperCase() || '?';
    } else {
      return '?';
    }
  }

  openAccount(): void {
    this.menuClicked.emit();
    this.router.navigate(['account']);
  }

  logout(): void {
    this.menuClicked.emit();
    this.authService.logout();
  }

}
