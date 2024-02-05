///
/// Copyright © 2016-2023 The Thingsboard Authors
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
import { PageLink } from '@shared/models/page/page-link';
import { PageData } from '@shared/models/page/page-data';
import { SharedSubscription, SharedSubscriptionGroup, SharedSubscriptionQuery } from '@shared/models/shared-subscription.model';

@Injectable({
  providedIn: 'root'
})
export class SharedSubscriptionService {

  constructor(private http: HttpClient) {
  }

  public getSharedSubscriptionById(sharedSubscriptionId: string, config?: RequestConfig): Observable<SharedSubscription> {
    return this.http.get<SharedSubscription>(`/api/app/shared/subs/${sharedSubscriptionId}`, defaultHttpOptionsFromConfig(config));
  }

  public getSharedSubscriptions(pageLink: PageLink, config?: RequestConfig): Observable<PageData<SharedSubscription>> {
    return this.http.get<PageData<SharedSubscription>>(`/api/app/shared/subs${pageLink.toQuery()}`, defaultHttpOptionsFromConfig(config));
  }

  public saveSharedSubscription(sharedSubscription: SharedSubscription, config?: RequestConfig): Observable<SharedSubscription> {
    return this.http.post<SharedSubscription>(`/api/app/shared/subs`, sharedSubscription, defaultHttpOptionsFromConfig(config));
  }

  public deleteSharedSubscription(sharedSubscriptionId: string, config?: RequestConfig): Observable<void> {
    return this.http.delete<void>(`/api/app/shared/subs/${sharedSubscriptionId}`, defaultHttpOptionsFromConfig(config));
  }

  public getSharedSubscriptionsV2(query: SharedSubscriptionQuery, config?: RequestConfig): Observable<PageData<SharedSubscriptionGroup>> {
    return this.http.get<PageData<SharedSubscriptionGroup>>(`/api/subscription${query.toQuery()}`, defaultHttpOptionsFromConfig(config));
  }
}
