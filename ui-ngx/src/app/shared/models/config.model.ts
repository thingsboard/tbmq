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

export interface BrokerConfig {
  tcpPort: number;
  tlsPort: number;
  tcpMaxPayloadSize: number;
  tlsMaxPayloadSize: number;
  tcpListenerEnabled: boolean;
  tlsListenerEnabled: boolean;
  basicAuthEnabled: boolean;
  x509AuthEnabled: boolean;
  wsPort: number;
  wssPort: number;
  wsListenerEnabled: boolean;
  wssListenerEnabled: boolean;
  wsMaxPayloadSize: number;
  wssMaxPayloadSize: number;
  existsBasicCredentials: boolean;
  existsX509Credentials: boolean;
}

export interface BrokerConfigTable extends BaseData {
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
  wsPort = 'wsPort',
  wssPort = 'wssPort',
  wsListenerEnabled = 'wsListenerEnabled',
  wssListenerEnabled = 'wssListenerEnabled',
  wsMaxPayloadSize = 'wsMaxPayloadSize',
  wssMaxPayloadSize = 'wssMaxPayloadSize',
  existsBasicCredentials = 'existsBasicCredentials',
  existsX509Credentials = 'existsX509Credentials'
}

export const ConfigParamsTranslationMap = new Map<ConfigParams, string>(
  [
    [ConfigParams.tcpPort, 'config.port-mqtt'],
    [ConfigParams.tcpListenerEnabled, 'config.tcp-listener'],
    [ConfigParams.tcpMaxPayloadSize, 'config.tcp-listener-max-payload-size'],
    [ConfigParams.tlsListenerEnabled, 'config.tls-listener'],
    [ConfigParams.tlsMaxPayloadSize, 'config.tls-listener-max-payload-size'],
    [ConfigParams.tlsPort, 'config.tls-tcp-port'],
    [ConfigParams.basicAuthEnabled, 'config.basic-auth'],
    [ConfigParams.x509AuthEnabled, 'config.ssl-auth'],
    [ConfigParams.wsPort, 'config.ws-port'],
    [ConfigParams.wssPort, 'config.wss-port'],
    [ConfigParams.wsListenerEnabled, 'config.ws-listener'],
    [ConfigParams.wssListenerEnabled, 'config.wss-listener'],
    [ConfigParams.wsMaxPayloadSize, 'config.ws-listener-max-payload-size'],
    [ConfigParams.wssMaxPayloadSize, 'config.wss-listener-max-payload-size']
  ]
);

export interface SystemVersionInfo {
  version: string;
  artifact: string;
  name: string;
}
