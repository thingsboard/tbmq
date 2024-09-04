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

import { ClientType } from '@shared/models/client.model';
import { BaseData } from '@shared/models/base-data';
import {
  isArraysEqualIgnoreUndefined,
  isDefinedAndNotNull,
  isEmpty,
  isEqualIgnoreUndefined,
  isNotEmptyStr,
  isUndefinedOrNull
} from '@core/utils';
import { TimePageLink } from '@shared/models/page/page-link';
import { SubscriptionOptions } from '@shared/models/ws-client.model';

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
  connected?: boolean;
  credentials?: string;
  mqttVersion?: number;
}

export interface ShortClientSessionInfo {
  clientId: string;
  clientType: ClientType;
  connected?: boolean;
}

export interface TopicSubscription extends SubscriptionOptions {
  topic: string;
  qos: WsMqttQoSType;
}

export enum MqttQoS {
  AT_MOST_ONCE = 'AT_MOST_ONCE',
  AT_LEAST_ONCE = 'AT_LEAST_ONCE',
  EXACTLY_ONCE = 'EXACTLY_ONCE'
}

export interface MqttQoSType {
  value: MqttQoS;
  name: string;
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

export const mqttQoSValuesMap = new Map<MqttQoS, number>(
  [
    [MqttQoS.AT_MOST_ONCE, 0],
    [MqttQoS.AT_LEAST_ONCE, 1],
    [MqttQoS.EXACTLY_ONCE, 2]
  ]
);

export enum WsMqttQoSType {
  AT_MOST_ONCE = 0,
  AT_LEAST_ONCE = 1,
  EXACTLY_ONCE = 2
}

export const WsQoSTypes = [0, 1, 2];

export const WsQoSTranslationMap = new Map<WsMqttQoSType, string>(
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

export enum MqttVersion {
  MQTT_3_1 = 'MQTT_3_1',
  MQTT_3_1_1 = 'MQTT_3_1_1',
  MQTT_5 = 'MQTT_5'
}

export const MqttVersionTranslationMap = new Map<MqttVersion, string>(
  [
    [MqttVersion.MQTT_3_1, 'MQTT 3.1'],
    [MqttVersion.MQTT_3_1_1, 'MQTT 3.1.1'],
    [MqttVersion.MQTT_5, 'MQTT 5'],
  ]
);

export interface ClientSessionStatsInfo {
  connectedCount: number;
  disconnectedCount: number;
  totalCount: number;
}

export interface SessionFilterConfig {
  connectedStatusList?: ConnectionState[];
  clientTypeList?: ClientType[];
  nodeIdList?: string[];
  cleanStartList?: boolean[];
  subscriptions?: number;
  clientId?: string;
  openSession?: boolean;
}

export interface ClientSessionCredentials {
  name: string;
  mqttVersion: MqttVersion;
}

export const sessionFilterConfigEquals = (filter1?: SessionFilterConfig, filter2?: SessionFilterConfig): boolean => {
  if (filter1 === filter2) {
    return true;
  }
  if ((isUndefinedOrNull(filter1) || isEmpty(filter1)) && (isUndefinedOrNull(filter2) || isEmpty(filter2))) {
    return true;
  } else if (isDefinedAndNotNull(filter1) && isDefinedAndNotNull(filter2)) {
    if (!isArraysEqualIgnoreUndefined(filter1.connectedStatusList, filter2.connectedStatusList)) {
      return false;
    }
    if (!isArraysEqualIgnoreUndefined(filter1.clientTypeList, filter2.clientTypeList)) {
      return false;
    }
    if (!isArraysEqualIgnoreUndefined(filter1.cleanStartList, filter2.cleanStartList)) {
      return false;
    }
    if (!isArraysEqualIgnoreUndefined(filter1.nodeIdList, filter2.nodeIdList)) {
      return false;
    }
    if (!isEqualIgnoreUndefined(filter1.clientId, filter2.clientId)) {
      return false;
    }
    if (!isEqualIgnoreUndefined(filter1.subscriptions, filter2.subscriptions)) {
      return false;
    }
    return true;
  }
  return false;
};

export class SessionQuery {
  pageLink: TimePageLink;

  clientId: string;
  connectedStatusList: ConnectionState[];
  clientTypeList: ClientType[];
  cleanStartList: boolean[];
  nodeIdList: string[];
  subscriptions: number;

  constructor(pageLink: TimePageLink, sessionFilter: SessionFilterConfig) {
    this.pageLink = pageLink;
    this.connectedStatusList = sessionFilter?.connectedStatusList;
    this.clientTypeList = sessionFilter?.clientTypeList;
    this.cleanStartList = sessionFilter?.cleanStartList;
    this.nodeIdList = sessionFilter?.nodeIdList;
    this.subscriptions = sessionFilter?.subscriptions;
    if (isNotEmptyStr(sessionFilter?.clientId)) {
      this.pageLink.textSearch = sessionFilter.clientId;
    }
  }

  public toQuery(): string {
    let query = this.pageLink.toQuery();
    if (this.connectedStatusList && this.connectedStatusList.length) {
      query += `&connectedStatusList=${this.connectedStatusList.join(',')}`;
    }
    if (this.clientTypeList && this.clientTypeList.length) {
      query += `&clientTypeList=${this.clientTypeList.join(',')}`;
    }
    if (this.cleanStartList && this.cleanStartList.length) {
      query += `&cleanStartList=${this.cleanStartList.join(',')}`;
    }
    if (this.nodeIdList && this.nodeIdList.length) {
      query += `&nodeIdList=${this.nodeIdList.join(',')}`;
    }
    if (typeof this.subscriptions !== 'undefined' && this.subscriptions !== null) {
      query += `&subscriptions=${this.subscriptions}`;
    }
    return query;
  }
}
