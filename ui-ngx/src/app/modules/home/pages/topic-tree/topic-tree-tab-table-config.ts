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

import { Router } from '@angular/router';
import { TranslateService } from '@ngx-translate/core';
import { escape as escapeHtml } from 'lodash';
import { Observable } from 'rxjs';
import { map } from 'rxjs/operators';
import {
  cellLink,
  CellLinkTarget,
  cellStatus,
  EntityTableColumn,
  EntityTableConfig
} from '@home/models/entity/entities-table-config.models';
import { EntityTypeResource } from '@shared/models/entity-type.models';
import { BaseData } from '@shared/models/base-data';
import { PageData } from '@shared/models/page/page-data';
import { PageLink } from '@shared/models/page/page-link';
import { TopicTreeService } from '@core/http/topic-tree.service';
import { ClientType, clientTypeTranslationMap } from '@shared/models/client.model';
import { TopicTreeEntityKind } from '@shared/models/topic-tree.models';
import { ConnectionState, connectionStateColor, connectionStateTranslationMap } from '@shared/models/session.model';

export const TOPIC_TREE_TABLE_TABS = ['subscriptions', 'clients', 'shared', 'integrations', 'retained'] as const;
export type TopicTreeTableTab = typeof TOPIC_TREE_TABLE_TABS[number];

export interface TopicTreeTableServices {
  topicTree: TopicTreeService;
  translate: TranslateService;
  router: Router;
}

type TopicTreeTableRow = BaseData & Record<string, any>;

const OPEN_IN_KEYS: Partial<Record<TopicTreeTableTab, string>> = {
  shared: 'topic-tree.open-in-shared',
  integrations: 'topic-tree.open-in-integrations'
};

export class TopicTreeTabTableConfig extends EntityTableConfig<TopicTreeTableRow> {

  constructor(private tab: TopicTreeTableTab,
              private path: string | null,
              private services: TopicTreeTableServices) {
    super();
    this.tableTitle = '';
    this.pageMode = false;
    this.detailsPanelEnabled = false;
    this.selectionEnabled = false;
    this.searchEnabled = false;
    this.addEnabled = false;
    this.entitiesDeleteEnabled = false;
    this.defaultSortOrder = null;
    this.entityTranslations = { noEntities: 'topic-tree.no-data', search: 'action.search' };
    this.entityResources = {} as EntityTypeResource<TopicTreeTableRow>;
    this.columns = this.buildColumns();
    this.entitiesFetchFunction = pageLink => this.fetch(pageLink);
    if (OPEN_IN_KEYS[tab]) {
      this.headerActionDescriptors.push({
        name: this.services.translate.instant(OPEN_IN_KEYS[tab]),
        icon: 'open_in_new',
        isEnabled: () => true,
        onAction: () => this.openIn()
      });
    }
  }

  private buildColumns(): EntityTableColumn<TopicTreeTableRow>[] {
    const t = (key: string) => key ? this.services.translate.instant(key) : '';
    const text = (key: string, title: string, width: string, content?: (row: TopicTreeTableRow) => string,
                  link?: (row: TopicTreeTableRow) => CellLinkTarget | null, linkName?: (row: TopicTreeTableRow) => string) =>
      new EntityTableColumn<TopicTreeTableRow>(key, title, width, content ?? (row => escapeHtml(row[key])), () => ({}), false,
        undefined, undefined, false, link ? { ...cellLink('', link), nameFunction: row => t(linkName(row)) } : undefined);
    const session = (clientId: string): CellLinkTarget => ({ commands: ['/sessions'], queryParams: { clientId } });
    const integration = (id: string): CellLinkTarget => ({ commands: ['/integrations', id] });
    const number = (key: string, title: string, width: string) =>
      new EntityTableColumn<TopicTreeTableRow>(key, title, width, row => escapeHtml(row[key]), () => ({}), false,
        () => ({}), () => undefined, true);
    switch (this.tab) {
      case 'subscriptions':
        return [
          text('client', 'topic-tree.entity.client-id', '30%', row => escapeHtml(row.integration ? (row.integrationName ?? row.clientId) : row.clientId),
            row => row.integration ? (row.integrationName ? integration(row.clientId) : null) : session(row.clientId),
            row => row.integration ? 'topic-tree.entity.open-integration' : 'topic-tree.entity.open-session'),
          text('type', 'topic-tree.entity.type', '15%', row => t(row.integration ? 'topic-tree.entity.type-integration'
            : row.shareName ? 'topic-tree.entity.type-shared' : 'topic-tree.entity.type-client')),
          text('topicFilter', 'topic-tree.entity.topic-filter', '40%'),
          number('qos', 'topic-tree.entity.qos', '15%')
        ];
      case 'clients':
        return [
          text('id', 'topic-tree.entity.client-id', '40%', undefined, row => session(row.id), () => 'topic-tree.entity.open-session'),
          text('status', 'topic-tree.entity.status', '20%', row => this.connectionStatus(row.connected)),
          text('clientType', 'topic-tree.entity.client-type', '20%', row => t(clientTypeTranslationMap.get(row.clientType as ClientType))),
          number('subscriptions', 'topic-tree.entity.subscriptions', '20%')
        ];
      case 'shared':
        return [
          text('id', 'topic-tree.entity.group', '70%', undefined,
            row => ({ commands: ['/shared-subscriptions/manage'], queryParams: { shareName: row.id } }), () => 'topic-tree.entity.open-group'),
          number('subscriptions', 'topic-tree.entity.subscriptions', '30%')
        ];
      case 'integrations':
        return [
          text('name', 'topic-tree.entity.name', '40%', row => escapeHtml(row.name) || t('topic-tree.entity.integration-not-found'),
            row => row.name ? integration(row.id) : null, () => 'topic-tree.entity.open-integration'),
          text('id', 'topic-tree.entity.id', '40%'),
          number('subscriptions', 'topic-tree.entity.subscriptions', '20%')
        ];
      case 'retained':
        return [
          text('topic', 'topic-tree.entity.topic', '40%'),
          text('payload', 'topic-tree.entity.payload', '45%'),
          number('qos', 'topic-tree.entity.qos', '15%')
        ];
    }
  }

  private connectionStatus(connected: boolean | null): string {
    if (connected == null) {
      return '';
    }
    const state = connected ? ConnectionState.CONNECTED : ConnectionState.DISCONNECTED;
    const color = connectionStateColor.get(state);
    return cellStatus(this.services.translate.instant(connectionStateTranslationMap.get(state)), color.content, color.background);
  }

  private fetch(pageLink: PageLink): Observable<PageData<TopicTreeTableRow>> {
    const { topicTree } = this.services;
    switch (this.tab) {
      case 'subscriptions':
        return topicTree.getSubscriptions(this.path, pageLink).pipe(
          map(p => this.rows(p, s => ({ ...s, id: `${s.clientId}|${s.topicFilter}` }))));
      case 'clients':
      case 'shared':
      case 'integrations':
        return topicTree.getEntities(this.path, this.tab.toUpperCase() as TopicTreeEntityKind, pageLink).pipe(
          map(p => this.rows(p, e => ({ ...e }))));
      case 'retained':
        return topicTree.getRetained(this.path, pageLink).pipe(map(p => this.rows(p, m => ({ ...m, id: m.topic }))));
    }
  }

  private rows<T>(page: PageData<T>, toRow: (item: T) => TopicTreeTableRow): PageData<TopicTreeTableRow> {
    return { ...page, data: page.data.map(toRow) };
  }

  private openIn(): void {
    const { router } = this.services;
    switch (this.tab) {
      case 'shared':
        router.navigate(['/shared-subscriptions/manage']);
        break;
      case 'integrations':
        router.navigate(['/integrations']);
        break;
    }
  }
}
