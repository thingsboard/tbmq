///
/// Copyright © 2016-2025 The Thingsboard Authors
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
import { ClientType } from '@shared/models/client.model';
import { BasicCredentials, CertPemCredentials, Credentials } from '@shared/models/integration.models';
import { AuthRules } from '@shared/models/credentials.model';

export enum MqttAuthProviderType {
  MQTT_BASIC = 'MQTT_BASIC',
  X_509 = 'X_509',
  SCRAM = 'SCRAM',
  JWT = 'JWT',
  HTTP_SERVICE = 'HTTP_SERVICE'
}

export const mqttAuthProviderTypeTranslationMap = new Map<MqttAuthProviderType, string>(
  [
    [MqttAuthProviderType.MQTT_BASIC, 'mqtt-client-credentials.type-basic'],
    [MqttAuthProviderType.X_509, 'mqtt-client-credentials.type-ssl'],
    [MqttAuthProviderType.SCRAM, 'mqtt-client-credentials.type-scram'],
    [MqttAuthProviderType.JWT, 'authentication.type-jwt'],
    [MqttAuthProviderType.HTTP_SERVICE, 'authentication.type-http-service'],
  ]
);

export interface MqttAuthProvider extends ShortMqttAuthProvider {
  configuration: BasicMqttAuthProviderConfiguration | ScramMqttAuthProviderConfiguration | SslMqttAuthProviderConfiguration | JwtMqttAuthProviderConfiguration;
}

export interface ShortMqttAuthProvider extends BaseData {
  type: MqttAuthProviderType;
  enabled: boolean;
  description?: string;
}

export interface BasicMqttAuthProviderConfiguration {}

export interface ScramMqttAuthProviderConfiguration {}

export interface SslMqttAuthProviderConfiguration {
  skipValidityCheckForClientCert: boolean;
}

export interface JwtMqttAuthProviderConfiguration {
  type: MqttAuthProviderType;
  jwtVerifierType: JwtVerifierType;
  defaultClientType: ClientType;
  authRules?: AuthRules;
  authClaims?: {[key: string]: string} | null;
  clientTypeClaims?: {[key: string]: string} | null;
  jwtVerifierConfiguration: JwksVerifierConfiguration
}

export interface JwksVerifierConfiguration {
  endpoint?: string;
  refreshInterval?: number;
  ssl?: boolean;
  credentials?: Credentials | BasicCredentials | CertPemCredentials;
  headers?: {[key: string]: string} | null;
  secret?: string
  publicKey?: string;
}

export enum JwtVerifierType {
  ALGORITHM_BASED = 'ALGORITHM_BASED',
  JWKS = 'JWKS',
}

export enum JwtAlgorithmType {
  HMAC_BASED = 'HMAC_BASED',
  PUBLIC_KEY = 'PUBLIC_KEY'
}

export const JwtAlgorithmTypeTranslation = new Map<JwtAlgorithmType, string>(
  [
    [JwtAlgorithmType.HMAC_BASED, 'authentication.hmac'],
    [JwtAlgorithmType.PUBLIC_KEY, 'authentication.public-key']
  ]
);
