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

import { Injectable } from '@angular/core';
import { defaultHttpOptionsFromConfig, RequestConfig } from './http-utils';
import { Observable } from 'rxjs';
import { HttpClient } from '@angular/common/http';
import { PageLink } from '@shared/models/page/page-link';
import { PageData } from '@shared/models/page/page-data';
import { BlockedClient, BlockedClientsQuery } from '@shared/models/blocked-client.models';
import { isDefinedAndNotNull } from '@core/utils';

@Injectable({
  providedIn: 'root'
})
export class BlockedClientService {

  constructor(private http: HttpClient) {}

  public saveBlockedClient(entity: BlockedClient, config?: RequestConfig): Observable<BlockedClient> {
    return this.http.post<BlockedClient>('/api/blockedClient', entity, defaultHttpOptionsFromConfig(config));
  }

  public deleteBlockedClient(entity: BlockedClient, config?: RequestConfig) {
    let url = `/api/blockedClient?type=${entity.type}&value=${encodeURIComponent(entity.value)}`;
    if (isDefinedAndNotNull(entity.regexMatchTarget)) {
      url += `&regexMatchTarget=${entity.regexMatchTarget}`;
    }
    return this.http.delete(url, defaultHttpOptionsFromConfig(config));
  }

  public getBlockedClientsTimeToLive(config?: RequestConfig): Observable<number> {
    return this.http.get<number>('/api/blockedClient/ttl', defaultHttpOptionsFromConfig(config));
  }

  public getBlockedClients(pageLink: PageLink, config?: RequestConfig): Observable<PageData<BlockedClient>> {
    return this.http.get<PageData<BlockedClient>>(`/api/blockedClient${pageLink.toQuery()}`,
      defaultHttpOptionsFromConfig(config));
  }

  public getBlockedClientsV2(query: BlockedClientsQuery, config?: RequestConfig): Observable<PageData<BlockedClient>> {
    return this.http.get<PageData<BlockedClient>>(`/api/blockedClient/v2${query.toQuery()}`,
      defaultHttpOptionsFromConfig(config));
  }

}
