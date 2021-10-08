import { BaseData } from '@shared/models/base-data';
import { ClientId, ClientType } from '@shared/models/mqtt-client.model';

export enum MqttCredentialsType {
  MQTT_BASIC = 'MQTT_BASIC',
  SSL = 'SSL'
}

export const MqttCredentialsTypes = [
  MqttCredentialsType.MQTT_BASIC,
  MqttCredentialsType.SSL
];

export const credentialsTypeNames = new Map<MqttCredentialsType, string>(
  [
    [MqttCredentialsType.MQTT_BASIC, 'MQTT Basic'],
    [MqttCredentialsType.SSL, 'SSL']
  ]
);

export interface MqttClientCredentials extends BaseData<ClientId> {
  credentialsId: string;
  name: string;
  clientType: ClientType;
  credentialsType: MqttCredentialsType;
  credentialsValue: string;
}

export interface SslMqttCredentials {
  parentCertCommonName: string;
  authorizationRulesMapping: string[];
}

export interface BasicMqttCredentials {
  clientId: string;
  userName: string;
  password: string;
  authorizationRulePattern: string;
}
