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

export type TopicTreeColumnId = 'subscriptions' | 'clients' | 'retained' | 'online' | 'shared' | 'integrations' | 'dropped';

export interface TopicTreeNode {
  path: string | null;
  segment: string;
  wildcard: boolean;
  hasChildren: boolean;
  childrenCount: number;
  subscriptionsExact: number;
  subscriptionsTotal: number;
  retainedExact: number;
  retainedTotal: number;
  clientsTotal?: number;
  clientsOnline?: number;
  clientsOffline?: number;
  sharedGroupsExact?: number;
  sharedGroupsTotal?: number;
  integrationsExact?: number;
  integrationsTotal?: number;
  dropped24h?: number;
}

export interface TopicTreeEntity {
  id: string;
  subscriptions: number;
  connected?: boolean;
  clientType?: string;
}

export type TopicTreeEntityKind = 'CLIENTS' | 'INTEGRATIONS' | 'SHARED';

export interface TopicResolveMatch {
  topicFilter: string;
  subscriptions: number;
  clients: number;
  qos: Record<string, number>;
  integrations: number;
  sharedGroups: number;
}

export interface TopicResolve {
  topic: string;
  subscribersTotal: number;
  clientsTotal: number;
  matches: TopicResolveMatch[];
}

export interface TopicTreeColumnDef {
  id: TopicTreeColumnId;
  titleKey: string;
  default: boolean;
  backend?: string;
}

export const topicTreeColumns: TopicTreeColumnDef[] = [
  { id: 'subscriptions', titleKey: 'topic-tree.column.subscriptions', default: true },
  { id: 'clients', titleKey: 'topic-tree.column.clients', default: true, backend: 'clients' },
  { id: 'retained', titleKey: 'topic-tree.column.retained', default: true },
  { id: 'online', titleKey: 'topic-tree.column.online', default: false, backend: 'online' },
  { id: 'shared', titleKey: 'topic-tree.column.shared', default: false, backend: 'shared' },
  { id: 'integrations', titleKey: 'topic-tree.column.integrations', default: false, backend: 'integrations' },
  { id: 'dropped', titleKey: 'topic-tree.column.dropped', default: false, backend: 'dropped' },
];

export const TOPIC_TREE_COLUMNS_STORAGE_KEY = 'tbmq_topic_tree_columns';

export interface TopicTreeRow {
  node: TopicTreeNode;
  depth: number;
  expanded: boolean;
  loading: boolean;
  kind: 'node' | 'more';
  parentPath: string | null;
  nextPage?: number;
  remaining?: number;
}

export const toTreePath = (filter: string): string =>
  !filter ? '' : filter.startsWith('$share/') ? filter.split('/').slice(2).join('/') : filter;
