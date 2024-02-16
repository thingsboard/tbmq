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
import { HttpClient } from '@angular/common/http';
import { Observable } from 'rxjs';
import { defaultHttpOptionsFromConfig, RequestConfig } from '@core/http/http-utils';
import { PageData } from '@shared/models/page/page-data';
import { WebSocketConnection, WebSocketConnectionDto, WebSocketConnectionsLimit } from '@shared/models/ws-client.model';
import { PageLink } from '@shared/models/page/page-link';
import { Direction } from '@shared/models/page/sort-order';

@Injectable({
  providedIn: 'root'
})
export class WebSocketConnectionService {

  constructor(private http: HttpClient) {
  }

  public saveWebSocketConnection(connection: WebSocketConnection, config?: RequestConfig): Observable<WebSocketConnection> {
    return this.http.post<WebSocketConnection>('/api/ws/connection', connection, defaultHttpOptionsFromConfig(config));
  }

  public getWebSocketConnections(config?: RequestConfig): Observable<PageData<WebSocketConnectionDto>> {
    const pageLink = new PageLink(WebSocketConnectionsLimit, 0, null, { property: 'createdTime', direction: Direction.DESC });
    return this.http.get<PageData<WebSocketConnectionDto>>(`/api/ws/connection${pageLink.toQuery()}`, defaultHttpOptionsFromConfig(config));
  }

  public getWebSocketConnectionById(connectionId: string, config?: RequestConfig): Observable<WebSocketConnection> {
    return this.http.get<WebSocketConnection>(`/api/ws/connection/${connectionId}`, defaultHttpOptionsFromConfig(config));
  }

  public deleteWebSocketConnection(connectionId: string, config?: RequestConfig) {
    return this.http.delete(`/api/ws/connection/${connectionId}`, defaultHttpOptionsFromConfig(config));
  }
}
