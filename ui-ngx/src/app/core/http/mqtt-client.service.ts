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
import { PageLink } from '@shared/models/page/page-link';
import { PageData } from '@shared/models/page/page-data';
import { Client } from '@shared/models/mqtt-client.model';

@Injectable({
  providedIn: 'root'
})
export class MqttClientService {

  constructor(
    private http: HttpClient
  ) { }

  public getMqttClient(clientId: string, config?: RequestConfig): Observable<Client> {
    return this.http.get<Client>(`/api/mqtt/client/${clientId}`, defaultHttpOptionsFromConfig(config));
  }

  public getMqttClients(pageLink: PageLink, config?: RequestConfig): Observable<PageData<Client>> {
    return this.http.get<PageData<Client>>(`/api/mqtt/client${pageLink.toQuery()}`, defaultHttpOptionsFromConfig(config));
  }

  public saveMqttClient(mqttClient: Client, config?: RequestConfig): Observable<Client> {
    return this.http.post<Client>('/api/mqtt/client', mqttClient, defaultHttpOptionsFromConfig(config));
  }

  public deleteMqttClient(clientId: string, config?: RequestConfig) {
    return this.http.delete(`/api/mqtt/client/${clientId}`, defaultHttpOptionsFromConfig(config));
  }

}