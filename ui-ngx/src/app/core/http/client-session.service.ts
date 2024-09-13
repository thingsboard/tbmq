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
import { PageLink } from '@shared/models/page/page-link';
import { PageData } from '@shared/models/page/page-data';
import {
  ClientSessionCredentials,
  ClientSessionStatsInfo,
  DetailedClientSessionInfo,
  SessionMetrics,
  SessionMetricsList,
  SessionQuery
} from '@shared/models/session.model';
import { StatsService } from '@core/http/stats.service';
import { map } from 'rxjs/operators';

@Injectable({
  providedIn: 'root'
})
export class ClientSessionService {

  constructor(private http: HttpClient,
              private statsService: StatsService) {
  }

  public getDetailedClientSessionInfo(clientId: string, config?: RequestConfig): Observable<DetailedClientSessionInfo> {
    return this.http.get<DetailedClientSessionInfo>(`/api/client-session?clientId=${clientId}`, defaultHttpOptionsFromConfig(config));
  }

  public getShortClientSessionInfos(pageLink: PageLink, config?: RequestConfig): Observable<PageData<DetailedClientSessionInfo>> {
    return this.http.get<PageData<DetailedClientSessionInfo>>(`/api/client-session${pageLink.toQuery()}`, defaultHttpOptionsFromConfig(config));
  }

  public getShortClientSessionInfosV2(query: SessionQuery, config?: RequestConfig): Observable<PageData<DetailedClientSessionInfo>> {
    return this.http.get<PageData<DetailedClientSessionInfo>>(`/api/v2/client-session${query.toQuery()}`, defaultHttpOptionsFromConfig(config));
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

  public getClientSessionsStats(config?: RequestConfig): Observable<ClientSessionStatsInfo> {
    return this.http.get<ClientSessionStatsInfo>(`/api/client-session/info`, defaultHttpOptionsFromConfig(config));
  }

  public getClientSessionDetails(clientId: string, config?: RequestConfig): Observable<ClientSessionCredentials> {
    return this.http.get<ClientSessionCredentials>(`/api/client-session/details?clientId=${clientId}`, defaultHttpOptionsFromConfig(config));
  }

  public getSessionMetrics(clientId: string, config?: RequestConfig): Observable<PageData<SessionMetrics>> {
    return this.statsService.getLatestTimeseries(clientId, SessionMetricsList, true, config).pipe(
      map((metrics) => {
        const data = [];
        for (const [key, value] of Object.entries(metrics)) {
          data.push({
            key,
            ts: value[0].ts,
            value: value[0].value || 0
          });
        }
        return {
          data,
          totalPages: 1,
          totalElements: data.length,
          hasNext: false
        };
      })
    );
  }

  public deleteSessionMetrics(clientId: string, config?: RequestConfig) {
    return this.http.delete(`/api/client-session/?clientId=${clientId}}`, defaultHttpOptionsFromConfig(config));
  }
}
