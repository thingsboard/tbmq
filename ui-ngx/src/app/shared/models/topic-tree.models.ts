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

import { BaseData } from '@shared/models/base-data';
import { NumericOperation } from '@shared/models/query/query.models';

export type TopicTreeColumnId = 'subscriptions' | 'clients' | 'retained' | 'online' | 'shared' | 'integrations';

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
  sharedSubscriptionsTotal?: number;
  integrationsExact?: number;
  integrationsTotal?: number;
}

export interface TopicTreeEntity {
  id: string;
  name?: string;
  subscriptions: number;
  connected?: boolean;
  clientType?: string;
}

export interface TopicTreeSubscription {
  clientId: string;
  topicFilter: string;
  qos: number;
  integration: boolean;
  shareName?: string;
  integrationName?: string;
}

export type TopicTreeEntityKind = 'CLIENTS' | 'INTEGRATIONS' | 'SHARED';

export type TopicTreeHas = 'SUBSCRIPTIONS' | 'RETAINED' | 'SHARED' | 'INTEGRATIONS';

export const topicTreeHasValues: TopicTreeHas[] = ['SUBSCRIPTIONS', 'RETAINED', 'SHARED', 'INTEGRATIONS'];

export const topicTreeHasTranslations = new Map<TopicTreeHas, string>([
  ['SUBSCRIPTIONS', 'topic-tree.column.subscriptions'],
  ['RETAINED', 'topic-tree.column.retained'],
  ['SHARED', 'topic-tree.column.shared'],
  ['INTEGRATIONS', 'topic-tree.column.integrations']
]);

export interface TopicTreeFilter {
  topic?: string;
  topicPrefix?: boolean;
  matchesTopic?: string;
  has?: TopicTreeHas[];
  wildcardOnly?: boolean;
  subscriptions?: number;
  subscriptionOperation?: NumericOperation;
}

export const isTopicTreeFilterEmpty = (filter: TopicTreeFilter | null | undefined): boolean =>
  !filter || (!filter.topic && !filter.matchesTopic && !filter.has?.length && !filter.wildcardOnly
    && (filter.subscriptions ?? null) === null);

export interface TopicTreeColumnDef {
  id: TopicTreeColumnId;
  titleKey: string;
  default: boolean;
  // Computed by the server only when asked for; the other counts come with every node.
  backend: boolean;
}

export const topicTreeColumns: TopicTreeColumnDef[] = [
  { id: 'subscriptions', titleKey: 'topic-tree.column.subscriptions', default: true, backend: false },
  { id: 'clients', titleKey: 'topic-tree.column.clients', default: true, backend: true },
  { id: 'retained', titleKey: 'topic-tree.column.retained', default: true, backend: false },
  { id: 'online', titleKey: 'topic-tree.column.online', default: false, backend: true },
  { id: 'shared', titleKey: 'topic-tree.column.shared', default: false, backend: true },
  { id: 'integrations', titleKey: 'topic-tree.column.integrations', default: false, backend: true },
];

export const defaultTopicTreeColumns = (): TopicTreeColumnId[] => topicTreeColumns.filter(c => c.default).map(c => c.id);

// The known ids among the given ones, in the canonical column order.
export const knownTopicTreeColumns = (ids: unknown): TopicTreeColumnId[] =>
  Array.isArray(ids) ? topicTreeColumns.map(c => c.id).filter(id => ids.includes(id)) : [];

export const topicTreeBackendColumns = (ids: TopicTreeColumnId[] = topicTreeColumns.map(c => c.id)): string[] =>
  topicTreeColumns.filter(c => c.backend && ids.includes(c.id)).map(c => c.id);

export const TOPIC_TREE_COLUMNS_STORAGE_KEY = 'tbmq_topic_tree_columns';

export interface TopicTreeRow extends BaseData {
  id: string;
  kind: 'node' | 'more';
  node?: TopicTreeNode;
  depth: number;
  expanded: boolean;
  flat: boolean;
  parentPath: string | null;
  remaining?: number;
}
