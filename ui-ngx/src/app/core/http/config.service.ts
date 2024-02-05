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
import { BrokerConfigTable, SystemVersionInfo } from '@shared/models/config.model';
import { map } from 'rxjs/operators';
import { PageData } from '@shared/models/page/page-data';

@Injectable({
  providedIn: 'root'
})
export class ConfigService {

  constructor(private http: HttpClient) {
  }

  public getBrokerConfig(config?: RequestConfig): Observable<BrokerConfigTable> {
    return this.http.get<BrokerConfigTable>(`/api/app/config`, defaultHttpOptionsFromConfig(config));
  }

  public getBrokerConfigPageData(config?: RequestConfig): Observable<PageData<BrokerConfigTable>> {
    return this.getBrokerConfig(config).pipe(
      map(brokerConfig => {
        const data = [];
        for (const [key, value] of Object.entries(brokerConfig)) {
          data.push({
            key,
            value
          });
        }
        return {
          data: data,
          totalPages: 1,
          totalElements: data.length,
          hasNext: false
        };
      })
    );
  }

  public getBrokerServiceIds(config?: RequestConfig): Observable<string[]> {
    return this.http.get<Array<string>>(`/api/app/brokers`, defaultHttpOptionsFromConfig(config));
  }

  public getSystemVersion(config?: RequestConfig): Observable<SystemVersionInfo> {
    return this.http.get<SystemVersionInfo>(`/api/system/info`, defaultHttpOptionsFromConfig(config));
  }

  public getGithubSystemVersion(config?: RequestConfig): Observable<any> {
    const url = 'https://api.github.com/repos/thingsboard/tbmq/releases/latest';
    return this.http.get<any>(url, defaultHttpOptionsFromConfig(config));
  }

}
