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
import { MqttAuthProvider, ShortMqttAuthProvider } from '@shared/models/mqtt-auth-provider.model';

@Injectable({
  providedIn: 'root'
})
export class MqttAuthProviderService {

  constructor(private http: HttpClient) {
  }

  public saveAuthProvider(mqttClientCredentials: MqttAuthProvider, config?: RequestConfig): Observable<MqttAuthProvider> {
    return this.http.post<MqttAuthProvider>('/api/mqtt/auth/provider', mqttClientCredentials, defaultHttpOptionsFromConfig(config));
  }

  public getAuthProviders(pageLink: PageLink, config?: RequestConfig): Observable<PageData<ShortMqttAuthProvider>> {
    return this.http.get<PageData<ShortMqttAuthProvider>>(`/api/mqtt/auth/providers${pageLink.toQuery()}`, defaultHttpOptionsFromConfig(config));
  }

  public getAuthProviderById(id: string, config?: RequestConfig): Observable<MqttAuthProvider> {
    return this.http.get<MqttAuthProvider>(`/api/mqtt/auth/provider/${id}`, defaultHttpOptionsFromConfig(config));
  }

  public enableAuthProvider(id: string, config?: RequestConfig): Observable<void> {
    return this.http.post<void>(`/api/mqtt/auth/provider/${id}/enable`, defaultHttpOptionsFromConfig(config));
  }

  public disableAuthProvider(id: string, config?: RequestConfig): Observable<void> {
    return this.http.post<void>(`/api/mqtt/auth/provider/${id}/disable`, defaultHttpOptionsFromConfig(config));
  }

  public switchAuthProvider(id: string, enabled: boolean, config?: RequestConfig): Observable<void> {
    if (enabled) {
      return this.disableAuthProvider(id, config);
    } else {
      return this.enableAuthProvider(id, config);
    }
  }
}
