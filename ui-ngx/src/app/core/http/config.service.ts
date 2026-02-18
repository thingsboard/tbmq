///
/// Copyright © 2016-2026 The Thingsboard Authors
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
import { BrokerConfig, BrokerConfigTableParam, SystemVersionInfo } from '@shared/models/config.model';
import { PageData } from '@shared/models/page/page-data';
import { ResourcesService } from '@core/services/resources.service';

@Injectable({
  providedIn: 'root'
})
export class ConfigService {

  brokerConfig: BrokerConfig;

  constructor(
    private http: HttpClient,
    private resourcesService: ResourcesService,
  ) {
  }

  public getBrokerConfigPageData(): Observable<PageData<BrokerConfigTableParam>> {
    const data = [];
    for (const [key, value] of Object.entries(this.brokerConfig)) {
      data.push({
        key,
        value
      });
    }
    return of({
      data,
      totalPages: 1,
      totalElements: data.length,
      hasNext: false
    });
  }

  public getBrokerServiceIds(config?: RequestConfig): Observable<string[]> {
    return this.http.get<Array<string>>(`/api/app/brokers`, defaultHttpOptionsFromConfig(config));
  }

  public getSystemVersion(config?: RequestConfig): Observable<SystemVersionInfo> {
    return this.http.get<SystemVersionInfo>(`/api/system/info`, defaultHttpOptionsFromConfig(config));
  }

  public fetchBrokerConfig(config?: RequestConfig): Observable<BrokerConfig> {
    return this.getBrokerConfig(config).pipe(
      mergeMap(brokerConfig => {
        this.brokerConfig = brokerConfig;
        return of(brokerConfig);
      })
    );
  }

  public downloadCaCertificate(): Observable<object> {
    return this.resourcesService.downloadResource('/api/device-connectivity/mqtts/certificate/download');
  }

  public caCertificateConfigured(config?: RequestConfig): Observable<boolean> {
    return this.http.get<boolean>('/api/device-connectivity/mqtts/certificate/configured',
      defaultHttpOptionsFromConfig(config));
  }

  private getBrokerConfig(config?: RequestConfig): Observable<BrokerConfig> {
    return this.http.get<BrokerConfig>(`/api/app/config`, defaultHttpOptionsFromConfig(config));
  }
}
