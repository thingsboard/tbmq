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
import { Observable, of, switchMap } from 'rxjs';
import { HttpClient } from '@angular/common/http';
import { PageLink } from '@shared/models/page/page-link';
import { PageData } from '@shared/models/page/page-data';
import {
  MqttAuthProvider,
  MqttBasicAuthenticationStrategy,
  ShortMqttAuthProvider,
  MqttBasicAuthenticationStrategyTranslationMap
} from '@shared/models/mqtt-auth-provider.model';
import { ClientCredentials, CredentialsType } from '@shared/models/credentials.model';
import { take } from 'rxjs/operators';
import { TranslateService } from '@ngx-translate/core';
import { DialogService } from '@core/services/dialog.service';

@Injectable({
  providedIn: 'root'
})
export class MqttAuthProviderService {

  constructor(
    private http: HttpClient,
    private translate: TranslateService,
    private dialog: DialogService,
  ) {
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

  public getBasicAuthProviderStrategy(config?: RequestConfig): Observable<MqttBasicAuthenticationStrategy> {
    return this.http.get<MqttBasicAuthenticationStrategy>('/api/mqtt/auth/provider/basic/strategy', defaultHttpOptionsFromConfig(config));
  }

  public displayBasicAuthProviderAlert(credentials: ClientCredentials): Observable<boolean> {
    if (credentials.credentialsType !== CredentialsType.MQTT_BASIC) {
      return of(true);
    }
    return this.getBasicAuthProviderStrategy().pipe(
      take(1),
      switchMap(strategy => {
        const credentialsValue = JSON.parse(credentials.credentialsValue);
        const hasUsername = credentialsValue.userName;
        const hasClientId = credentialsValue.clientId;
        const displayAlert = (strategy === MqttBasicAuthenticationStrategy.CLIENT_ID && hasUsername) ||
                                (strategy === MqttBasicAuthenticationStrategy.USERNAME && hasClientId);
        if (!displayAlert) {
          return of(true);
        }
        const name = credentials.name;
        const activeStrategy = this.translate.instant(MqttBasicAuthenticationStrategyTranslationMap.get(strategy));
        const inactiveStrategy = this.translate.instant(MqttBasicAuthenticationStrategyTranslationMap.get(strategy === MqttBasicAuthenticationStrategy.CLIENT_ID ? MqttBasicAuthenticationStrategy.USERNAME : MqttBasicAuthenticationStrategy.CLIENT_ID));
        return this.dialog.alert(
          this.translate.instant('authentication.basic-authentication-alert-title'),
          this.translate.instant('authentication.basic-authentication-alert-text', { name, activeStrategy, inactiveStrategy })
        );
      })
    );
  }
}
