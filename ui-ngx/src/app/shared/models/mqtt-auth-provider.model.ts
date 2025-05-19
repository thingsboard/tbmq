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
import { CredentialsType } from '@shared/models/credentials.model';
import { ClientType } from '@shared/models/client.model';
import { BasicCredentials, CertPemCredentials, Credentials } from '@shared/models/integration.models';
import { PageData } from '@shared/models/page/page-data';

export interface MqttAuthProvider extends ShortMqttAuthProvider {
  configuration: BasicMqttAuthProviderConfiguration | ScramMqttAuthProviderConfiguration | SslMqttAuthProviderConfiguration | JwtMqttAuthProviderConfiguration;
}

export interface ShortMqttAuthProvider extends BaseData {
  type: CredentialsType;
  enabled: boolean;
  description?: string;
}

export interface BasicMqttAuthProviderConfiguration {}

export interface ScramMqttAuthProviderConfiguration {}

export interface SslMqttAuthProviderConfiguration {
  skipValidityCheckForClientCert: boolean;
}

export interface JwtMqttAuthProviderConfiguration {
  jwtVerifierType: JwtVerifierType;
  defaultClientType: ClientType;
  authClaims?: {[key: string]: string} | null;
  clientTypeClaims?: {[key: string]: string} | null;
}

export interface JwksVerifierConfiguration {
  endpoint: string;
  refreshInterval: number;
  ssl: boolean;
  credentials: Credentials | BasicCredentials | CertPemCredentials;
  headers: {[key: string]: string} | null;
}

export enum JwtVerifierType {
  ALGORITHM_BASED= 'ALGORITHM_BASED',
  JWKS= 'JWKS',
}

export const mockProviders: MqttAuthProvider[] = [
  {
    id: "7d6999ff-cdd0-4175-9a34-fa2e53f8e251",
    createdTime: 1747383888393,
    type: CredentialsType.MQTT_BASIC,
    enabled: true,
    configuration: {}
  },
  {
    id: "7d6999ff-cdd0-4175-9a34-fa2e53f8e252",
    createdTime: 1747383888393,
    type: CredentialsType.SCRAM,
    enabled: true,
    configuration: {}
  },
  {
    id: "7d6999ff-cdd0-4175-9a34-fa2e53f8e253",
    createdTime: 1747383888393,
    type: CredentialsType.SSL,
    enabled: true,
    configuration: {
      skipValidityCheckForClientCert: true
    }
  },
  {
    id: "7d6999ff-cdd0-4175-9a34-fa2e53f8e254",
    createdTime: 1747383888393,
    type: CredentialsType.JWT,
    enabled: false,
    configuration: {
      jwtVerifierType: JwtVerifierType.ALGORITHM_BASED,
      defaultClientType: ClientType.DEVICE,
      authClaims: null,
      clientTypeClaims: null
    }
  },
]

export const mockProvidersPageData: PageData<ShortMqttAuthProvider> = {
  data: [
    {
      id: "7d6999ff-cdd0-4175-9a34-fa2e53f8e251",
      createdTime: 1747383888391,
      type: CredentialsType.MQTT_BASIC,
      enabled: true,
      description: 'Uses plain credentials for access control.'
    },
    {
      id: "7d6999ff-cdd0-4175-9a34-fa2e53f8e253",
      createdTime: 1747383888392,
      type: CredentialsType.SSL,
      enabled: true,
      description: 'Secure auth using public key infrastructure.'
    },
    {
      id: "7d6999ff-cdd0-4175-9a34-fa2e53f8e254",
      createdTime: 1747383888393,
      type: CredentialsType.JWT,
      enabled: false,
      description: 'Authenticate using signed JWT tokens.'
    },
    {
      id: "7d6999ff-cdd0-4175-9a34-fa2e53f8e252",
      createdTime: 1747383888394,
      type: CredentialsType.SCRAM,
      enabled: true,
      description: 'Auth with hashed and salted passwords.'
    },
  ],
  "totalPages": 1,
  "totalElements": 4,
  "hasNext": false
}
