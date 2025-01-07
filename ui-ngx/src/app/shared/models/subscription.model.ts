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

import { TopicSubscription } from '@shared/models/ws-client.model';
import { PageLink } from '@shared/models/page/page-link';
import {
  isArraysEqualIgnoreUndefined,
  isDefinedAndNotNull,
  isEmpty,
  isEqualIgnoreUndefined,
  isNotEmptyStr,
  isUndefinedOrNull
} from '@core/utils';
import { BaseData } from '@shared/models/base-data';

export interface ClientSubscription extends BaseData {
  clientId: string;
  subscription: TopicSubscription;
}

export interface ClientSubscriptions {
  clientId: string;
  subscriptions: TopicSubscription[];
}

export interface ClientSubscriptionFilterConfig {
  clientId?: string;
  topicFilter?: string;
  qosList?: string[];
  noLocalList?: string[];
  retainAsPublishList?: string[];
  retainHandlingList?: string[];
  subscriptionId?: number;
}

export class ClientSubscriptionsQuery {
  pageLink: PageLink;
  clientId: string;
  topicFilter: string;
  qosList: string[];
  noLocalList: string[];
  retainAsPublishList: string[];
  retainHandlingList: string[];
  subscriptionId: number;

  constructor(pageLink: PageLink, filter: ClientSubscriptionFilterConfig) {
    this.pageLink = pageLink;
    this.clientId = filter?.clientId;
    this.topicFilter = filter?.topicFilter;
    this.qosList = filter?.qosList;
    this.noLocalList = filter?.noLocalList;
    this.retainAsPublishList = filter?.retainAsPublishList;
    this.retainHandlingList = filter?.retainHandlingList;
    this.subscriptionId = filter?.subscriptionId;
    if (isNotEmptyStr(filter?.clientId)) {
      this.pageLink.textSearch = filter.clientId;
    }
  }

  public toQuery(): string {
    let query = this.pageLink.toQuery();
    if (this.clientId?.length) {
      query += `&clientId=${this.clientId}`;
    }
    if (this.topicFilter?.length) {
      query += `&topicFilter=${this.topicFilter}`;
    }
    if (this.subscriptionId) {
      query += `&subscriptionId=${this.subscriptionId}`;
    }
    if (this.qosList?.length) {
      query += `&qosList=${this.qosList.join(',')}`;
    }
    if (this.noLocalList?.length) {
      query += `&noLocalList=${this.noLocalList.join(',')}`;
    }
    if (this.retainAsPublishList?.length) {
      query += `&retainAsPublishList=${this.retainAsPublishList.join(',')}`;
    }
    if (this.retainHandlingList?.length) {
      query += `&retainHandlingList=${this.retainHandlingList.join(',')}`;
    }
    return query;
  }
}

export const subscriptionsFilterConfigEquals = (filter1?: ClientSubscriptionFilterConfig, filter2?: ClientSubscriptionFilterConfig): boolean => {
  if (filter1 === filter2) {
    return true;
  }
  if ((isUndefinedOrNull(filter1) || isEmpty(filter1)) && (isUndefinedOrNull(filter2) || isEmpty(filter2))) {
    return true;
  } else if (isDefinedAndNotNull(filter1) && isDefinedAndNotNull(filter2)) {
    if (!isEqualIgnoreUndefined(filter1.clientId, filter2.clientId)) {
      return false;
    }
    if (!isEqualIgnoreUndefined(filter1.topicFilter, filter2.topicFilter)) {
      return false;
    }
    if (!isEqualIgnoreUndefined(filter1.subscriptionId, filter2.subscriptionId)) {
      return false;
    }
    if (!isArraysEqualIgnoreUndefined(filter1.qosList, filter2.qosList)) {
      return false;
    }
    if (!isArraysEqualIgnoreUndefined(filter1.noLocalList, filter2.noLocalList)) {
      return false;
    }
    if (!isArraysEqualIgnoreUndefined(filter1.retainAsPublishList, filter2.retainAsPublishList)) {
      return false;
    }
    if (!isArraysEqualIgnoreUndefined(filter1.retainHandlingList, filter2.retainHandlingList)) {
      return false;
    }
    return true;
  }
  return false;
};

