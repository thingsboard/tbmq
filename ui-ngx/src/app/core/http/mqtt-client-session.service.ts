///
/// Copyright © 2016-2022 The Thingsboard Authors
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
import { DetailedClientSessionInfo } from '@shared/models/mqtt-session.model';

@Injectable({
  providedIn: 'root'
})
export class MqttClientSessionService {

  constructor(private http: HttpClient) {
  }

  public getDetailedClientSessionInfo(clientId: string, config?: RequestConfig): Observable<DetailedClientSessionInfo> {
    return this.http.get<DetailedClientSessionInfo>(`/api/client-session?clientId=${clientId}`, defaultHttpOptionsFromConfig(config));
  }

  public getShortClientSessionInfos(pageLink: PageLink, config?: RequestConfig): Observable<PageData<DetailedClientSessionInfo>> {
    return this.http.get<PageData<DetailedClientSessionInfo>>(`/api/client-session${pageLink.toQuery()}`, defaultHttpOptionsFromConfig(config));
  }

  public updateShortClientSessionInfo(session: DetailedClientSessionInfo, config?: RequestConfig): Observable<DetailedClientSessionInfo> {
    return this.http.post<DetailedClientSessionInfo>(`/api/subscription`, session, defaultHttpOptionsFromConfig(config));
  }

  public removeClientSession(clientId: string, sessionId: string, config?: RequestConfig) {
    return this.http.delete(`/api/client-session/remove?clientId=${clientId}&sessionId=${sessionId}`, defaultHttpOptionsFromConfig(config));
  }

  public disconnectClientSession(clientId: string, sessionId: string, config?: RequestConfig) {
    return this.http.delete(`/api/client-session/disconnect?clientId=${clientId}&sessionId=${sessionId}`, defaultHttpOptionsFromConfig(config));
  }

}
