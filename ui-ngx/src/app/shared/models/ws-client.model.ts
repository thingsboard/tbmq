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

import { BaseData } from '@shared/models/base-data';
import { ClientCredentials } from '@shared/models/credentials.model';
import { ValueType } from '@shared/models/constants';
import { randomAlphanumeric } from '@core/utils';
import { colorPresetsHex } from '@shared/components/color-picker/color-picker.component';

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

export type MqttJsProtocolVersion = 3 | 4 | 5;
export type MqttJsProtocolId = 'MQTT' | 'MQIsdp';
export type MqttJsProtocolSecurity = 'ws://' | 'wss://';
export declare type QoS = 0 | 1 | 2;
export const WebSocketConnectionsLimit = 100;

export interface ConnectionStatusLog {
  createdTime?: number;
  status: ConnectionStatus;
  details?: string;
}

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
  clientId: string;
  username?: string;
  password?: string;
  passwordRequired: boolean;
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
  // requestProblemInfo?: boolean;
  lastWillMsg?: LastWillMsg;
  userProperties?: any;
  rejectUnauthorized?: boolean;
}

export interface LastWillMsg {
  topic?: string;
  qos?: QoS;
  payload?: Buffer;
  payloadType?: ValueType;
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
  // requestProblemInformation?: boolean;
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
  messageExpiryIntervalUnit?: WebSocketTimeUnit;
  topicAlias?: number;
  correlationData?: Buffer;
  responseTopic?: string;
  userProperties?: any;
  changed?: boolean;
}

export interface WebSocketSubscription extends BaseData {
  webSocketConnectionId: string;
  configuration: WebSocketSubscriptionConfiguration;
}

export interface WebSocketSubscriptionConfiguration {
  topicFilter?: string;
  qos?: QoS;
  color?: string;
  options: SubscriptionOptions;
}

export interface SubscriptionOptions {
  noLocal?: boolean;
  retainAsPublish?: boolean;
  retainHandling?: number;
}

export interface MessageFilterConfig {
  topic?: string;
  qosList?: number[];
  retainList?: boolean[];
  type?: string;
}

export interface MessageCounters {
  all: number;
  received: number;
  published: number;
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
export enum WebSocketTimeUnit {
  MILLISECONDS = 'MILLISECONDS',
  SECONDS = 'SECONDS',
  MINUTES = 'MINUTES',
  HOURS = 'HOURS'
}
export enum AboveSecWebSocketTimeUnit {
  SECONDS = 'SECONDS',
  MINUTES = 'MINUTES',
  HOURS = 'HOURS'
}
export enum DataSizeUnitType {
  BYTE = 'BYTE',
  KILOBYTE = 'KILOBYTE',
  MEGABYTE = 'MEGABYTE',
  GIGABYTE = 'GIGABYTE'
}

export function transformPropsToObject(input: {props: Array<{k: string, v: number|string}>}): {[key: string]: number|string|Array<number|string>} {
  return input.props.reduce((result: {[key: string]: number|string|Array<number|string>}, current) => {
    if (Object.prototype.hasOwnProperty.call(result,current.k)) {
      if (Array.isArray(result[current.k])) {
        // @ts-ignore
        result[current.k].push(current.v);
      } else {
        // @ts-ignore
        result[current.k] = [result[current.k], current.v];
      }
    } else {
      result[current.k] = current.v;
    }
    return result;
  }, {});
}
export function transformObjectToProps(input: {[key: string]: number|string|Array<number|string>}): {props: Array<{k: string, v: number|string}>}  {
  let props = Object.entries(input).flatMap(([k, v]) => {
    if (Array.isArray(v)) {
      return v.map(value => ({k, v: value}));
    } else {
      return [{k, v}];
    }
  });
  return { props };
}

export const defaultPublishTopic = 'sensors/temperature';
export const defaultSubscriptionTopicFilter = 'sensors/#';
export const clientIdRandom = () => 'tbmq_' + randomAlphanumeric(8);
export const clientUserNameRandom = () => 'tbmq_un_' + randomAlphanumeric(8);
export const clientCredentialsNameRandom = (number = randomAlphanumeric(8)) => 'WebSocket Credentials ' + number;
export const connectionName = (number = 0) => 'WebSocket Connection ' + number;
export const colorRandom = () => {
  const randomIndex = Math.floor(Math.random() * colorPresetsHex.length);
  return colorPresetsHex[randomIndex];
}
export const isDefinedProps = (obj: any): boolean => {
  let count = 0;
  for (let key in obj) {
    if (obj[key] !== null && obj[key] !== '' && key !== 'messageExpiryIntervalUnit') {
      count++;
    }
  }
  return count > 0;
}

export const MessageFilterDefaultConfigAll: MessageFilterConfig = {
  type: 'all',
  topic: null,
  qosList: null,
  retainList: null
};
export const MessageFilterDefaultConfig: MessageFilterConfig = {
  topic: null,
  qosList: null,
  retainList: null
};
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
export const RhOptions = [
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

export const WsAddressProtocolTypeValueMap = new Map<WsAddressProtocolType, MqttJsProtocolSecurity>([
  [WsAddressProtocolType.WS, 'ws://'],
  [WsAddressProtocolType.WSS, 'wss://']
]);
export const WsCredentialsGeneratortTypeTranslationMap = new Map<WsCredentialsGeneratorType, string>([
    [WsCredentialsGeneratorType.AUTO, 'ws-client.connections.credentials-auto-generated'],
    [WsCredentialsGeneratorType.CUSTOM, 'ws-client.connections.credentials-custom'],
    [WsCredentialsGeneratorType.EXISTING, 'ws-client.connections.credentials-existing']
  ]);
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
export const DataSizeUnitTypeTranslationMap = new Map<DataSizeUnitType, string>([
    [DataSizeUnitType.BYTE, 'B'],
    [DataSizeUnitType.KILOBYTE, 'KB'],
    [DataSizeUnitType.MEGABYTE, 'MB'],
    [DataSizeUnitType.GIGABYTE, 'GB']
  ]);
export const TimeUnitTypeTranslationMap = new Map<WebSocketTimeUnit, string>([
    [WebSocketTimeUnit.MILLISECONDS, 'timeunit.milliseconds'],
    [WebSocketTimeUnit.SECONDS, 'timeunit.seconds'],
    [WebSocketTimeUnit.MINUTES, 'timeunit.minutes'],
    [WebSocketTimeUnit.HOURS, 'timeunit.hours']
  ]);
export const WsClientMessageTypeTranslationMap = new Map<boolean, string>([
    [true, 'ws-client.messages.received'],
    [false, 'ws-client.messages.published']
  ]);

export const DisconnectReasonCodes = {
  0: 'Normal disconnection',
  4: 'Disconnect with Will Message',
  128: 'Unspecified error',
  129: 'Malformed Packet',
  130: 'Protocol Error',
  131: 'Implementation specific error',
  135: 'Not authorized',
  137: 'Server busy',
  139: 'Server shutting down',
  140: 'Bad authentication method',
  141: 'Keep Alive timeout',
  142: 'Session taken over',
  143: 'Topic Filter invalid',
  144: 'Topic Name invalid',
  147: 'Receive Maximum exceeded',
  148: 'Topic Alias invalid',
  149: 'Packet too large',
  150: 'Message rate too high',
  151: 'Quota exceeded',
  152: 'Administrative action',
  153: 'Payload format invalid',
  154: 'Retain not supported',
  155: 'QoS not supported',
  156: 'Use another server',
  157: 'Server moved',
  158: 'Shared Subscriptions not supported',
  159: 'Connection rate exceeded',
  160: 'Maximum connect time',
  161: 'Subscription Identifiers not supported',
  162: 'Wildcard Subscriptions not supported'
}
