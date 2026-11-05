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
import { HttpClient } from '@angular/common/http';
import { Observable } from 'rxjs';
import { createDefaultHttpOptions, QueryParams, RequestConfig } from './http-utils';
import { PageData } from '@shared/models/page/page-data';
import { PageLink } from '@shared/models/page/page-link';
import { RetainedMessage } from '@shared/models/retained-message.model';
import {
  TopicTreeEntity,
  TopicTreeEntityKind,
  TopicTreeFilter,
  TopicTreeNode,
  TopicTreeSubscription
} from '@shared/models/topic-tree.models';

@Injectable({
  providedIn: 'root'
})
export class TopicTreeService {

  constructor(private http: HttpClient) {
  }

  public getChildren(path: string | null, columns: string[], pageLink: PageLink,
                     config?: RequestConfig): Observable<PageData<TopicTreeNode>> {
    const queryParams: QueryParams = { ...this.pageParams(pageLink), columns: columns.join(',') };
    return this.http.get<PageData<TopicTreeNode>>(this.url('/api/topic-tree/children', path), this.options(queryParams, config));
  }

  public search(textSearch: string | null, filter: TopicTreeFilter, columns: string[], pageLink: PageLink,
                config?: RequestConfig): Observable<PageData<TopicTreeNode>> {
    const queryParams: QueryParams = { ...this.pageParams(pageLink), columns: columns.join(',') };
    if (textSearch) {
      queryParams.textSearch = textSearch;
    }
    if (filter?.topic) {
      queryParams.topic = filter.topic;
      queryParams.topicMatch = filter.topicPrefix ? 'PREFIX' : 'CONTAINS';
    }
    if (filter?.matchesTopic) {
      queryParams.matchesTopic = filter.matchesTopic;
    }
    if (filter?.has?.length) {
      queryParams.has = filter.has.join(',');
    }
    if (filter?.wildcardOnly) {
      queryParams.wildcardOnly = 'true';
    }
    if ((filter?.subscriptions ?? null) !== null) {
      queryParams.subscriptions = `${filter.subscriptions}`;
      queryParams.subscriptionOperation = filter.subscriptionOperation ?? 'EQUAL';
    }
    return this.http.get<PageData<TopicTreeNode>>('/api/topic-tree/search', this.options(queryParams, config));
  }

  public getNode(path: string | null, columns: string[], config?: RequestConfig): Observable<TopicTreeNode> {
    const queryParams: QueryParams = { columns: columns.join(',') };
    return this.http.get<TopicTreeNode>(this.url('/api/topic-tree/node', path), this.options(queryParams, config));
  }

  public getEntities(path: string | null, kind: TopicTreeEntityKind, pageLink: PageLink,
                     config?: RequestConfig): Observable<PageData<TopicTreeEntity>> {
    const queryParams: QueryParams = { kind, ...this.pageParams(pageLink) };
    return this.http.get<PageData<TopicTreeEntity>>(this.url('/api/topic-tree/entities', path), this.options(queryParams, config));
  }

  public getSubscriptions(path: string | null, pageLink: PageLink,
                          config?: RequestConfig): Observable<PageData<TopicTreeSubscription>> {
    return this.http.get<PageData<TopicTreeSubscription>>(this.url('/api/topic-tree/subscriptions', path),
      this.options(this.pageParams(pageLink), config));
  }

  public getRetained(path: string | null, pageLink: PageLink,
                     config?: RequestConfig): Observable<PageData<RetainedMessage>> {
    return this.http.get<PageData<RetainedMessage>>(this.url('/api/topic-tree/retained', path),
      this.options(this.pageParams(pageLink), config));
  }

  // cleanQueryParams drops empty strings, but path '' addresses the node with an empty segment (root is null)
  private url(base: string, path: string | null): string {
    return path === null ? base : `${base}?path=${encodeURIComponent(path)}`;
  }

  private pageParams(pageLink: PageLink): QueryParams {
    const params: QueryParams = { page: `${pageLink.page}`, pageSize: `${pageLink.pageSize}` };
    if (pageLink.sortOrder?.property) {
      params.sortProperty = pageLink.sortOrder.property;
      params.sortOrder = pageLink.sortOrder.direction;
    }
    return params;
  }

  private options(queryParams: QueryParams, config?: RequestConfig) {
    return createDefaultHttpOptions({ ...config, queryParams });
  }
}
