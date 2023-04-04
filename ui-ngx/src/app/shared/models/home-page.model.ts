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

export enum HomePageTitleType {
  MONITORING = 'MONITORING',
  SESSION = 'SESSION',
  CLIENT_CREDENTIALS = 'CLIENT_CREDENTIALS',
  CONFIG = 'CONFIG',
  KAFKA_BROKERS = 'KAFKA_BROKERS',
  QUICK_LINKS = 'QUICK_LINKS',
  VERSION = 'VERSION',
  GETTING_STARTED = 'GETTING_STARTED'
}

export interface HomePageTitle {
  label: string;
  tooltip: string;
  link?: string;
}

export const homePageTitleConfig = new Map<HomePageTitleType, HomePageTitle>(
  [
    [
      HomePageTitleType.MONITORING,
      {
        label: 'monitoring.monitoring',
        tooltip: 'monitoring.monitoring',
        link: 'monitoring'
      }
    ],
        [
      HomePageTitleType.SESSION,
      {
        label: 'mqtt-client-session.sessions',
        tooltip: 'mqtt-client-session.sessions',
        link: 'sessions'
      }
    ],
        [
      HomePageTitleType.CLIENT_CREDENTIALS,
      {
        label: 'mqtt-client-credentials.credentials',
        tooltip: 'mqtt-client-credentials.credentials',
        link: 'client-credentials'
      }
    ],
        [
      HomePageTitleType.CONFIG,
      {
        label: 'home.config',
        tooltip: 'home.config'
      }
    ],
        [
      HomePageTitleType.KAFKA_BROKERS,
      {
        label: 'kafka.brokers',
        tooltip: 'kafka.brokers'
      }
    ],
        [
      HomePageTitleType.VERSION,
      {
        label: 'version.version',
        tooltip: 'version.version'
      }
    ],
        [
      HomePageTitleType.QUICK_LINKS,
      {
        label: 'home.quick-links',
        tooltip: 'home.quick-links'
      }
    ],
    [
      HomePageTitleType.GETTING_STARTED,
      {
        label: 'getting-started.getting-started',
        tooltip: 'getting-started.getting-started'
      }
    ]
  ]
);

export const SessionsHomeCardConfig = [{
  key: 'connectedCount',
  label: 'mqtt-client-session.connected',
  value: 0
}, {
  key: 'disconnectedCount',
  label: 'mqtt-client-session.disconnected',
  value: 0
}, {
  key: 'totalCount',
  label: 'home.total',
  value: 0
}];

export const CredentialsHomeCardConfig = [{
  key: 'deviceCredentialsCount',
  label: 'mqtt-client-credentials.type-devices',
  value: 0
}, {
  key: 'applicationCredentialsCount',
  label: 'mqtt-client-credentials.type-applications',
  value: 0
}, {
  key: 'totalCount',
  label: 'home.total',
  value: 0
}];
