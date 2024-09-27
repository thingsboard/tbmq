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

import { Injectable } from '@angular/core';
import { defaultHttpOptionsFromConfig, RequestConfig } from './http-utils';
import { Observable } from 'rxjs';
import { HttpClient } from '@angular/common/http';
import { PageData } from '@shared/models/page/page-data';
import {
  ClientSubscriptions,
  ClientSubscriptionsQuery, ClientSubscription
} from '@shared/models/subscription.model';
import { SharedSubscriptionQuery } from '@shared/models/shared-subscription.model';
import { TopicSubscription } from '@shared/models/ws-client.model';

@Injectable({
  providedIn: 'root'
})
export class SubscriptionService {

  constructor(private http: HttpClient) {
  }

  public updateSubscriptions(subscriptions: ClientSubscriptions, config?: RequestConfig): Observable<ClientSubscriptions> {
    return this.http.post<ClientSubscriptions>(`/api/subscription`, subscriptions, defaultHttpOptionsFromConfig(config));
  }

  public getClientSubscriptions(clientId: string, config?: RequestConfig): Observable<TopicSubscription[]> {
    return this.http.get<TopicSubscription[]>(`/api/subscription/${clientId}`, defaultHttpOptionsFromConfig(config));
  }

  public getSharedSubscriptions(query: SharedSubscriptionQuery, config?: RequestConfig): Observable<PageData<TopicSubscription>> {
    return this.http.get<PageData<TopicSubscription>>(`/api/subscription${query.toQuery()}`, defaultHttpOptionsFromConfig(config));
  }

  public getAllClientSubscriptions(query: ClientSubscriptionsQuery, config?: RequestConfig): Observable<PageData<ClientSubscription>> {
    return this.http.get<PageData<ClientSubscription>>(`/api/subscription/all${query.toQuery()}`, defaultHttpOptionsFromConfig(config));
  }

  public clearEmptySubscriptionNodes(config?: RequestConfig): Observable<void> {
    return this.http.delete<void>(`/api/subscription/topic-trie/clear`, defaultHttpOptionsFromConfig(config));
  }

}
