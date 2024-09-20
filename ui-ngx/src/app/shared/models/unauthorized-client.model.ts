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
import { TimePageLink } from '@shared/models/page/page-link';
import {
  isArraysEqualIgnoreUndefined,
  isDefinedAndNotNull,
  isEmpty, isEqualIgnoreUndefined,
  isNotEmptyStr,
  isUndefinedOrNull
} from '@core/utils';

export interface UnauthorizedClient extends BaseData {
  clientId: string;
  ipAddress: string;
  username: string;
  reason: string;
  passwordProvided: boolean;
  tlsUsed: boolean;
  ts: number;
}

export interface UnauthorizedClientFilterConfig {
  clientId?: string;
  ipAddress?: string;
  username?: string;
  reason?: string;
  passwordProvidedList?: boolean[];
  tlsUsedList?: boolean[];
}

export class UnauthorizedClientQuery {
  pageLink: TimePageLink;
  clientId: string;
  ipAddress: string;
  username: string;
  reason: string;
  passwordProvidedList: boolean[];
  tlsUsedList: boolean[];

  constructor(pageLink: TimePageLink, filter: UnauthorizedClientFilterConfig) {
    this.pageLink = pageLink;
    this.ipAddress = filter?.ipAddress;
    this.username = filter?.username;
    this.reason = filter?.reason;
    this.passwordProvidedList = filter?.passwordProvidedList;
    this.tlsUsedList = filter?.tlsUsedList;
    if (isNotEmptyStr(filter?.clientId)) {
      this.pageLink.textSearch = filter.clientId;
    }
  }

  public toQuery(): string {
    let query = this.pageLink.toQuery();
    if (this.ipAddress && this.ipAddress.length) {
      query += `&ipAddress=${this.ipAddress}`;
    }
    if (this.username && this.username.length) {
      query += `&username=${this.username}`;
    }
    if (this.reason && this.reason.length) {
      query += `&reason=${this.reason}`;
    }
    if (this.passwordProvidedList && this.passwordProvidedList.length) {
      query += `&passwordProvidedList=${this.passwordProvidedList.join(',')}`;
    }
    if (this.tlsUsedList && this.tlsUsedList.length) {
      query += `&tlsUsedList=${this.tlsUsedList.join(',')}`;
    }
    return query;
  }
}

export const unauthorizedClientFilterConfigEquals = (filter1?: UnauthorizedClientFilterConfig, filter2?: UnauthorizedClientFilterConfig): boolean => {
  if (filter1 === filter2) {
    return true;
  }
  if ((isUndefinedOrNull(filter1) || isEmpty(filter1)) && (isUndefinedOrNull(filter2) || isEmpty(filter2))) {
    return true;
  } else if (isDefinedAndNotNull(filter1) && isDefinedAndNotNull(filter2)) {
    if (!isArraysEqualIgnoreUndefined(filter1.passwordProvidedList, filter2.passwordProvidedList)) {
      return false;
    }
    if (!isArraysEqualIgnoreUndefined(filter1.tlsUsedList, filter2.tlsUsedList)) {
      return false;
    }
    if (!isEqualIgnoreUndefined(filter1.clientId, filter2.clientId)) {
      return false;
    }
    if (!isEqualIgnoreUndefined(filter1.username, filter2.username)) {
      return false;
    }
    if (!isEqualIgnoreUndefined(filter1.ipAddress, filter2.ipAddress)) {
      return false;
    }
    if (!isEqualIgnoreUndefined(filter1.reason, filter2.reason)) {
      return false;
    }
    return true;
  }
  return false;
};

