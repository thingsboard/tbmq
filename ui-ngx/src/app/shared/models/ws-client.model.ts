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

import { BaseData } from '@shared/models/base-data';
import { ClientCredentials } from '@shared/models/credentials.model';
import { ValueType } from '@shared/models/constants';
export type MqttJsProtocolVersion = 3 | 4 | 5;
export type MqttJsProtocolId = 'MQTT' | 'MQIsdp';
export type MqttJsProtocolSecurity = 'ws://' | 'wss://';

export enum ConnectionStatus {
  CONNECTED = 'CONNECTED',
  CONNECTING = 'CONNECTING',
  DISCONNECTED = 'DISCONNECTED',
  CONNECTION_FAILED = 'CONNECTION_FAILED',
  RECONNECTING = 'RECONNECTING',
  CLOSE = 'CLOSE',
  END = 'END',
  OFFLINE = 'OFFLINE',
}

export const ConnectionStatusTranslationMap = new Map<ConnectionStatus, string>([
  [ConnectionStatus.CONNECTED, 'ws-client.connections.connected'],
  [ConnectionStatus.CONNECTING, 'ws-client.connections.connecting'],
  [ConnectionStatus.DISCONNECTED, 'ws-client.connections.disconnected'],
  [ConnectionStatus.CONNECTION_FAILED, 'ws-client.connections.connection-failed'],
  [ConnectionStatus.RECONNECTING, 'ws-client.connections.reconnecting'],
  [ConnectionStatus.CLOSE, 'ws-client.connections.close'],
  [ConnectionStatus.END, 'ws-client.connections.end'],
  [ConnectionStatus.OFFLINE, 'ws-client.connections.offline'],
]);

export interface ConnectionStatusLog {
  createdTime: number;
  status: ConnectionStatus;
  details?: string;
}

// TODO check usage
const MqttJsProtocolIdVersionMap = new Map<MqttJsProtocolVersion, MqttJsProtocolId>([
  [3, 'MQIsdp'],
  [4, 'MQTT'],
  [5, 'MQIsdp']
]);

export interface ConnectionShortInfo extends BaseData {
  name: string;
}

export interface TbConnectionDetails extends ConnectionShortInfo {
  url: string;
  keepaliveUnit?: WebSocketTimeUnit;
  connectTimeoutUnit?: WebSocketTimeUnit;
  reconnectPeriodUnit?: WebSocketTimeUnit;
  clientCredentials?: Partial<ClientCredentials>;
  clientCredentialsId?: string;
}

export interface WebSocketConnectionDto extends BaseData {
  name: string;
  clientId?: string;
}

export interface WebSocketConnection extends WebSocketConnectionDto {
  userId: string;
  configuration: WebSocketConnectionConfiguration;
}

export interface WebSocketConnectionConfiguration {
  url: string;
  clientCredentialsId?: string;
  clientCredentials?: Partial<ClientCredentials>;
  clientId?: string;
  username?: string;
  password?: string;
  cleanStart?: boolean;
  keepAlive?: number;
  keepAliveUnit?: WebSocketTimeUnit;
  connectTimeout?: number;
  connectTimeoutUnit?: WebSocketTimeUnit;
  reconnectPeriod?: number;
  reconnectPeriodUnit?: WebSocketTimeUnit;
  mqttVersion?: MqttJsProtocolVersion;
  sessionExpiryInterval?: number;
  sessionExpiryIntervalUnit?: WebSocketTimeUnit;
  maxPacketSize?: number;
  maxPacketSizeUnit?: DataSizeUnitType;
  topicAliasMax?: number;
  receiveMax?: number;
  requestResponseInfo?: boolean;
  requestProblemInfo?: boolean;
  lastWillMsg?: LastWillMsg;
  userProperties?: any;
}

export interface LastWillMsg {
  topic?: string;
  qos?: QoS;
  payload?: Buffer;
  payloadType?: WsDataType;
  retain?: boolean;
  payloadFormatIndicator?: boolean;
  contentType?: string;
  willDelayInterval?: number;
  willDelayIntervalUnit?: WebSocketTimeUnit;
  msgExpiryInterval?: number;
  msgExpiryIntervalUnit?: WebSocketTimeUnit;
  responseTopic?: string;
  correlationData?: Buffer;
}

export declare type CorrelationData = WithImplicitCoercion<string> | {  [Symbol.toPrimitive](hint: 'string'): string; }

export declare type QoS = 0 | 1 | 2

export enum WsDataType {
  STRING = 'STRING',
  JSON = 'JSON'
}

export interface Connection extends TbConnectionDetails {
  protocol?: MqttJsProtocolSecurity,
  host?: string,
  port?: number,
  path?: string,
  protocolId?: MqttJsProtocolId;
  protocolVersion?: MqttJsProtocolVersion;
  clean?: boolean;
  clientId?: string;
  keepalive?: number;
  connectTimeout?: number;
  reconnectPeriod?: number;
  username?: string;
  password?: string;
  properties?: ConnectionProperties;
  userProperties?: any;
  will?: {
    topic?: string;
    payload?: any;
    payloadType?: any;
    qos?: QoS;
    retain?: boolean;
    properties?: WillProperties;
  };
}

export interface WebSocketUserProperties {
  props: WebSocketUserPropertiesKeyValue[]
}

export interface WebSocketUserPropertiesKeyValue {
  k: string;
  v: string;
}

interface TbConnectionProperties {
  sessionExpiryIntervalUnit?: WebSocketTimeUnit;
  maximumPacketSizeUnit?: DataSizeUnitType;
}

export interface ConnectionProperties extends TbConnectionProperties{
  sessionExpiryInterval?: number;
  receiveMaximum?: number;
  maximumPacketSize?: number;
  topicAliasMaximum?: number;
  requestResponseInformation?: boolean;
  requestProblemInformation?: boolean;
}

interface TbWillProperties {
  willDelayIntervalUnit?: WebSocketTimeUnit;
  messageExpiryIntervalUnit?: WebSocketTimeUnit;
}

interface WillProperties extends TbWillProperties {
  willDelayInterval: number;
  payloadFormatIndicator: boolean;
  messageExpiryInterval: number;
  contentType: string;
  responseTopic: string;
  correlationData: Buffer;
}

export interface WsPublishMessageOptions {
  qos?: QoS;
  retain?: boolean;
  dup?: boolean;
  properties?: PublishMessageProperties;
}

export interface WsTableMessage extends BaseData {
  subscriptionId?: string;
  createdTime?: number;
  topic?: string;
  payload?: any;
  options?: WsPublishMessageOptions;
  qos?: QoS;
  retain?: boolean;
  color?: string;
  properties?: PublishMessageProperties;
  type?: string;
}

export interface PublishMessageProperties {
  contentType?: string;
  payloadFormatIndicator?: boolean;
  messageExpiryInterval?: number;
  topicAlias?: number;
  correlationData?: Buffer;
  responseTopic?: string;
  userProperties?: any,
}

export declare type UserProperties = {[index: string]: string | string[]}

interface TopicObject {
  [topicName: string]: { qos: QoS };
}

type Topic = string | string[] | TopicObject;

export interface WebSocketSubscription extends BaseData {
  webSocketConnectionId: string;
  configuration: WebSocketSubscriptionConfiguration;
}

export interface WebSocketSubscriptionConfiguration {
  topicFilter?: string;
  qos?: QoS;
  color?: string;
  options: WebSocketSubscriptionOptions;
}

export interface WebSocketSubscriptionOptions {
  noLocal?: boolean;
  retainAsPublish?: boolean;
  retainHandling?: number;
}

interface WsSubscriptionOptions {
  qos?: QoS;
  nl?: boolean;
  rap?: boolean;
  rh?: number;
}

export interface WsSubscription extends BaseData {
  topic: Topic; // TODO rename topicfilter
  color: string;
  options?: WsSubscriptionOptions;
}

export enum WsAddressProtocolType {
  WS = 'WS',
  WSS = 'WSS'
}

export enum WsCredentialsGeneratorType {
  AUTO = 'AUTO',
  CUSTOM = 'CUSTOM',
  EXISTING = 'EXISTING'
}

export const WsCredentialsGeneratortTypeTranslationMap = new Map<WsCredentialsGeneratorType, string>(
  [
    [WsCredentialsGeneratorType.AUTO, 'ws-client.connections.credentials-auto-generated'],
    [WsCredentialsGeneratorType.CUSTOM, 'ws-client.connections.credentials-custom'],
    [WsCredentialsGeneratorType.EXISTING, 'ws-client.connections.credentials-existing']
  ]
);

export const wsAddressProtocolTypeValueMap = new Map<WsAddressProtocolType, MqttJsProtocolSecurity>(
  [
    [WsAddressProtocolType.WS, 'ws://'],
    [WsAddressProtocolType.WSS, 'wss://']
  ]
);

export const WsAddressProtocols = [
  {
    value: 'ws://'
  },
  {
    value: 'wss://'
  }
];

export const MqttVersions = [
  {
    value: 3,
    name: 'MQTT 3.1'
  },
  {
    value: 4,
    name: 'MQTT 3.1.1'
  },
  {
    value: 5,
    name: 'MQTT 5'
  }
];

export const rhOptions = [
  {
    value: 0,
    name: '0 - Send retained messages at the time of the subscribe'
  },
  {
    value: 1,
    name: '1 - Send retained messages at subscribe only if the subscription does not currently exist'
  },
  {
    value: 2,
    name: '2 - Do not send retained messages at the time of the subscribe'
  }
];

export const mqttPropertyTimeUnit = [
  {
    value: 3,
    name: 'MQTT 3.1'
  },
  {
    value: 4,
    name: 'MQTT 3.1.1'
  },
  {
    value: 5,
    name: 'MQTT 5'
  }
];

export enum WebSocketTimeUnit {
  MILLISECONDS = 'MILLISECONDS',
  SECONDS = 'SECONDS',
  MINUTES = 'MINUTES',
  HOURS = 'HOURS'
}

export const timeUnitTypeTranslationMap = new Map<WebSocketTimeUnit, string>(
  [
    [WebSocketTimeUnit.MILLISECONDS, 'timeunit.milliseconds'],
    [WebSocketTimeUnit.SECONDS, 'timeunit.seconds'],
    [WebSocketTimeUnit.MINUTES, 'timeunit.minutes'],
    [WebSocketTimeUnit.HOURS, 'timeunit.hours']
  ]
);

export enum DataSizeUnitType {
  BYTE = 'BYTE',
  KILOBYTE = 'KILOBYTE',
  MEGABYTE = 'MEGABYTE'
}

export const dataSizeUnitTypeTranslationMap = new Map<DataSizeUnitType, string>(
  [
    [DataSizeUnitType.BYTE, 'B'],
    [DataSizeUnitType.KILOBYTE, 'KB'],
    [DataSizeUnitType.MEGABYTE, 'MB']
  ]
);

export const WsClientMessageTypeTranslationMap = new Map<boolean, string>(
  [
    [true, 'ws-client.messages.received'],
    [false, 'ws-client.messages.published']
  ]
);

export const WsMessagesTypeFilters = [
  {
    name: 'All',
    value: 'all'
  },
  {
    name: 'Received',
    value: 'received'
  },
  {
    name: 'Published',
    value: 'published'
  }
];

export const WsPayloadFormats = [
  {
    value: ValueType.JSON,
    name: 'value.json'
  },
  {
    value: ValueType.STRING,
    name: 'value.string'
  }
];
