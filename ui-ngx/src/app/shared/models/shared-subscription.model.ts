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
import { ShortClientSessionInfo } from '@shared/models/session.model';
import {
  isDefinedAndNotNull,
  isEmpty,
  isEqualIgnoreUndefined, isNotEmptyStr,
  isUndefinedOrNull
} from '@core/utils';
import { TimePageLink } from '@shared/models/page/page-link';

export interface SharedSubscription extends BaseData {
  name: string;
  partitions: number;
  topicFilter: string;
}

export interface SharedSubscriptionGroup extends BaseData {
  shareName: string;
  topicFilter: string;
  clients: ShortClientSessionInfo[];
}

export interface SharedSubscriptionFilterConfig {
  shareNameSearch?: string;
  topicFilter?: string;
  clientIdSearch?: string;
}

export const sharedSubscriptionFilterConfigEquals = (filter1?: SharedSubscriptionFilterConfig, filter2?: SharedSubscriptionFilterConfig): boolean => {
  if (filter1 === filter2) {
    return true;
  }
  if ((isUndefinedOrNull(filter1) || isEmpty(filter1)) && (isUndefinedOrNull(filter2) || isEmpty(filter2))) {
    return true;
  } else if (isDefinedAndNotNull(filter1) && isDefinedAndNotNull(filter2)) {
    if (!isEqualIgnoreUndefined(filter1.shareNameSearch, filter2.shareNameSearch)) {
      return false;
    }
    if (!isEqualIgnoreUndefined(filter1.topicFilter, filter2.topicFilter)) {
      return false;
    }
    if (!isEqualIgnoreUndefined(filter1.clientIdSearch, filter2.clientIdSearch)) {
      return false;
    }
    return true;
  }
  return false;
};

export class SharedSubscriptionQuery {
  pageLink: TimePageLink;

  shareNameSearch: string;
  clientIdSearch: string;
  topicFilter: string;

  constructor(pageLink: TimePageLink, filter: SharedSubscriptionFilterConfig) {
    this.pageLink = pageLink;
    this.shareNameSearch = filter?.shareNameSearch;
    this.clientIdSearch = filter?.clientIdSearch;
    if (isNotEmptyStr(filter?.topicFilter)) {
      this.pageLink.textSearch = filter.topicFilter;
    }
  }

  public toQuery(): string {
    let query = this.pageLink.toQuery();
    if (this.shareNameSearch?.length) {
      query += `&shareNameSearch=${this.shareNameSearch}`;
    }
    if (this.clientIdSearch?.length) {
      query += `&clientIdSearch=${this.clientIdSearch}`;
    }
    return query;
  }
}
