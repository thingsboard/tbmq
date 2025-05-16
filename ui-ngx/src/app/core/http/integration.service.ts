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
import { HttpClient } from '@angular/common/http';
import { PageLink } from '@shared/models/page/page-link';
import { defaultHttpOptionsFromConfig, RequestConfig } from '@core/http/http-utils';
import { Observable, Subject } from 'rxjs';
import { PageData } from '@shared/models/page/page-data';
import { Integration, IntegrationInfo } from '@shared/models/integration.models';
import { saveTopicsToLocalStorage } from '@core/utils';

@Injectable({
  providedIn: 'root'
})
export class IntegrationService {

  readonly restarted = new Subject<boolean>();

  constructor(
    private http: HttpClient
  ) { }

  public getIntegrationsInfo(pageLink: PageLink, config?: RequestConfig): Observable<PageData<IntegrationInfo>> {
    return this.http.get<PageData<IntegrationInfo>>(`/api/integrations${pageLink.toQuery()}`, defaultHttpOptionsFromConfig(config));
  }

  public getIntegration(integrationId: string, config?: RequestConfig): Observable<Integration> {
    return this.http.get<Integration>(`/api/integration/${integrationId}`, defaultHttpOptionsFromConfig(config));
  }

  public saveIntegration(integration: Integration, config?: RequestConfig): Observable<Integration> {
    saveTopicsToLocalStorage(integration.configuration.topicFilters);
    return this.http.post<Integration>('/api/integration', integration, defaultHttpOptionsFromConfig(config));
  }

  public deleteIntegration(integrationId: string, config?: RequestConfig) {
    return this.http.delete(`/api/integration/${integrationId}`, defaultHttpOptionsFromConfig(config));
  }

  public checkIntegrationConnection(value: Integration, config?: RequestConfig): Observable<string>{
    return this.http.post<string>('/api/integration/check', value, defaultHttpOptionsFromConfig(config));
  }

  public getIntegrationSubscriptions(integrationId: string, config?: RequestConfig): Observable<string[]> {
    return this.http.get<string[]>(`/api/subscription/integration?integrationId=${integrationId}`, defaultHttpOptionsFromConfig(config));
  }

  public restartIntegration(integrationId: string, config?: RequestConfig): Observable<Integration> {
    return this.http.post<Integration>(`/api/integration/${integrationId}`, defaultHttpOptionsFromConfig(config));
  }

  public onRestartIntegration() {
    this.restarted.next(true);
  }
}
