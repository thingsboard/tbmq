///
/// ThingsBoard, Inc. ("COMPANY") CONFIDENTIAL
///
/// Copyright © 2016-2026 ThingsBoard, Inc. All Rights Reserved.
///
/// NOTICE: All information contained herein is, and remains
/// the property of ThingsBoard, Inc. and its suppliers,
/// if any.  The intellectual and technical concepts contained
/// herein are proprietary to ThingsBoard, Inc.
/// and its suppliers and may be covered by U.S. and Foreign Patents,
/// patents in process, and are protected by trade secret or copyright law.
///
/// Dissemination of this information or reproduction of this material is strictly forbidden
/// unless prior written permission is obtained from COMPANY.
///
/// Access to the source code contained herein is hereby forbidden to anyone except current COMPANY employees,
/// managers or contractors who have executed Confidentiality and Non-disclosure agreements
/// explicitly covering such access.
///
/// The copyright notice above does not evidence any actual or intended publication
/// or disclosure  of  this source code, which includes
/// information that is confidential and/or proprietary, and is a trade secret, of  COMPANY.
/// ANY REPRODUCTION, MODIFICATION, DISTRIBUTION, PUBLIC  PERFORMANCE,
/// OR PUBLIC DISPLAY OF OR THROUGH USE  OF THIS  SOURCE CODE  WITHOUT
/// THE EXPRESS WRITTEN CONSENT OF COMPANY IS STRICTLY PROHIBITED,
/// AND IN VIOLATION OF APPLICABLE LAWS AND INTERNATIONAL TREATIES.
/// THE RECEIPT OR POSSESSION OF THIS SOURCE CODE AND/OR RELATED INFORMATION
/// DOES NOT CONVEY OR IMPLY ANY RIGHTS TO REPRODUCE, DISCLOSE OR DISTRIBUTE ITS CONTENTS,
/// OR TO MANUFACTURE, USE, OR SELL ANYTHING THAT IT  MAY DESCRIBE, IN WHOLE OR IN PART.
///

import { Injectable } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { Observable } from 'rxjs';
import { createDefaultHttpOptions, QueryParams, RequestConfig } from './http-utils';
import { PageData } from '@shared/models/page/page-data';
import {
  TopicResolve,
  TopicTreeEntity,
  TopicTreeEntityKind,
  TopicTreeNode,
  TopicTreeSubscription
} from '@shared/models/topic-tree.models';

@Injectable({
  providedIn: 'root'
})
export class TopicTreeService {

  constructor(private http: HttpClient) {
  }

  public getChildren(path: string | null, columns: string[], page: number, pageSize: number,
                     config?: RequestConfig): Observable<PageData<TopicTreeNode>> {
    const queryParams: QueryParams = { page: `${page}`, pageSize: `${pageSize}`, columns: columns.join(',') };
    return this.http.get<PageData<TopicTreeNode>>(this.url('/api/topic-tree/children', path), this.options(queryParams, config));
  }

  public search(filter: string, columns: string[], page: number, pageSize: number,
                config?: RequestConfig): Observable<PageData<TopicTreeNode>> {
    const queryParams: QueryParams = { filter, page: `${page}`, pageSize: `${pageSize}`, columns: columns.join(',') };
    return this.http.get<PageData<TopicTreeNode>>('/api/topic-tree/search', this.options(queryParams, config));
  }

  public getNode(path: string | null, columns: string[], config?: RequestConfig): Observable<TopicTreeNode> {
    const queryParams: QueryParams = { columns: columns.join(',') };
    return this.http.get<TopicTreeNode>(this.url('/api/topic-tree/node', path), this.options(queryParams, config));
  }

  public getEntities(path: string | null, kind: TopicTreeEntityKind, page: number, pageSize: number,
                     config?: RequestConfig): Observable<PageData<TopicTreeEntity>> {
    const queryParams: QueryParams = { kind, page: `${page}`, pageSize: `${pageSize}` };
    return this.http.get<PageData<TopicTreeEntity>>(this.url('/api/topic-tree/entities', path), this.options(queryParams, config));
  }

  public getSubscriptions(path: string | null, page: number, pageSize: number,
                          config?: RequestConfig): Observable<PageData<TopicTreeSubscription>> {
    const queryParams: QueryParams = { page: `${page}`, pageSize: `${pageSize}` };
    return this.http.get<PageData<TopicTreeSubscription>>(this.url('/api/topic-tree/subscriptions', path), this.options(queryParams, config));
  }

  public resolve(topic: string, config?: RequestConfig): Observable<TopicResolve> {
    return this.http.get<TopicResolve>('/api/topic-tree/resolve', this.options({ topic }, config));
  }

  // cleanQueryParams drops empty strings, but path '' addresses the node with an empty segment (root is null)
  private url(base: string, path: string | null): string {
    return path === null ? base : `${base}?path=${encodeURIComponent(path)}`;
  }

  private options(queryParams: QueryParams, config?: RequestConfig) {
    return createDefaultHttpOptions({ ...config, queryParams });
  }
}
