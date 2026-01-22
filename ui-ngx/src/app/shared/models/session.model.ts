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

import { ClientType } from '@shared/models/client.model';
import { BaseData } from '@shared/models/base-data';
import {
  isArraysEqualIgnoreUndefined,
  isDefinedAndNotNull,
  isEmpty,
  isEqualIgnoreUndefined,
  isNotEmptyStr, isNumber,
  isUndefinedOrNull
} from '@core/utils';
import { TimePageLink } from '@shared/models/page/page-link';
import { TopicSubscription } from '@shared/models/ws-client.model';
import { StatusColor, STATUS_COLOR } from '@home/models/entity/entities-table-config.models';
import { MqttAuthProviderType, UNKNOWN_AUTH_PROVIDER } from '@shared/models/mqtt-auth-provider.model';

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

export enum QoS {
  AT_MOST_ONCE = 0,
  AT_LEAST_ONCE = 1,
  EXACTLY_ONCE = 2
}

export const QosTranslation = new Map<QoS, string>([
  [QoS.AT_MOST_ONCE, 'mqtt-client-session.qos-at-most-once'],
  [QoS.AT_LEAST_ONCE, 'mqtt-client-session.qos-at-least-once'],
  [QoS.EXACTLY_ONCE, 'mqtt-client-session.qos-exactly-once']
]);

export const QosAsNum = (qos: QoS): string => QoS[qos];

export const QosTypes = Object.values(QoS).filter(v => isNumber(v)) as QoS[];

export const DEFAULT_QOS = QoS.AT_LEAST_ONCE;

export enum ConnectionState {
  CONNECTED = 'CONNECTED',
  DISCONNECTED = 'DISCONNECTED'
}

export const connectionStateColor = new Map<ConnectionState, StatusColor>(
  [
    [ConnectionState.CONNECTED, STATUS_COLOR.ACTIVE],
    [ConnectionState.DISCONNECTED, STATUS_COLOR.INACTIVE]
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
  subscriptionOperation?: string;
  clientId?: string;
  clientIpAddress?: string;
  openSession?: boolean;
}

export interface ClientSessionCredentials {
  authProvider?: MqttAuthProviderType;
  credentialsName?: string;
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
    if (!isEqualIgnoreUndefined(filter1.clientIpAddress, filter2.clientIpAddress)) {
      return false;
    }
    if (!isEqualIgnoreUndefined(filter1.subscriptions, filter2.subscriptions)) {
      return false;
    }
    if (!isEqualIgnoreUndefined(filter1.subscriptionOperation, filter2.subscriptionOperation)) {
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
  subscriptionOperation: string;
  clientIpAddress: string;

  constructor(pageLink: TimePageLink, sessionFilter: SessionFilterConfig) {
    this.pageLink = pageLink;
    this.connectedStatusList = sessionFilter?.connectedStatusList;
    this.clientTypeList = sessionFilter?.clientTypeList;
    this.cleanStartList = sessionFilter?.cleanStartList;
    this.nodeIdList = sessionFilter?.nodeIdList;
    this.subscriptions = sessionFilter?.subscriptions;
    this.subscriptionOperation = sessionFilter?.subscriptionOperation;
    this.clientIpAddress = sessionFilter?.clientIpAddress;
    if (isNotEmptyStr(sessionFilter?.clientId)) {
      this.pageLink.textSearch = sessionFilter.clientId;
    }
  }

  public toQuery(): string {
    let query = this.pageLink.toQuery();
    if (this.connectedStatusList?.length) {
      query += `&connectedStatusList=${this.connectedStatusList.join(',')}`;
    }
    if (this.clientTypeList?.length) {
      query += `&clientTypeList=${this.clientTypeList.join(',')}`;
    }
    if (this.cleanStartList?.length) {
      query += `&cleanStartList=${this.cleanStartList.join(',')}`;
    }
    if (this.nodeIdList?.length) {
      query += `&nodeIdList=${this.nodeIdList.join(',')}`;
    }
    if (isDefinedAndNotNull(this.subscriptions)) {
      query += `&subscriptionOperation=${this.subscriptionOperation}`;
      query += `&subscriptions=${this.subscriptions}`;
    }
    if (this.clientIpAddress?.length) {
      query += `&clientIpAddress=${this.clientIpAddress}`;
    }
    return query;
  }
}

export interface SessionMetricsTable extends BaseData {
  key: SessionMetrics;
  value: number;
  ts: number;
}

export enum SessionMetrics {
  receivedPubMsgs = 'receivedPubMsgs',
  qos0ReceivedPubMsgs = 'qos0ReceivedPubMsgs',
  qos1ReceivedPubMsgs = 'qos1ReceivedPubMsgs',
  qos2ReceivedPubMsgs = 'qos2ReceivedPubMsgs',
  sentPubMsgs = 'sentPubMsgs',
  qos0SentPubMsgs = 'qos0SentPubMsgs',
  qos1SentPubMsgs = 'qos1SentPubMsgs',
  qos2SentPubMsgs = 'qos2SentPubMsgs',
}

export const SessionMetricsTranslationMap = new Map<SessionMetrics, string>(
  [
    [SessionMetrics.receivedPubMsgs, 'mqtt-client-session.metrics-received'],
    [SessionMetrics.qos0ReceivedPubMsgs, 'mqtt-client-session.metrics-qos-0-received'],
    [SessionMetrics.qos1ReceivedPubMsgs, 'mqtt-client-session.metrics-qos-1-received'],
    [SessionMetrics.qos2ReceivedPubMsgs, 'mqtt-client-session.metrics-qos-2-received'],
    [SessionMetrics.sentPubMsgs, 'mqtt-client-session.metrics-sent'],
    [SessionMetrics.qos0SentPubMsgs, 'mqtt-client-session.metrics-qos-0-sent'],
    [SessionMetrics.qos1SentPubMsgs, 'mqtt-client-session.metrics-qos-1-sent'],
    [SessionMetrics.qos2SentPubMsgs, 'mqtt-client-session.metrics-qos-2-sent'],
  ]
);

export const SessionMetricsList: string[] = Object.values(SessionMetrics);

export const ClientCredentialsLabelTranslationMap = new Map<MqttAuthProviderType, string>(
  [
    [MqttAuthProviderType.MQTT_BASIC, 'mqtt-client-session.credentials-label-basic'],
    [MqttAuthProviderType.X_509, 'mqtt-client-session.credentials-label-x509'],
    [MqttAuthProviderType.SCRAM, 'mqtt-client-session.credentials-label-auth-provider'],
    [MqttAuthProviderType.JWT, 'mqtt-client-session.credentials-label-auth-provider'],
    [MqttAuthProviderType.HTTP, 'mqtt-client-session.credentials-label-auth-provider'],
    [UNKNOWN_AUTH_PROVIDER, 'mqtt-client-session.credentials-label-unknown'],
  ]
);
