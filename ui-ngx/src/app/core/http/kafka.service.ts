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
import { Observable, of } from 'rxjs';
import { HttpClient } from '@angular/common/http';
import { PageLink } from '@shared/models/page/page-link';
import { PageData } from '@shared/models/page/page-data';
import { KafkaBroker, KafkaConsumerGroup, KafkaTopic } from '@shared/models/kafka.model';

@Injectable({
  providedIn: 'root'
})
export class KafkaService {

  constructor(
    private http: HttpClient
  ) {
  }

  public getKafkaBrokers(pageLink: PageLink, config?: RequestConfig): Observable<PageData<KafkaBroker>> {
    return this.http.get<PageData<KafkaBroker>>(`/api/app/cluster-info/v2${pageLink.toQuery()}`, defaultHttpOptionsFromConfig(config));
  }

  public getKafkaTopics(pageLink: PageLink, config?: RequestConfig): Observable<PageData<KafkaTopic>> {
    return this.http.get<PageData<KafkaTopic>>(`/api/app/kafka-topics/v2${pageLink.toQuery()}`, defaultHttpOptionsFromConfig(config));
  }

  public getKafkaConsumerGroups(pageLink: PageLink, config?: RequestConfig): Observable<PageData<KafkaConsumerGroup>> {
    return this.http.get<PageData<KafkaConsumerGroup>>(`/api/app/consumer-groups/v2${pageLink.toQuery()}`, defaultHttpOptionsFromConfig(config));
  }

  public deleteConsumerGroup(groupId: string, config?: RequestConfig) {
    return this.http.delete(`/api/app/consumer-group?groupId=${groupId}`, defaultHttpOptionsFromConfig(config));
  }
}
