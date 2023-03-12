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

import { Authority } from "@shared/models/authority.enum";

export interface TimeseriesData {
  [key: string]: Array<TsValue>;
}

export interface TsValue {
  ts: number;
  value: string;
  count?: number;
}

export enum StatsChartType {
  INCOMING_MESSAGES = 'INCOMING_MESSAGES',
  OUTGOING_MESSAGES = 'OUTGOING_MESSAGES',
  DROPPED_MESSAGES = 'DROPPED_MESSAGES',
  SESSIONS = 'SESSIONS',
  SUBSCRIPTIONS = 'SUBSCRIPTIONS'
}

export const StatsChartTypeTranslationMap = new Map<StatsChartType, string>(
  [
    [StatsChartType.INCOMING_MESSAGES, 'overview.incoming-messages'],
    [StatsChartType.OUTGOING_MESSAGES, 'overview.outgoing-messages'],
    [StatsChartType.DROPPED_MESSAGES, 'overview.dropped-messages'],
    [StatsChartType.SESSIONS, 'overview.sessions'],
    [StatsChartType.SUBSCRIPTIONS, 'overview.subscriptions'],
  ]
);

export interface ThreeCardsData {
  title: string,
  link: {
    enabled: boolean,
    href: string
  },
  docs: {
    enabled: boolean,
    href: string
  },
  actions: {
    enabled: boolean,
    items: Array<any>
  },
  items: Array<CardItem>
}

export interface CardItem {
  key: string;
  value: number | string;
  label: string;
}

export enum ConfigParams {
  PORT_MQTT = 'PORT_MQTT',
  TCP_LISTENER = 'TCP_LISTENER',
  TCP_LISTENER_MAX_PAYLOAD_SIZE = 'TCP_LISTENER_MAX_PAYLOAD_SIZE',
  TLS_LISTENER = 'TLS_LISTENER',
  TLS_LISTENER_MAX_PAYLOAD_SIZE = 'TLS_LISTENER_MAX_PAYLOAD_SIZE',
  TLS_TCP_PORT = 'TLS_TCP_PORT',
  BASIC_AUTH = 'BASIC_AUTH',
  X509_CERT_CHAIN_AUTH = 'X509_CERT_CHAIN_AUTH',
}

export const ConfigParamsTranslationMap = new Map<ConfigParams, string>(
  [
    [ConfigParams.PORT_MQTT, 'config.port-mqtt'],
    [ConfigParams.TCP_LISTENER, 'config.tcp-listener'],
    [ConfigParams.TCP_LISTENER_MAX_PAYLOAD_SIZE, 'config.tcp-listener-max-payload-size'],
    [ConfigParams.TLS_LISTENER, 'config.tls-listener'],
    [ConfigParams.TLS_LISTENER_MAX_PAYLOAD_SIZE, 'config.tls-listener-max-payload-size'],
    [ConfigParams.TLS_TCP_PORT, 'config.tls-tcp-port'],
    [ConfigParams.BASIC_AUTH, 'config.basic-auth'],
    [ConfigParams.X509_CERT_CHAIN_AUTH, 'config.ssl-auth'],
  ]
);
