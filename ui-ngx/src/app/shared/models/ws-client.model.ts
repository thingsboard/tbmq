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
  keepaliveUnit?: TimeUnitType;
  connectTimeoutUnit?: TimeUnitType;
  reconnectPeriodUnit?: TimeUnitType;
  clientCredentials?: Partial<ClientCredentials>;
  clientCredentialsId?: string;
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
  will?: {
    topic?: string;
    payload?: any;
    payloadType?: any;
    qos?: number;
    retain?: boolean;
    properties?: WillProperties;
  };
}

interface TbConnectionProperties {
  sessionExpiryIntervalUnit?: TimeUnitType;
  maximumPacketSizeUnit?: DataSizeUnitType;
}

export interface ConnectionProperties extends TbConnectionProperties{
  sessionExpiryInterval?: number;
  receiveMaximum?: number;
  maximumPacketSize?: number;
  topicAliasMaximum?: number;
  requestResponseInformation?: boolean;
  requestProblemInformation?: boolean;
  // authenticationMethod?: string;
  // authenticationData?: any;
  userProperties?: {
    [key: string]: string;
  };
}

interface TbWillProperties {
  willDelayIntervalUnit?: TimeUnitType;
  messageExpiryIntervalUnit?: TimeUnitType;
}

interface WillProperties extends TbWillProperties {
  willDelayInterval: number;
  payloadFormatIndicator: boolean;
  messageExpiryInterval: number;
  contentType: string;
  responseTopic: string;
  correlationData: any; // TODO double check how to send
}

export interface WsPublishMessageOptions {
  qos?: number;
  retain?: boolean;
  dup?: boolean;
  properties?: PublishMessageProperties;
}

export interface WsTableMessage extends BaseData {
  createdTime?: number;
  topic?: string;
  payload?: any;
  options?: WsPublishMessageOptions;
  qos?: number;
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
  // subscriptionIdentifier?: number | number[],
  correlationData?: Buffer;
  responseTopic?: string;
  userProperties?: UserProperties,
}

export declare type UserProperties = {[index: string]: string | string[]}

interface TopicObject {
  [topicName: string]: { qos: number };
}

type Topic = string | string[] | TopicObject;

interface Properties {
  // subscriptionIdentifier: number;
  // userProperties: {
  //   [name: string]: string
  // };
}

interface WsSubscriptionOptions {
  qos?: number;
  nl?: boolean;
  rap?: boolean;
  rh?: number;
  // properties?: Properties;
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

export const addressProtocols = [
  {
    value: 'ws://'
  },
  {
    value: 'wss://'
  }
];

export const mqttVersions = [
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

export const TimeUnitTypesV2 = [
  {
    value: 'MILLISECONDS',
    name: 'timeunit.milliseconds'
  },
  {
    value: 'SECONDS',
    name: 'timeunit.seconds'
  },
  {
    value: 'MINUTES',
    name: 'timeunit.minutes'
  },
  {
    value: 'HOURS',
    name: 'timeunit.milliseconds'
  }
];

export enum TimeUnitType {
  MILLISECONDS = 'MILLISECONDS',
  SECONDS = 'SECONDS',
  MINUTES = 'MINUTES',
  HOURS = 'HOURS'
}

export const timeUnitTypeTranslationMap = new Map<TimeUnitType, string>(
  [
    [TimeUnitType.MILLISECONDS, 'timeunit.milliseconds'],
    [TimeUnitType.SECONDS, 'timeunit.seconds'],
    [TimeUnitType.MINUTES, 'timeunit.minutes'],
    [TimeUnitType.HOURS, 'timeunit.hours']
  ]
);

export enum DataSizeUnitType {
  B = 'B',
  KB = 'KB',
  MB = 'MB'
}

export const dataSizeUnitTypeTranslationMap = new Map<DataSizeUnitType, string>(
  [
    [DataSizeUnitType.B, 'B'],
    [DataSizeUnitType.KB, 'KB'],
    [DataSizeUnitType.MB, 'MB']
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
