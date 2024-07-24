///
/// Copyright © 2016-2024 The Thingsboard Authors
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
import {
  isArraysEqualIgnoreUndefined,
  isDefinedAndNotNull,
  isEmpty,
  isEqualIgnoreUndefined,
  isNotEmptyStr,
  isUndefinedOrNull
} from '@core/utils';
import { TimePageLink } from '@shared/models/page/page-link';

export enum CredentialsType {
  MQTT_BASIC = 'MQTT_BASIC',
  SSL = 'SSL'
}

export const ANY_CHARACTERS = '.*';
export const wsSystemCredentialsName = 'TBMQ WebSockets MQTT Credentials';

export const credentialsTypeTranslationMap = new Map<CredentialsType, string>(
  [
    [CredentialsType.MQTT_BASIC, 'mqtt-client-credentials.type-basic'],
    [CredentialsType.SSL, 'mqtt-client-credentials.type-ssl']
  ]
);

export const credentialsWarningTranslations = new Map<CredentialsType, string>(
  [
    [CredentialsType.MQTT_BASIC, 'mqtt-client-credentials.type-basic-auth'],
    [CredentialsType.SSL, 'mqtt-client-credentials.type-ssl-auth']
  ]
);

export interface ClientCredentials extends BaseData {
  credentialsId?: string;
  name: string;
  clientType: ClientType;
  credentialsType: CredentialsType;
  credentialsValue: string;
  password?: string;
}

export interface SslMqttCredentials extends SslAuthRulesMapping {
  certCnPattern: string;
  certCnIsRegex: boolean;
}

export interface SslAuthRulesMapping {
  authRulesMapping: AuthRulesMapping[];
}

export interface SslCredentialsAuthRules {
  [key: string]: AuthRules;
}

export interface BasicCredentials {
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

export interface ClientCredentialsFilterConfig {
  credentialsTypeList?: CredentialsType[];
  clientTypeList?: ClientType[];
  name?: string;
}

export const clientCredentialsFilterConfigEquals = (filter1?: ClientCredentialsFilterConfig, filter2?: ClientCredentialsFilterConfig): boolean => {
  if (filter1 === filter2) {
    return true;
  }
  if ((isUndefinedOrNull(filter1) || isEmpty(filter1)) && (isUndefinedOrNull(filter2) || isEmpty(filter2))) {
    return true;
  } else if (isDefinedAndNotNull(filter1) && isDefinedAndNotNull(filter2)) {
    if (!isArraysEqualIgnoreUndefined(filter1.credentialsTypeList, filter2.credentialsTypeList)) {
      return false;
    }
    if (!isArraysEqualIgnoreUndefined(filter1.clientTypeList, filter2.clientTypeList)) {
      return false;
    }
    if (!isEqualIgnoreUndefined(filter1.name, filter2.name)) {
      return false;
    }
    return true;
  }
  return false;
};

export class ClientCredentialsQuery {
  pageLink: TimePageLink;

  credentialsTypeList: CredentialsType[];
  clientTypeList: ClientType[];
  name: string;

  constructor(pageLink: TimePageLink, clientCredentialsFilter: ClientCredentialsFilterConfig) {
    this.pageLink = pageLink;
    this.credentialsTypeList = clientCredentialsFilter?.credentialsTypeList;
    this.clientTypeList = clientCredentialsFilter?.clientTypeList;
    if (isNotEmptyStr(clientCredentialsFilter?.name)) {
      this.pageLink.textSearch = clientCredentialsFilter?.name;
    }
  }

  public toQuery(): string {
    let query = this.pageLink.toQuery();
    if (this.credentialsTypeList && this.credentialsTypeList.length) {
      query += `&credentialsTypeList=${this.credentialsTypeList.join(',')}`;
    }
    if (this.clientTypeList && this.clientTypeList.length) {
      query += `&clientTypeList=${this.clientTypeList.join(',')}`;
    }
    return query;
  }
}
