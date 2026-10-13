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

import { DatePipe } from '@angular/common';
import { Router } from '@angular/router';
import { TranslateService } from '@ngx-translate/core';
import { Observable } from 'rxjs';
import { map } from 'rxjs/operators';
import {
  cellLink,
  CellLinkTarget,
  cellStatus,
  DateEntityTableColumn,
  EntityTableColumn,
  EntityTableConfig
} from '@home/models/entity/entities-table-config.models';
import { EntityTypeResource } from '@shared/models/entity-type.models';
import { BaseData } from '@shared/models/base-data';
import { PageData } from '@shared/models/page/page-data';
import { PageLink } from '@shared/models/page/page-link';
import { TopicTreeService } from '@core/http/topic-tree.service';
import { droppedMsgReasonTranslationMap } from '@shared/models/dropped-message.model';
import { ClientType, clientTypeTranslationMap } from '@shared/models/client.model';
import { escapeHtml, TopicTreeEntityKind } from '@shared/models/topic-tree.models';
import { ConnectionState, connectionStateColor, connectionStateTranslationMap } from '@shared/models/session.model';

export type TopicTreeTableTab = 'subscriptions' | 'clients' | 'shared' | 'integrations' | 'retained' | 'dropped';

export interface TopicTreeTableServices {
  topicTree: TopicTreeService;
  translate: TranslateService;
  datePipe: DatePipe;
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
      case 'dropped':
        return [
          new DateEntityTableColumn<TopicTreeTableRow>('lastEventTime', 'topic-tree.entity.last-event', this.services.datePipe,
            '20%', undefined, undefined, false),
          text('topic', 'topic-tree.entity.topic', '25%'),
          text('clientId', 'topic-tree.entity.client-id', '20%', undefined,
            row => row.clientId ? session(row.clientId) : null, () => 'topic-tree.entity.open-session'),
          text('reason', 'topic-tree.entity.reason', '25%', row => t(droppedMsgReasonTranslationMap.get(row.reason)) || escapeHtml(row.reason)),
          number('droppedCount', 'topic-tree.entity.count', '10%')
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
    const { page, pageSize } = pageLink;
    const { topicTree } = this.services;
    switch (this.tab) {
      case 'subscriptions':
        return topicTree.getSubscriptions(this.path, page, pageSize).pipe(
          map(p => this.rows(p, s => ({ ...s, id: `${s.clientId}|${s.topicFilter}` }))));
      case 'clients':
      case 'shared':
      case 'integrations':
        return topicTree.getEntities(this.path, this.tab.toUpperCase() as TopicTreeEntityKind, page, pageSize).pipe(
          map(p => this.rows(p, e => ({ ...e }))));
      case 'retained':
        return topicTree.getRetained(this.path, page, pageSize).pipe(map(p => this.rows(p, m => ({ ...m, id: m.topic }))));
      case 'dropped':
        return topicTree.getDropped(this.path, page, pageSize).pipe(map(p => this.rows(p, m => ({ ...m }))));
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
