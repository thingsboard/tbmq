///
/// Copyright © 2016-2020 The Thingsboard Authors
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

import { BaseData } from '@shared/models/base-data';
import { EntityId } from '@shared/models/id/entity-id';
import { EntityType } from '@shared/models/entity-type.models';
import { TenantId } from '@shared/models/id/tenant-id';
import { EdgeEventStatus } from '@shared/models/edge.models';
import { NULL_UUID } from '@shared/models/id/has-uuid';

export enum ClientType {
  DEVICE = 'DEVICE',
  APPLICATION = 'APPLICATION'
}

export enum MqttCredentialsType {
  MQTT_BASIC = 'MQTT_BASIC',
  SSL = 'SSL'
}

export const MqttCredentialsTypes = [MqttCredentialsType.SSL, MqttCredentialsType.MQTT_BASIC];

export enum ConnectionState {
  CONNECTED = 'CONNECTED',
  DISCONNECTED = 'DISCONNECTED'
}

export const connectionStateColor = new Map<ConnectionState, string>(
  [
    [ConnectionState.CONNECTED, '#008A00'],
    [ConnectionState.DISCONNECTED, '#757575']
  ]
);

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
  }];

export const clientTypeTranslationMap = new Map<ClientType, string>(
  [
    [ClientType.DEVICE, 'mqtt-client.device'],
    [ClientType.APPLICATION, 'mqtt-client.application']
  ]
);

export const credentialsTypeNames = new Map<MqttCredentialsType, string>(
  [
    [MqttCredentialsType.MQTT_BASIC, 'MQTT Basic'],
    [MqttCredentialsType.SSL, 'SSL']
  ]
);

export const connectionStateTranslationMap = new Map<ConnectionState, string>(
  [
    [ConnectionState.CONNECTED, 'mqtt-client-session.connected'],
    [ConnectionState.DISCONNECTED, 'mqtt-client-session.disconnected']
  ]
);

export interface Client extends ClientInfo, ClientSessionInfo, BaseData<ClientId> {
}

export class ClientId implements EntityId {
  entityType = EntityType.MQTT_CLIENT;
  id: string;
  constructor(id: string) {
    this.id = id;
  }
}

export interface ClientSession {
  connected: boolean;
  sessionInfo: SessionInfo;
}

export interface ClientInfo {
  clientId: string;
  type: ClientType;
}

export interface SessionInfo {
  serviceId: string;
  sessionId: string;
  persistent: boolean;
  clientInfo: ClientInfo;
}

export interface MqttCredentials extends BaseData<ClientId> {
  name: string;
  credentialsId: string;
  clientType: ClientType;
  credentialsType: MqttCredentialsType;
  credentialsValue: string;
}

export interface MqttAdminDto extends BaseData<ClientId> {
  email: string;
  password: string;
  firstName?: string;
  lastName?: string;
}

export interface ClientSessionInfo extends BaseData<ClientId> {
  clientId: string;
  sessionId: string;
  connectionState: ConnectionState;
  clientType: ClientType;
  nodeId: string;
  persistent: boolean;
  subscriptions: TopicSubscription[];
  keepAliveSeconds: number;
  connectedAt: number;
  disconnectedAt: number;
  username: string;
  note: string;
  cleanSession: boolean;
  subscriptionsCount: string;
}

export interface TopicSubscription {
  topic: string;
  qos: MqttQoS;
}

export interface SslMqttCredentials {
  parentCertCommonName: string;
  authorizationRulesMapping: string[];
}

export interface BasicMqttCredentials {
  clientId: string;
  userName: string;
  password: string;
  authorizationRulePattern: string;
}
