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
import { MqttClientCredentials } from '@shared/models/mqtt-client-crenetials.model';
import { map } from 'rxjs/operators';
import { isNotEmptyStr } from '@core/utils';
import { Direction } from '@shared/models/page/sort-order';

@Injectable({
  providedIn: 'root'
})
export class MqttClientCredentialsService {

  constructor(
    private http: HttpClient
  ) { }

  public saveMqttClientCredentials(mqttClientCredentials: MqttClientCredentials, config?: RequestConfig): Observable<MqttClientCredentials> {
    const credentialsValue = typeof mqttClientCredentials.credentialsValue === 'string'
      ? mqttClientCredentials.credentialsValue
      : JSON.stringify(mqttClientCredentials.credentialsValue);
    return this.http.post<MqttClientCredentials>('/api/mqtt/client/credentials', {...mqttClientCredentials, ...{credentialsValue}}, defaultHttpOptionsFromConfig(config));
  }

  public deleteMqttClientCredentials(credentialsId: string, config?: RequestConfig) {
    return this.http.delete(`/api/mqtt/client/credentials/${credentialsId}`, defaultHttpOptionsFromConfig(config));
  }

  public getMqttClientsCredentials(pageLink: PageLink, config?: RequestConfig): Observable<PageData<MqttClientCredentials>> {
    return this.http.get<PageData<MqttClientCredentials>>(`/api/mqtt/client/credentials${pageLink.toQuery()}`,
      defaultHttpOptionsFromConfig(config));
  }

  public getMqttClientCredentials(credentialsId: string, config?: RequestConfig): Observable<MqttClientCredentials> {
    return this.http.get<MqttClientCredentials>(`/api/mqtt/client/credentials/${credentialsId}`, defaultHttpOptionsFromConfig(config));
  }

}
