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

import { ClientInfo, ClientType } from '@shared/models/client.model';
import { BaseData } from '@shared/models/base-data';

export interface DetailedClientSessionInfo extends BaseData {
  clientId: string;
  sessionId: string;
  connectionState: ConnectionState;
  clientType: ClientType;
  nodeId: string;
  cleanStart: boolean;
  subscriptions: TopicSubscription[];
  keepAliveSeconds: number;
  connectedAt: number;
  disconnectedAt: number;
  sessionExpiryInterval: number;
  sessionEndTs: number;
  clientIpAdr: string;
  subscriptionsCount?: number;
}

export interface SessionInfo {
  serviceId: string;
  sessionId: string;
  persistent: boolean;
  clientInfo: ClientInfo;
  connectionInfo: ConnectionInfo;
}

export interface ConnectionInfo {
  connectedAt: number;
  disconnectedAt: number;
  keepAlive: number;
}

export interface TopicSubscription {
  topic: string;
  qos: MqttQoS;
  shareName: string;
}

export enum MqttQoS {
  AT_MOST_ONCE = 'AT_MOST_ONCE',
  AT_LEAST_ONCE = 'AT_LEAST_ONCE',
  EXACTLY_ONCE = 'EXACTLY_ONCE'
}

export const mqttQoSTypes = [
  {
    value: MqttQoS.AT_MOST_ONCE,
    name: 'mqtt-client-session.qos-at-most-once'
  },
  {
    value: MqttQoS.AT_LEAST_ONCE,
    name: 'mqtt-client-session.qos-at-least-once'
  },
  {
    value: MqttQoS.EXACTLY_ONCE,
    name: 'mqtt-client-session.qos-exactly-once'
  }
];

export const QoSTranslationMap = new Map<number, string>(
  [
    [0, 'mqtt-client-session.qos-at-most-once'],
    [1, 'mqtt-client-session.qos-at-least-once'],
    [2, 'mqtt-client-session.qos-exactly-once']
  ]
);

export enum ConnectionState {
  CONNECTED = 'CONNECTED',
  DISCONNECTED = 'DISCONNECTED'
}

export const connectionStateColor = new Map<ConnectionState, string>(
  [
    [ConnectionState.CONNECTED, '#008A00'],
    [ConnectionState.DISCONNECTED, '#e33737']
  ]
);

export const connectionStateTranslationMap = new Map<ConnectionState, string>(
  [
    [ConnectionState.CONNECTED, 'mqtt-client-session.connected'],
    [ConnectionState.DISCONNECTED, 'mqtt-client-session.disconnected']
  ]
);
