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

import { BaseData } from '@shared/models/base-data';
import { MqttAuthProviderType } from '@shared/models/mqtt-auth-provider.model';

export interface BrokerConfig {
  tcpPort: number;
  tlsPort: number;
  tcpMaxPayloadSize: number;
  tlsMaxPayloadSize: number;
  tcpListenerEnabled: boolean;
  tlsListenerEnabled: boolean;
  basicAuthEnabled: boolean;
  x509AuthEnabled: boolean;
  scramAuthEnabled: boolean;
  jwtAuthEnabled: boolean;
  wsPort: number;
  wssPort: number;
  wsListenerEnabled: boolean;
  wssListenerEnabled: boolean;
  wsMaxPayloadSize: number;
  wssMaxPayloadSize: number;
  existsBasicCredentials: boolean;
  existsX509Credentials: boolean;
  existsScramCredentials: boolean;
  allowKafkaTopicDeletion: boolean;
}

export interface BrokerConfigTableParam extends BaseData {
  key: ConfigParams;
  value: any;
}

export enum ConfigParams {
  tcpPort = 'tcpPort',
  tcpListenerEnabled = 'tcpListenerEnabled',
  tcpMaxPayloadSize = 'tcpMaxPayloadSize',
  tlsListenerEnabled = 'tlsListenerEnabled',
  tlsMaxPayloadSize = 'tlsMaxPayloadSize',
  tlsPort = 'tlsPort',
  basicAuthEnabled = 'basicAuthEnabled',
  x509AuthEnabled = 'x509AuthEnabled',
  scramAuthEnabled = 'scramAuthEnabled',
  jwtAuthEnabled = 'jwtAuthEnabled',
  wsPort = 'wsPort',
  wssPort = 'wssPort',
  wsListenerEnabled = 'wsListenerEnabled',
  wssListenerEnabled = 'wssListenerEnabled',
  wsMaxPayloadSize = 'wsMaxPayloadSize',
  wssMaxPayloadSize = 'wssMaxPayloadSize',
  existsBasicCredentials = 'existsBasicCredentials',
  existsX509Credentials = 'existsX509Credentials',
  existsScramCredentials = 'existsScramCredentials',
  allowKafkaTopicDeletion = 'allowKafkaTopicDeletion',
}

export const ConfigParamTranslationMap = new Map<ConfigParams, string>(
  [
    [ConfigParams.tcpPort, 'config.port-mqtt'],
    [ConfigParams.tcpListenerEnabled, 'config.tcp-listener'],
    [ConfigParams.tcpMaxPayloadSize, 'config.tcp-listener-max-payload-size'],
    [ConfigParams.tlsListenerEnabled, 'config.tls-listener'],
    [ConfigParams.tlsMaxPayloadSize, 'config.tls-listener-max-payload-size'],
    [ConfigParams.tlsPort, 'config.tls-tcp-port'],
    [ConfigParams.basicAuthEnabled, 'config.basic-auth'],
    [ConfigParams.x509AuthEnabled, 'config.ssl-auth'],
    [ConfigParams.scramAuthEnabled, 'config.scram-auth'],
    [ConfigParams.wsPort, 'config.ws-port'],
    [ConfigParams.wssPort, 'config.wss-port'],
    [ConfigParams.wsListenerEnabled, 'config.ws-listener'],
    [ConfigParams.wssListenerEnabled, 'config.wss-listener'],
    [ConfigParams.wsMaxPayloadSize, 'config.ws-listener-max-payload-size'],
    [ConfigParams.wssMaxPayloadSize, 'config.wss-listener-max-payload-size'],
    [ConfigParams.jwtAuthEnabled, 'config.jwt-auth'],
    [ConfigParams.allowKafkaTopicDeletion, 'config.allow-kafka-topic-deletion'],
  ]
);

export interface SystemVersionInfo {
  version: string;
  artifact: string;
  name: string;
  newestVersion: string;
}

export const ConfigParamAuthProviderTypeMap = new Map<ConfigParams, MqttAuthProviderType>(
  [
    [ConfigParams.basicAuthEnabled, MqttAuthProviderType.MQTT_BASIC],
    [ConfigParams.x509AuthEnabled, MqttAuthProviderType.X_509],
    [ConfigParams.jwtAuthEnabled, MqttAuthProviderType.JWT],
    [ConfigParams.scramAuthEnabled, MqttAuthProviderType.SCRAM],
  ]
);

export const ConfigParamAuthProviderTranslationMap = new Map<ConfigParams, string>(
  [
    [ConfigParams.basicAuthEnabled, 'mqtt-client-credentials.type-basic'],
    [ConfigParams.x509AuthEnabled, 'mqtt-client-credentials.type-ssl'],
    [ConfigParams.scramAuthEnabled, 'mqtt-client-credentials.type-scram'],
  ]
);

export const settingsConfigPortMap = new Map<string, string>(
  [
    ['mqtt', 'tcpPort'],
    ['mqtts', 'tlsPort'],
    ['ws', 'wsPort'],
    ['wss', 'wssPort'],
  ]
);

export const allowedFlagKeys = [ConfigParams.allowKafkaTopicDeletion];
export const customValueKeys = [...allowedFlagKeys];
