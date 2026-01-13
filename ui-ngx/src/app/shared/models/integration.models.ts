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
import { QoS } from '@shared/models/session.model';
import { ContentType } from '@shared/models/constants';

export enum IntegrationType {
  HTTP = 'HTTP',
  MQTT = 'MQTT',
  KAFKA = 'KAFKA',
}

export interface IntegrationTypeInfo {
  name: string;
  description: string;
  icon: string;
  tags?: string[];
  remote?: boolean;
  checkConnection?: boolean;
  hideDownlink?: boolean;
}

export const integrationTypeInfoMap = new Map<IntegrationType, IntegrationTypeInfo>(
  [
    [
      IntegrationType.HTTP,
      {
        name: 'integration.type-http',
        description: 'integration.type-http-description',
        icon: 'assets/integration-icon/http.svg',
        checkConnection: true
      }
    ],
    [
      IntegrationType.MQTT,
      {
        name: 'integration.type-mqtt',
        description: 'integration.type-mqtt-description',
        icon: 'assets/integration-icon/mqtt.svg',
        checkConnection: true
      }
    ],
    [
      IntegrationType.KAFKA,
      {
        name: 'integration.type-kafka',
        description: 'integration.type-kafka-description',
        icon: 'assets/integration-icon/kafka.svg',
        checkConnection: true
      }
    ],
  ]
);

const integrationHelpLinkMap = new Map<IntegrationType, string>(
  [
    [IntegrationType.HTTP, 'integrationHttp'],
    [IntegrationType.MQTT, 'integrationMqtt'],
    [IntegrationType.KAFKA, 'integrationKafka'],
  ]
);

export type IntegrationConfiguration = HttpIntegration | MqttIntegration | KafkaIntegration;

export function getIntegrationHelpLink(integration: Integration): string {
  if (integration && integration.type) {
    if (integrationHelpLinkMap.has(integration.type)) {
      return integrationHelpLinkMap.get(integration.type);
    }
  }
  return 'integrations';
}

export interface IntegrationMetaData {
  metadata?: { [k: string]: string };
}

export interface IntegrationBasic extends BaseData, HasDebugSettings {
  type: IntegrationType;
  enabled: boolean;
}

export interface Integration extends IntegrationBasic {
  configuration: IntegrationConfiguration & IntegrationMetaData;
  additionalInfo?: any;
}

export interface IntegrationInfo extends IntegrationBasic {
  status?: IntegrationStatus;
  stats?: Array<number>;
}

export interface IntegrationStatus {
  success: boolean;
  serviceId?: string;
  error?: any;
}

export interface IntegrationTopicFilter {
  filter: string;
}

export enum IntegrationCredentialType {
  Anonymous = 'anonymous',
  Basic = 'basic',
  CertPEM = 'cert.PEM',
}

export interface Credentials {
  type: IntegrationCredentialType;
}

export interface CertPemCredentials extends Credentials{
  caCertFileName: string;
  caCert: string;
  certFileName: string;
  cert: string;
  privateKeyFileName: string;
  privateKey: string;
  privateKeyPassword?: string;
}

export interface BasicCredentials extends Credentials{
  username: string;
  password: string;
}

export const IntegrationCredentialTypeTranslation = new Map<IntegrationCredentialType, string>([
  [IntegrationCredentialType.Anonymous, 'integration.credentials-type-anonymous'],
  [IntegrationCredentialType.Basic, 'integration.credentials-type-basic'],
  [IntegrationCredentialType.CertPEM, 'integration.credentials-type-pem'],
]);

export interface Topics {
  topicFilters: Array<string>;
}

export interface HttpIntegration extends Topics {
  clientConfiguration: {
    restEndpointUrl: string;
    requestMethod: HttpRequestType;
    credentials: Credentials;
    readTimeoutMs: number;
    headers?: {[key: string]: string} | null;
    maxInMemoryBufferSizeInKb: number;
    maxParallelRequestsCount: number;
    payloadContentType: ContentType,
    sendBinaryOnParseFailure: boolean;
    sendOnlyMsgPayload: boolean
  }
}

export enum HttpRequestType {
  POST = 'POST',
  PUT = 'PUT',
  GET = 'GET',
  DELETE = 'DELETE',
  // HEAD = 'HEAD',
  // PATCH = 'PATCH',
  // OPTIONS = 'OPTIONS',
  // TRACE = 'TRACE',
  // CONNECT = 'CONNECT'
}

export interface KafkaIntegration extends Topics {
  clientConfiguration: {
    topic: string;
    key: string;
    clientId: string;
    bootstrapServers: string;
    clientIdPrefix: string;
    retries: number;
    batchSize: number;
    linger: number;
    bufferMemory: number;
    compression: string;
    acks: string;
    kafkaHeadersCharset: string;
    kafkaHeaders: {[key: string]: string} | null;
    otherProperties: {[key: string]: string} | null;
  };
}

export interface MqttIntegration extends Topics {
  clientConfiguration: {
    host: string;
    port: number;
    topicName: string;
    ssl: boolean;
    clientId: string;
    credentials: Credentials | BasicCredentials | CertPemCredentials;
    connectTimeoutSec: number;
    reconnectPeriodSec: number;
    keepAliveSec: number;
    mqttVersion: string;
    qos: QoS;
    retained: boolean;
    useMsgTopicName: boolean;
    useMsgQoS: boolean;
    useMsgRetain: boolean;
  };
}

export interface HasDebugSettings {
  debugSettings?: DebugSettings;
}

export interface DebugSettings {
  failuresEnabled?: boolean;
  allEnabled?: boolean;
  allEnabledUntil?: number;
}

export const ToByteStandartCharsetTypes = [
  'US-ASCII',
  'ISO-8859-1',
  'UTF-8',
  'UTF-16BE',
  'UTF-16LE',
  'UTF-16'
];

export const ToByteStandartCharsetTypeTranslations = new Map<string, string>(
  [
    ['US-ASCII', 'integration.charset-us-ascii'],
    ['ISO-8859-1', 'integration.charset-iso-8859-1'],
    ['UTF-8', 'integration.charset-utf-8'],
    ['UTF-16BE', 'integration.charset-utf-16be'],
    ['UTF-16LE', 'integration.charset-utf-16le'],
    ['UTF-16', 'integration.charset-utf-16'],
  ]
);
