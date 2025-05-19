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

import { Injectable } from '@angular/core';
import { select, Store } from '@ngrx/store';
import { AppState } from '../core.state';
import { selectAuth, selectIsAuthenticated } from '../auth/auth.selectors';
import { take } from 'rxjs/operators';
import { MenuId, MenuSection } from '@core/services/menu.models';
import { BehaviorSubject, Observable, of, Subject } from 'rxjs';
import { Authority } from '@shared/models/authority.enum';
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
        id: MenuId.home,
        name: 'home.home',
        type: 'link',
        path: '/home',
        icon: 'mdi:view-dashboard-outline'
      },
      {
        id: MenuId.sessions,
        name: 'mqtt-client-session.sessions',
        type: 'link',
        path: '/sessions',
        icon: 'mdi:book-multiple'
      },
      {
        id: MenuId.subscriptions,
        name: 'subscription.subscriptions',
        type: 'link',
        path: '/subscriptions',
        icon: 'mdi:filter-outline'
      },
      {
        id: MenuId.integrations,
        name: 'integration.integrations',
        type: 'link',
        path: '/integrations',
        icon: 'input'
      },
      {
        id: MenuId.authentication,
        name: 'authentication.authentication',
        type: 'link',
        path: '/authentication',
        icon: 'mdi:shield-lock',
        pages: [
          {
            id: MenuId.client_credentials,
            name: 'mqtt-client-credentials.credentials',
            type: 'link',
            path: '/authentication/client-credentials',
            icon: 'mdi:account-lock-outline'
          },
          {
            id: MenuId.mqtt_auth_provider,
            name: 'authentication.providers',
            type: 'link',
            path: '/authentication/providers',
            icon: 'settings'
          }
        ]
      },
      {
        id: MenuId.unauthorized_clients,
        name: 'unauthorized-client.unauthorized-clients',
        type: 'link',
        path: '/unauthorized-clients',
        icon: 'no_accounts'
      },
      {
        id: MenuId.web_socket_client,
        name: 'ws-client.ws-client',
        type: 'link',
        path: '/ws-client',
        icon: 'mdi:chat'
      },
      {
        id: MenuId.retained_messages,
        name: 'retained-message.retained-messages',
        type: 'link',
        path: '/retained-messages',
        icon: 'mdi:archive'
      },
      {
        id: MenuId.shared_subscriptions_management,
        name: 'shared-subscription.shared-subscriptions',
        type: 'link',
        path: '/shared-subscriptions',
        icon: 'mediation',
        pages: [
          {
            id: MenuId.shared_subscriptions,
            name: 'shared-subscription.groups',
            type: 'link',
            path: '/shared-subscriptions/manage',
            icon: 'lan'
          },
          {
            id: MenuId.shared_subscriptions_application,
            name: 'shared-subscription.application-shared-subscriptions',
            type: 'link',
            path: '/shared-subscriptions/applications',
            icon: 'mdi:monitor-share'
          }
        ]
      },
      {
        id: MenuId.kafka_management,
        name: 'kafka.management',
        type: 'link',
        path: '/kafka',
        icon: 'apps',
        pages: [
          {
            id: MenuId.kafka_topics,
            name: 'kafka.topics-title',
            type: 'link',
            path: '/kafka/topics',
            icon: 'topic'
          },
          {
            id: MenuId.kafka_consumer_groups,
            name: 'kafka.consumer-groups-title',
            type: 'link',
            path: '/kafka/consumer-groups',
            icon: 'filter_alt'
          },
          /*{
            id: MenuId.kafka_brokers,
            name: 'kafka.brokers-title',
            type: 'link',
            path: '/kafka/brokers',
            icon: 'mdi:server'
          }*/
        ]
      },
      {
        id: MenuId.monitoring,
        name: 'monitoring.monitoring',
        type: 'link',
        path: '/monitoring',
        icon: 'mdi:monitor-dashboard',
        pages: [
          {
            id: MenuId.monitoring_broker_stats,
            name: 'monitoring.stats',
            type: 'link',
            path: '/monitoring/stats',
            icon: 'insert_chart'
          },
          {
            id: MenuId.monitoring_resource_usage,
            name: 'monitoring.resource-usage.resource-usage',
            type: 'link',
            path: '/monitoring/resource-usage',
            icon: 'memory'
          },
        ]
      },
      {
        id: MenuId.users,
        name: 'user.users',
        type: 'link',
        path: '/users',
        icon: 'mdi:account-multiple'
      },
      {
        id: MenuId.system_settings,
        name: 'admin.system-settings',
        type: 'link',
        path: '/settings',
        icon: 'settings',
        pages: [
          {
            id: MenuId.system_settings_general,
            name: 'admin.general',
            type: 'link',
            path: '/settings/general',
            icon: 'settings'
          },
          {
            id: MenuId.system_settings_security,
            name: 'home.security',
            type: 'link',
            path: '/settings/security',
            icon: 'security'
          },
          {
            id: MenuId.mail_server,
            name: 'admin.outgoing-mail',
            type: 'link',
            path: '/settings/outgoing-mail',
            icon: 'mdi:email'
          },
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
        icon: 'mdi:archive'
      }
    ]);
  }
}

