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

import { ConnectionState } from '@shared/models/session.model';
import { ClientType } from '@shared/models/client.model';

export const POLLING_INTERVAL = 1000 * 60;
export const HOME_CHARTS_DURATION = 1000 * 60 * 11;

export enum HomePageTitleType {
  MONITORING = 'MONITORING',
  SESSION = 'SESSION',
  CLIENT_CREDENTIALS = 'CLIENT_CREDENTIALS',
  CONFIG = 'CONFIG',
  KAFKA_BROKERS = 'KAFKA_BROKERS',
  KAFKA_TOPICS = 'KAFKA_TOPICS',
  KAFKA_CONSUMER_GROUPS = 'KAFKA_CONSUMER_GROUPS',
  QUICK_LINKS = 'QUICK_LINKS',
  VERSION = 'VERSION',
  GETTING_STARTED = 'GETTING_STARTED'
}

export interface HomePageTitle {
  label: string;
  tooltip: string;
  link?: string;
  docsLink?: string;
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
        link: 'sessions',
        docsLink: 'user-guide/ui/sessions'
      }
    ],
    [
      HomePageTitleType.CLIENT_CREDENTIALS,
      {
        label: 'mqtt-client-credentials.credentials',
        tooltip: 'mqtt-client-credentials.credentials',
        link: 'client-credentials',
        docsLink: 'user-guide/ui/mqtt-client-credentials'
      }
    ],
    [
      HomePageTitleType.CONFIG,
      {
        label: 'home.config',
        tooltip: 'home.config',
        docsLink: 'user-guide/ui/monitoring/#config'
      }
    ],
    [
      HomePageTitleType.KAFKA_BROKERS,
      {
        label: 'kafka.brokers',
        tooltip: 'kafka.brokers',
        link: 'kafka/brokers',
        docsLink: 'user-guide/ui/monitoring/#kafka-brokers'
      }
    ],
    [
      HomePageTitleType.KAFKA_TOPICS,
      {
        label: 'kafka.topics',
        tooltip: 'kafka.topics',
        link: 'kafka/topics',
        docsLink: 'user-guide/ui/monitoring/#kafka-topics'
      }
    ],
    [
      HomePageTitleType.KAFKA_CONSUMER_GROUPS,
      {
        label: 'kafka.consumer-groups',
        tooltip: 'kafka.consumer-groups',
        link: 'kafka/consumer-groups',
        docsLink: 'user-guide/ui/monitoring/#kafka-consumer-groups'
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

export enum HomeCardType {
  SESSION = 'SESSION',
  CLIENT_CREDENTIALS = 'CLIENT_CREDENTIALS'
}

export interface HomeCardFilter {
  key: string;
  label: string;
  path: string;
  value: number;
  type: HomeCardType;
  filter: any;
}

export const SessionsHomeCardConfig: HomeCardFilter[] = [{
  key: 'connectedCount',
  label: 'mqtt-client-session.connected',
  path: '/sessions',
  value: 0,
  type: HomeCardType.SESSION,
  filter: {
    connectedStatusList: [ConnectionState.CONNECTED]
  }
},
  {
    key: 'disconnectedCount',
    label: 'mqtt-client-session.disconnected',
    path: '/sessions',
    value: 0,
    type: HomeCardType.SESSION,
    filter: {
      connectedStatusList: [ConnectionState.DISCONNECTED]
    }
  },
  {
    key: 'totalCount',
    label: 'home.total',
    path: '/sessions',
    value: 0,
    type: HomeCardType.SESSION,
    filter: {
      connectedStatusList: [ConnectionState.CONNECTED, ConnectionState.DISCONNECTED]
    }
  }];

export const CredentialsHomeCardConfig = [{
  key: 'deviceCredentialsCount',
  label: 'mqtt-client-credentials.type-devices',
  path: '/client-credentials',
  value: 0,
  type: HomeCardType.CLIENT_CREDENTIALS,
  filter: {
    clientTypeList: [ClientType.DEVICE]
  }
},
  {
    key: 'applicationCredentialsCount',
    label: 'mqtt-client-credentials.type-applications',
    path: '/client-credentials',
    value: 0,
    type: HomeCardType.CLIENT_CREDENTIALS,
    filter: {
      clientTypeList: [ClientType.APPLICATION]
    }
  },
  {
    key: 'totalCount',
    label: 'home.total',
    path: '/client-credentials',
    value: 0,
    type: HomeCardType.CLIENT_CREDENTIALS,
    filter: {
      clientTypeList: [ClientType.DEVICE, ClientType.APPLICATION]
    }
  }];
