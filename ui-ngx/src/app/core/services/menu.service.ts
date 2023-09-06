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

import { Injectable } from '@angular/core';
import { select, Store } from '@ngrx/store';
import { AppState } from '../core.state';
import { selectAuth, selectIsAuthenticated } from '../auth/auth.selectors';
import { take } from 'rxjs/operators';
import { MenuSection } from '@core/services/menu.models';
import { BehaviorSubject, Observable, of, Subject } from 'rxjs';
import { Authority } from '@shared/models/authority.enum';
import { guid } from '@core/utils';
import { AuthState } from '@core/auth/auth.models';

@Injectable({
  providedIn: 'root'
})
export class MenuService {

  menuSections$: Subject<Array<MenuSection>> = new BehaviorSubject<Array<MenuSection>>([]);

  constructor(private store: Store<AppState>) {
    this.store.pipe(select(selectIsAuthenticated)).subscribe(
      (authenticated: boolean) => {
        if (authenticated) {
          this.buildMenu();
        }
      }
    );
  }

  private buildMenu() {
    this.store.pipe(select(selectAuth), take(1)).subscribe(
      (authState: AuthState) => {
        if (authState.authUser) {
          let menuSections: Array<MenuSection>;
          switch (authState.authUser.authority) {
            case Authority.SYS_ADMIN:
              menuSections = this.buildSysAdminMenu(authState);
              break;
          }
          this.menuSections$.next(menuSections);
        }
      }
    );
  }

  private buildSysAdminMenu(authState: AuthState): Array<MenuSection> {
    const sections: Array<MenuSection> = [];
    sections.push(
      {
        id: guid(),
        name: 'home.home',
        type: 'link',
        path: '/home',
        icon: 'mdi:view-dashboard-outline',
        isMdiIcon: true
      },
      {
        id: guid(),
        name: 'monitoring.monitoring',
        type: 'link',
        path: '/monitoring',
        icon: 'mdi:monitor-dashboard',
        isMdiIcon: true
      },
      {
        id: guid(),
        name: 'user.users',
        type: 'link',
        path: '/users',
        icon: 'mdi:account-multiple-outline',
        isMdiIcon: true
      },
      {
        id: guid(),
        name: 'mqtt-client-credentials.credentials',
        type: 'link',
        path: '/client-credentials',
        icon: 'mdi:shield-lock',
        isMdiIcon: true
      },
      {
        id: guid(),
        name: 'mqtt-client-session.sessions',
        type: 'link',
        path: '/sessions',
        icon: 'mdi:book-multiple',
        isMdiIcon: true
      },
      {
        id: guid(),
        name: 'shared-subscription.shared-subscriptions',
        type: 'link',
        path: '/shared-subscriptions',
        icon: 'mdi:monitor-share',
        isMdiIcon: true
      },
      {
        id: guid(),
        name: 'retained-message.retained-messages',
        type: 'link',
        path: '/retained-messages',
        icon: 'mdi:archive-outline',
        isMdiIcon: true
      },
      {
        id: guid(),
        name: 'admin.system-settings',
        type: 'toggle',
        path: '/settings',
        height: '40px',
        icon: 'settings',
        pages: [
          {
            id: guid(),
            name: 'admin.outgoing-mail',
            type: 'link',
            path: '/settings/outgoing-mail',
            icon: 'mdi:email',
            isMdiIcon: true
          }
        ]
      }
    );
    return sections;
  }

  public menuSections(): Observable<Array<MenuSection>> {
    return this.menuSections$;
  }

  public quickLinks(): Observable<Array<any>> {
    return of([
      {
        name: 'home.rest-api',
        path: 'rest-api',
        icon: 'mdi:api',
        isMdiIcon: true
      },
      {
        name: 'home.configuration',
        path: 'install/config',
        icon: 'mdi:cog-outline',
        isMdiIcon: true
      },
      {
        name: 'home.integration-with-thingsboard',
        path: 'user-guide/integrations/how-to-connect-thingsboard-to-tbmq',
        icon: 'input',
        isMdiIcon: false
      },
      {
        name: 'home.performance-tests',
        path: 'reference/performance-tests',
        icon: 'mdi:speedometer',
        isMdiIcon: true
      },
      {
        name: 'home.security',
        path: 'security',
        icon: 'mdi:security',
        isMdiIcon: true
      },
      {
        name: 'home.mqtt-client-type',
        path: 'user-guide/mqtt-client-type',
        icon: 'mdi:devices',
        isMdiIcon: true
      },
      {
        name: 'home.shared-subscriptions',
        path: 'user-guide/shared-subscriptions',
        icon: 'mdi:monitor-share',
        isMdiIcon: true
      },
      {
        name: 'home.retained-messages',
        path: 'user-guide/retained-messages',
        icon: 'mdi:archive-outline',
        isMdiIcon: true
      }
    ]);
  }
}

