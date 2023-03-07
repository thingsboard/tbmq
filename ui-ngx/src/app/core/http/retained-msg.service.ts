///
/// Copyright © 2016-2023 The Thingsboard Authors
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
import { RetainedMessage } from "@shared/models/retained-message.model";

@Injectable({
  providedIn: 'root'
})
export class RetainedMsgService {

  constructor(private http: HttpClient) {
  }

  public getRetainedMessages(pageLink: PageLink, config?: RequestConfig): Observable<PageData<RetainedMessage>> {
    return this.http.get<PageData<RetainedMessage>>(`/api/retained-msg${pageLink.toQuery()}`, defaultHttpOptionsFromConfig(config));
  }

  public getRetainedMessage(topicName: string, config?: RequestConfig): Observable<RetainedMessage> {
    return this.http.get<RetainedMessage>(`/api/retained-msg?topicName=${topicName}`, defaultHttpOptionsFromConfig(config));
  }

  public deleteRetainedMessage(topicName: string, config?: RequestConfig): Observable<void> {
    return this.http.delete<void>(`/api/retained-msg?topicName=${topicName}`, defaultHttpOptionsFromConfig(config));
  }

  public clearEmptyRetainedMsgNodes(config?: RequestConfig): Observable<void> {
    return this.http.delete<void>(`/api/retained-msg/topic-trie/clear`, defaultHttpOptionsFromConfig(config));
  }

}
