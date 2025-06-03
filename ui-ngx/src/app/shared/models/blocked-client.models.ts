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

import {
  isArraysEqualIgnoreUndefined,
  isDefinedAndNotNull,
  isEmpty,
  isEqualIgnoreUndefined, isNotEmptyStr,
  isUndefinedOrNull
} from '@core/utils';
import { TimePageLink } from '@shared/models/page/page-link';
import { BaseData } from '@shared/models/base-data';

export interface BlockedClient extends BaseData {
  type: BlockedClientType;
  expirationTime: number;
  status: BlockedClientStatus;
  description: string;
  value: string;
  regexMatchTarget?: RegexMatchTarget;
}

export enum BlockedClientType {
  CLIENT_ID = 'CLIENT_ID',
  USERNAME = 'USERNAME',
  IP_ADDRESS = 'IP_ADDRESS',
  REGEX = 'REGEX',
}

export enum RegexMatchTarget {
  BY_CLIENT_ID = 'BY_CLIENT_ID',
  BY_USERNAME = 'BY_USERNAME',
  BY_IP_ADDRESS = 'BY_IP_ADDRESS',
}

export enum BlockedClientStatus {
  ACTIVE = 'ACTIVE',
  EXPIRED = 'EXPIRED',
  DELETING_SOON = 'DELETING_SOON',
}

export const blockedClientTypeTranslationMap = new Map<BlockedClientType, string>(
  [
    [BlockedClientType.CLIENT_ID, 'blocked-client.type-client-id'],
    [BlockedClientType.USERNAME, 'blocked-client.type-username'],
    [BlockedClientType.IP_ADDRESS, 'blocked-client.type-ip-address'],
    [BlockedClientType.REGEX, 'blocked-client.type-regex'],
  ]
);

export const regexMatchTargetTranslationMap = new Map<RegexMatchTarget, string>(
  [
    [RegexMatchTarget.BY_CLIENT_ID, 'blocked-client.type-client-id'],
    [RegexMatchTarget.BY_USERNAME, 'blocked-client.type-username'],
    [RegexMatchTarget.BY_IP_ADDRESS, 'blocked-client.type-ip-address'],
  ]
);

export const blockedClientStatusTranslationMap = new Map<BlockedClientStatus, string>(
  [
    [BlockedClientStatus.ACTIVE, 'blocked-client.status-active'],
    [BlockedClientStatus.EXPIRED, 'blocked-client.status-expired'],
    [BlockedClientStatus.DELETING_SOON, 'blocked-client.status-deleting-soon'],
  ]
);

export const blockedClientTypeValuePropertyMap = new Map<BlockedClientType, string>(
  [
    [BlockedClientType.CLIENT_ID, 'clientId'],
    [BlockedClientType.USERNAME, 'username'],
    [BlockedClientType.IP_ADDRESS, 'ipAddress'],
    [BlockedClientType.REGEX, 'pattern'],
  ]
);

export interface BlockedClientFilterConfig {
  value?: string;
  typeList?: BlockedClientType[];
  regexMatchTargetList?: RegexMatchTarget[];
}

export const blockedClientsFilterConfigEquals = (filter1?: BlockedClientFilterConfig, filter2?: BlockedClientFilterConfig): boolean => {
  if (filter1 === filter2) {
    return true;
  }
  if ((isUndefinedOrNull(filter1) || isEmpty(filter1)) && (isUndefinedOrNull(filter2) || isEmpty(filter2))) {
    return true;
  } else if (isDefinedAndNotNull(filter1) && isDefinedAndNotNull(filter2)) {
    if (!isArraysEqualIgnoreUndefined(filter1.typeList, filter2.typeList)) {
      return false;
    }
    if (!isArraysEqualIgnoreUndefined(filter1.regexMatchTargetList, filter2.regexMatchTargetList)) {
      return false;
    }
    if (!isEqualIgnoreUndefined(filter1.value, filter2.value)) {
      return false;
    }
    return true;
  }
  return false;
};

export class BlockedClientsQuery {
  pageLink: TimePageLink;
  value: string;
  typeList: BlockedClientType[];
  regexMatchTargetList: RegexMatchTarget[];

  constructor(pageLink: TimePageLink, filter: BlockedClientFilterConfig) {
    this.pageLink = pageLink;
    this.typeList = filter?.typeList;
    this.regexMatchTargetList = filter?.regexMatchTargetList;
    if (isNotEmptyStr(filter?.value)) {
      this.pageLink.textSearch = filter?.value;
    }
  }

  public toQuery(): string {
    let query = this.pageLink.toQuery();
    if (this.typeList?.length) {
      query += `&typeList=${this.typeList.join(',')}`;
    }
    if (this.regexMatchTargetList?.length) {
      query += `&regexMatchTargetList=${this.regexMatchTargetList.join(',')}`;
    }
    if (this.value?.length) {
      query += `&value=${this.value}`;
    }
    return query;
  }
}

