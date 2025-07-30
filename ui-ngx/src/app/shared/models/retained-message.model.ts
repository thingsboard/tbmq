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
import { TimePageLink } from '@shared/models/page/page-link';
import {
  isArraysEqualIgnoreUndefined,
  isDefinedAndNotNull,
  isEmpty,
  isEqualIgnoreUndefined,
  isNotEmptyStr,
  isUndefinedOrNull
} from '@core/utils';

export interface RetainedMessage extends BaseData {
  topic: string;
  payload: string;
  qos: number;
  userProperties: UserProperties;
}

export interface UserProperties {
  [key: string]: any;
}

export interface RetainedMessagesFilterConfig {
  topicName?: string;
  payload?: string;
  qosList?: boolean[];
}

export class RetainedMessagesQuery {
  pageLink: TimePageLink;
  topicName: string;
  payload: string;
  qosList: boolean[];

  constructor(pageLink: TimePageLink, filter: RetainedMessagesFilterConfig) {
    this.pageLink = pageLink;
    this.payload = filter?.payload;
    this.qosList = filter?.qosList;
    if (isNotEmptyStr(filter?.topicName)) {
      this.pageLink.textSearch = filter.topicName;
    }
  }

  public toQuery(): string {
    let query = this.pageLink.toQuery();
    if (this.payload?.length) {
      query += `&payload=${encodeURIComponent(this.payload)}`;
    }
    if (this.qosList?.length) {
      query += `&qosList=${this.qosList.join(',')}`;
    }
    return query;
  }
}

export const retainedMessagesFilterConfigEquals = (filter1?: RetainedMessagesFilterConfig, filter2?: RetainedMessagesFilterConfig): boolean => {
  if (filter1 === filter2) {
    return true;
  }
  if ((isUndefinedOrNull(filter1) || isEmpty(filter1)) && (isUndefinedOrNull(filter2) || isEmpty(filter2))) {
    return true;
  } else if (isDefinedAndNotNull(filter1) && isDefinedAndNotNull(filter2)) {
    if (!isEqualIgnoreUndefined(filter1.topicName, filter2.topicName)) {
      return false;
    }
    if (!isEqualIgnoreUndefined(filter1.payload, filter2.payload)) {
      return false;
    }
    if (!isArraysEqualIgnoreUndefined(filter1.qosList, filter2.qosList)) {
      return false;
    }
    return true;
  }
  return false;
};

