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
import { mergeMap, Observable, of } from 'rxjs';
import { HttpClient } from '@angular/common/http';
import { PageLink, TimePageLink } from '@shared/models/page/page-link';
import { PageData } from '@shared/models/page/page-data';
import { ClientCredentialsInfo, ClientCredentials, ClientCredentialsQuery } from '@shared/models/credentials.model';

@Injectable({
  providedIn: 'root'
})
export class ClientCredentialsService {

  constructor(
    private http: HttpClient
  ) {
  }

  public saveClientCredentials(mqttClientCredentials: ClientCredentials, config?: RequestConfig): Observable<ClientCredentials> {
    return this.http.post<ClientCredentials>('/api/mqtt/client/credentials', mqttClientCredentials, defaultHttpOptionsFromConfig(config));
  }

  public deleteClientCredentials(credentialsId: string, config?: RequestConfig) {
    return this.http.delete(`/api/mqtt/client/credentials/${credentialsId}`, defaultHttpOptionsFromConfig(config));
  }

  public getClientsCredentials(pageLink: PageLink, config?: RequestConfig): Observable<PageData<ClientCredentials>> {
    return this.http.get<PageData<ClientCredentials>>(`/api/mqtt/client/credentials${pageLink.toQuery()}`,
      defaultHttpOptionsFromConfig(config));
  }

  public getClientCredentialsV2(query: ClientCredentialsQuery, config?: RequestConfig): Observable<PageData<ClientCredentials>> {
    return this.http.get<PageData<ClientCredentials>>(`/api/v2/mqtt/client/credentials${query.toQuery()}`,
      defaultHttpOptionsFromConfig(config));
  }

  public getClientCredentials(credentialsId: string, config?: RequestConfig): Observable<ClientCredentials> {
    return this.http.get<ClientCredentials>(`/api/mqtt/client/credentials/${credentialsId}`, defaultHttpOptionsFromConfig(config));
  }

  public changePassword(currentPassword: string, newPassword: string, credentialsId: string) {
    return this.http.post(`/api/mqtt/client/credentials/${credentialsId}`, {currentPassword, newPassword});
  }

  public getClientCredentialsStatsInfo(config?: RequestConfig): Observable<ClientCredentialsInfo> {
    return this.http.get<ClientCredentialsInfo>(`/api/mqtt/client/credentials/info`, defaultHttpOptionsFromConfig(config));
  }

  public findClientCredentialsByName(name: string): Observable<ClientCredentials> {
    const pageLink = new TimePageLink(1, 0, name);
    const query = new ClientCredentialsQuery(pageLink, {name});
    return this.getClientCredentialsV2(query, {ignoreErrors: true}).pipe(
      mergeMap(credentials => {
        if (credentials.data.length) {
          return of(credentials.data[0]);
        } else {
          return of(null);
        }
      })
    );
  }
}
