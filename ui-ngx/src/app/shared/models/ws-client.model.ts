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
import { UserProperties } from '@home/components/client-credentials-templates/user-properties.component';
import { MqttQoS } from '@shared/models/session.model';

export interface Connection extends BaseData {
  name: string;
  connected?: boolean;
}

export interface ConnectionDetailed extends Connection {
  protocol: string;
  host: string;
  port: number;
  path: string;
  clientId: string;
  username: string;
  password: string;
  keepAlive: number;
  reconnectPeriod: number;
  connectTimeout: number;
  clean: boolean;
  protocolVersion: string;
  properties: ConnectionProperties;
  userProperties: UserProperties;
  will: ConnectionWill;
  subscriptions?: any[];
}

export interface ConnectionProperties {
  sessionExpiryInterval: number;
  receiveMaximum: number;
  maximumPacketSize: number;
  topicAliasMaximum: number;
  requestResponseInformation: boolean;
  requestProblemInformation: boolean;
}

export interface ConnectionWill {

}

export interface WsMessage extends PublishMessageProperties {
  createdTime: number;
  topic: string;
  qos: number;
  retain: boolean;
  payload: any;
  color?: string;
}

export interface PublishMessageProperties {
  contentType?: string;
  payloadFormatIndicator?: boolean;
  messageExpiryInterval?: number;
  topicAlias?: number;
  subscriptionIdentifier?: number;
  correlationData?: string;
  responseTopic?: string;
  userProperties?: any
}

export interface SubscriptionTopicFilter extends BaseData {
  topic: string;
  qos: number;
  color?: string;
}

export interface SubscriptionTopicFilterDetailed extends SubscriptionTopicFilter {
  nl: boolean;
  rap: boolean;
  rh: boolean;
  subscriptionIdentifier: number;
}

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
