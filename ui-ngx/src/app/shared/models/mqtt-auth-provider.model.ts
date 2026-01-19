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
import { ClientType } from '@shared/models/client.model';
import { BasicCredentials, CertPemCredentials, Credentials, HttpRequestType } from '@shared/models/integration.models';
import { AuthRules } from '@shared/models/credentials.model';

export enum MqttAuthProviderType {
  MQTT_BASIC = 'MQTT_BASIC',
  X_509 = 'X_509',
  SCRAM = 'SCRAM',
  JWT = 'JWT',
  HTTP = 'HTTP'
}

export const mqttAuthProviderTypeTranslationMap = new Map<MqttAuthProviderType, string>(
  [
    [MqttAuthProviderType.MQTT_BASIC, 'mqtt-client-credentials.type-basic'],
    [MqttAuthProviderType.X_509, 'mqtt-client-credentials.type-ssl'],
    [MqttAuthProviderType.SCRAM, 'mqtt-client-credentials.type-scram'],
    [MqttAuthProviderType.JWT, 'authentication.type-jwt'],
    [MqttAuthProviderType.HTTP, 'authentication.type-http'],
  ]
);

export interface MqttAuthProvider extends ShortMqttAuthProvider {
  configuration: SharedMqttAuthProviderConfiguration &
    (
      BasicMqttAuthProviderConfiguration |
      ScramMqttAuthProviderConfiguration |
      SslMqttAuthProviderConfiguration |
      HttpMqttAuthProviderConfiguration |
      JwtMqttAuthProviderConfiguration
    );
  additionalInfo: any;
}

export interface ShortMqttAuthProvider extends BaseData, SharedMqttAuthProviderConfiguration {
  enabled: boolean;
  description?: string;
}

export interface SharedMqttAuthProviderConfiguration {
  type: MqttAuthProviderType;
}


export interface BasicMqttAuthProviderConfiguration {
  authStrategy: MqttBasicAuthenticationStrategy;
}

export interface ScramMqttAuthProviderConfiguration {}

export interface SslMqttAuthProviderConfiguration {
  skipValidityCheckForClientCert: boolean;
}

export interface HttpMqttAuthProviderConfiguration {
  restEndpointUrl: string;
  requestMethod: HttpRequestType;
  credentials: Credentials;
  headers: {[key: string]: string} | null;
  requestBody: string;
  defaultClientType: ClientType;
  authRules: AuthRules;
  readTimeoutMs: number;
  maxInMemoryBufferSizeInKb: number;
  maxParallelRequestsCount: number;
}

export interface JwtMqttAuthProviderConfiguration {
  type: MqttAuthProviderType;
  jwtVerifierType: JwtVerifierType;
  defaultClientType: ClientType;
  authRules?: AuthRules;
  authClaims?: {[key: string]: string} | null;
  clientTypeClaims?: {[key: string]: string} | null;
  jwtVerifierConfiguration: JwksVerifierConfiguration;
}

export interface JwksVerifierConfiguration {
  jwtVerifierType?: JwtVerifierType;
  jwtSignAlgorithmConfiguration?: JwtSignAlgorithmConfiguration;
  algorithm?: JwtAlgorithmType;
  endpoint?: string;
  refreshInterval?: number;
  ssl?: boolean;
  credentials?: Credentials | BasicCredentials | CertPemCredentials;
  headers?: {[key: string]: string} | null;
}

export enum JwtVerifierType {
  ALGORITHM_BASED = 'ALGORITHM_BASED',
  JWKS = 'JWKS',
}

export enum JwtAlgorithmType {
  HMAC_BASED = 'HMAC_BASED',
  PEM_KEY = 'PEM_KEY'
}

export const JwtAlgorithmTypeTranslation = new Map<JwtAlgorithmType, string>(
  [
    [JwtAlgorithmType.HMAC_BASED, 'authentication.hmac'],
    [JwtAlgorithmType.PEM_KEY, 'authentication.public-key']
  ]
);

export interface JwtSignAlgorithmConfiguration {
  algorithm?: JwtAlgorithmType;
  secret?: string;
  publicPemKey?: string;
}

export const mqttAuthProviderTypeHelpLinkMap = new Map<MqttAuthProviderType, string>(
  [
    [MqttAuthProviderType.MQTT_BASIC, 'providerBasic'],
    [MqttAuthProviderType.X_509, 'providerX509'],
    [MqttAuthProviderType.SCRAM, 'providerScram'],
    [MqttAuthProviderType.JWT, 'providerJwt'],
    [MqttAuthProviderType.HTTP, 'providerHttp'],
  ]
);

export enum ClientAuthType {
  CLIENT_AUTH_REQUIRED = 'CLIENT_AUTH_REQUIRED',
  CLIENT_AUTH_REQUESTED = 'CLIENT_AUTH_REQUESTED',
}

export const ClientAuthTypeTranslationMap = new Map<ClientAuthType, string>(
  [
    [ClientAuthType.CLIENT_AUTH_REQUIRED, 'authentication.client-auth-type-required'],
    [ClientAuthType.CLIENT_AUTH_REQUESTED, 'authentication.client-auth-type-requested'],
  ]
);

export enum MqttBasicAuthenticationStrategy {
  CLIENT_ID_AND_USERNAME = 'CLIENT_ID_AND_USERNAME',
  CLIENT_ID = 'CLIENT_ID',
  USERNAME = 'USERNAME',
}

export const MqttBasicAuthenticationStrategyTranslationMap = new Map<MqttBasicAuthenticationStrategy, string>(
  [
    [MqttBasicAuthenticationStrategy.CLIENT_ID_AND_USERNAME, 'authentication.basic-authentication-by-client-id-username'],
    [MqttBasicAuthenticationStrategy.CLIENT_ID, 'authentication.basic-authentication-by-client-id'],
    [MqttBasicAuthenticationStrategy.USERNAME, 'authentication.basic-authentication-by-username'],
  ]
);

export const MqttBasicAuthenticationStrategyHintTranslationMap = new Map<MqttBasicAuthenticationStrategy, string>(
  [
    [MqttBasicAuthenticationStrategy.CLIENT_ID_AND_USERNAME, 'authentication.basic-authentication-by-client-id-username-hint'],
    [MqttBasicAuthenticationStrategy.CLIENT_ID, 'authentication.basic-authentication-by-client-id-hint'],
    [MqttBasicAuthenticationStrategy.USERNAME, 'authentication.basic-authentication-by-username-hint'],
  ]
);

export function getProviderHelpLink(entity: ShortMqttAuthProvider): string {
  if (entity && entity.type) {
    if (mqttAuthProviderTypeTranslationMap.has(entity.type)) {
      return mqttAuthProviderTypeHelpLinkMap.get(entity.type);
    }
  }
  return 'securitySettings';
}

export const UNKNOWN_AUTH_PROVIDER = 'Unknown' as MqttAuthProviderType;
