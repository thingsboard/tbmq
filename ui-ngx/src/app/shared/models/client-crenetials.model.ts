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

import {BaseData} from '@shared/models/base-data';
import {ClientType} from '@shared/models/client.model';

export enum MqttCredentialsType {
  MQTT_BASIC = 'MQTT_BASIC',
  SSL = 'SSL'
}

export const credentialsTypeNames = new Map<MqttCredentialsType, string>(
  [
    [MqttCredentialsType.MQTT_BASIC, 'Basic'],
    [MqttCredentialsType.SSL, 'X.509 Certificate chain']
  ]
);

export const credentialsWarningTranslations = new Map<MqttCredentialsType, string>(
  [
    [MqttCredentialsType.MQTT_BASIC, 'Basic Auth disabled'],
    [MqttCredentialsType.SSL, 'X.509 Certificate Auth disabled']
  ]
);

export interface MqttClientCredentials extends BaseData {
  credentialsId: string;
  name: string;
  clientType: ClientType;
  credentialsType: MqttCredentialsType;
  credentialsValue: string;
}

export interface SslMqttCredentials extends SslAuthRulesMapping {
  certCommonName: string;
}

export interface SslAuthRulesMapping {
  authRulesMapping: Array<SslMqttCredentialsAuthRules>;
}

export interface SslMqttCredentialsAuthRules {
  [key: string]: AuthRules;
}

export interface BasicMqttCredentials {
  clientId: string;
  userName: string;
  password: string;
  authRules: AuthRules;
}

export interface AuthRules {
  subAuthRulePatterns: any;
  pubAuthRulePatterns: any;
}

export interface AuthRulesMapping extends AuthRules {
  certificateMatcherRegex?: string;
}

export enum AuthRulePatternsType {
  SUBSCRIBE = 'SUBSCRIBE',
  PUBLISH = 'PUBLISH'
}

export interface ClientCredentialsInfo {
  deviceCredentialsCount: number;
  applicationCredentialsCount: number;
  totalCount: number;
}
