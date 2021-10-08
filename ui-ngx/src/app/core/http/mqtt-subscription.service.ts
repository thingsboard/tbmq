///
/// Copyright © 2016-2020 The Thingsboard Authors
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
import { TopicSubscription } from '@shared/models/mqtt-session.model';
import { DetailedClientSessionInfo } from '@shared/models/mqtt-client.model';

@Injectable({
  providedIn: 'root'
})
export class MqttSubscriptionService {

  constructor(
    private http: HttpClient
  ) { }

  public getClientSubscriptions(clientId: string, config?: RequestConfig): Observable<Array<TopicSubscription>> {
    return this.http.get<Array<TopicSubscription>>(`/api/subscription/${clientId}`, defaultHttpOptionsFromConfig(config));
  }

  public updateClientSubscriptions(session: DetailedClientSessionInfo, config?: RequestConfig): Observable<DetailedClientSessionInfo> {
    return this.http.post<DetailedClientSessionInfo>(`/api/subscription`, session, defaultHttpOptionsFromConfig(config));
  }

  public clearEmptySubscriptionNodes(config?: RequestConfig) {
    return this.http.delete(`/api/subscription/topic-trie/clear`, defaultHttpOptionsFromConfig(config));
  }

}
