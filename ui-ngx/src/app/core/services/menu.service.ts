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
        id: 'home',
        name: 'home.home',
        type: 'link',
        path: '/home',
        icon: 'mdi:view-dashboard-outline'
      },
      {
        id: 'sessions',
        name: 'mqtt-client-session.sessions',
        type: 'link',
        path: '/sessions',
        icon: 'mdi:book-multiple'
      },
      {
        id: 'client_credentials',
        name: 'mqtt-client-credentials.credentials',
        type: 'link',
        path: '/client-credentials',
        icon: 'mdi:shield-lock'
      },
      {
        id: 'retained_messages',
        name: 'retained-message.retained-messages',
        type: 'link',
        path: '/retained-messages',
        icon: 'mdi:archive-outline'
      },
      {
        id: 'shared_subscriptions_management',
        name: 'shared-subscription.shared-subscriptions',
        type: 'toggle',
        path: '/shared-subscriptions',
        icon: 'mediation',
        pages: [
          {
            id: 'shared_subscriptions',
            name: 'shared-subscription.groups',
            type: 'link',
            path: '/shared-subscriptions/manage',
            icon: 'lan'
          },
          {
            id: 'application_shared_subscriptions',
            name: 'shared-subscription.applications',
            type: 'link',
            path: '/shared-subscriptions/applications',
            icon: 'mdi:monitor-share'
          },
        ]
      },
      {
        id: 'kafka_management',
        name: 'kafka.management',
        type: 'toggle',
        path: '/kafka',
        icon: 'apps',
        pages: [
          {
            id: 'kafka_topics',
            name: 'kafka.topics-title',
            type: 'link',
            path: '/kafka/topics',
            icon: 'topic'
          },
          {
            id: 'kafka_consumer_groups',
            name: 'kafka.consumer-groups-title',
            type: 'link',
            path: '/kafka/consumer-groups',
            icon: 'filter_alt'
          },
          /*          {
                      id: 'kafka_brokers',
                      name: 'kafka.brokers-title',
                      type: 'link',
                      path: '/kafka/brokers',
                      icon: 'mdi:server'
                    }*/
        ]
      },
      {
        id: 'monitoring',
        name: 'monitoring.monitoring',
        type: 'link',
        path: '/monitoring',
        icon: 'mdi:monitor-dashboard'
      },
      {
        id: 'users',
        name: 'user.users',
        type: 'link',
        path: '/users',
        icon: 'mdi:account-multiple-outline'
      },
      {
        id: 'system_settings',
        name: 'admin.system-settings',
        type: 'toggle',
        path: '/settings',
        icon: 'settings',
        pages: [
          {
            id: 'outgoing_mail',
            name: 'admin.outgoing-mail',
            type: 'link',
            path: '/settings/outgoing-mail',
            icon: 'mdi:email'
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
        icon: 'mdi:api'
      },
      {
        name: 'home.configuration',
        path: 'install/config',
        icon: 'mdi:cog-outline'
      },
      {
        name: 'home.integration-with-thingsboard',
        path: 'user-guide/integrations/how-to-connect-thingsboard-to-tbmq',
        icon: 'input'
      },
      {
        name: 'home.performance-tests',
        path: 'reference/100m-connections-performance-test',
        icon: 'mdi:speedometer'
      },
      {
        name: 'home.security',
        path: 'security',
        icon: 'mdi:security'
      },
      {
        name: 'home.mqtt-client-type',
        path: 'user-guide/mqtt-client-type',
        icon: 'mdi:devices'
      },
      {
        name: 'home.shared-subscriptions',
        path: 'user-guide/shared-subscriptions',
        icon: 'mdi:monitor-share'
      },
      {
        name: 'home.retained-messages',
        path: 'user-guide/retained-messages',
        icon: 'mdi:archive-outline'
      }
    ]);
  }
}

