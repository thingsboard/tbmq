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

import { Type } from '@angular/core';
import { TranslateService } from '@ngx-translate/core';
import { escape as escapeHtml } from 'lodash';
import { forkJoin, Observable, of } from 'rxjs';
import { catchError, map, switchMap, tap } from 'rxjs/operators';
import { EntityTableColumn, EntityTableConfig } from '@home/models/entity/entities-table-config.models';
import { EntityTableHeaderComponent } from '@home/components/entity/entity-table-header.component';
import { EntityTypeResource } from '@shared/models/entity-type.models';
import { PageData } from '@shared/models/page/page-data';
import { PageLink } from '@shared/models/page/page-link';
import { Direction, SortOrder } from '@shared/models/page/sort-order';
import { TopicTreeService } from '@core/http/topic-tree.service';
import { ConnectionState, connectionStateColor } from '@shared/models/session.model';
import {
  isTopicTreeFilterEmpty,
  topicTreeBackendColumns,
  TopicTreeColumnId,
  TopicTreeFilter,
  TopicTreeNode,
  TopicTreeRow,
  topicTreeColumns
} from '@shared/models/topic-tree.models';

export interface TopicTreeTableHandlers {
  nodeClicked?: (row: TopicTreeRow) => void;
  nodeRevealed?: (row: TopicTreeRow) => void;
  revealFailed?: (path: string) => void;
  openColumns?: (event: MouseEvent) => void;
}

const CHILD_PAGE_SIZE = 100;
const MUTED = { color: 'rgba(0, 0, 0, .38)' };
const ROOT_KEY = '\u0000root';

const key = (path: string | null): string => path ?? ROOT_KEY;

/**
 * Drives tb-entities-table as a tree: the top level is paged by the paginator, expanded nodes insert their children
 * below them (paged inline by a "Show N more" row), and any search or filter switches to a flat list of matching paths.
 */
export class TopicTreeTableConfig extends EntityTableConfig<TopicTreeRow> {

  query: TopicTreeFilter = {};

  private readonly expanded = new Set<string>();
  private readonly childPages = new Map<string, number>();
  private readonly childCache = new Map<string, { pages: number; rows: TopicTreeRow[] }>();
  private reuseChildren = false;
  private revealPath: string | null = null;

  constructor(private service: TopicTreeService,
              private translate: TranslateService,
              private columnIds: TopicTreeColumnId[],
              private handlers: TopicTreeTableHandlers,
              headerComponent: Type<EntityTableHeaderComponent<TopicTreeRow>> | null) {
    super();
    this.tableTitle = this.translate.instant('topic-tree.topic-tree');
    this.headerComponent = headerComponent;
    this.pageMode = false;
    this.detailsPanelEnabled = false;
    this.selectionEnabled = false;
    this.addEnabled = false;
    this.entitiesDeleteEnabled = false;
    this.searchEnabled = true;
    this.displayPagination = true;
    this.rowPointer = true;
    this.defaultSortOrder = { property: 'subscriptions', direction: Direction.DESC };
    this.entityTranslations = { noEntities: 'topic-tree.no-topics', search: 'topic-tree.filter' };
    this.entityResources = {} as EntityTypeResource<TopicTreeRow>;
    this.columns = this.buildColumns();
    this.entitiesFetchFunction = pageLink => this.fetch(pageLink);
    this.handleRowClick = (event, row) => this.onRowClick(event, row);
    if (handlers.openColumns) {
      this.headerActionDescriptors.push({
        name: this.translate.instant('topic-tree.columns'),
        icon: 'view_column',
        isEnabled: () => true,
        onAction: event => handlers.openColumns(event)
      });
    }
  }

  reveal(path: string): void {
    const segments = path.split('/');
    for (let i = 1; i < segments.length; i++) {
      this.expanded.add(key(segments.slice(0, i).join('/')));
    }
    this.revealPath = path;
  }

  // Changes the visible columns in place: the expanded branches, the search text and the open details stay, only the
  // rows are refetched with the new column set.
  setColumns(columnIds: TopicTreeColumnId[]): void {
    this.columnIds = columnIds;
    this.columns = this.buildColumns();
    const table = this.getTable();
    if (table) {
      table.columnsUpdated();
      table.updateData(false);
    }
  }

  private buildColumns(): EntityTableColumn<TopicTreeRow>[] {
    const columns: EntityTableColumn<TopicTreeRow>[] = [
      new EntityTableColumn<TopicTreeRow>('topic', 'topic-tree.column.topic', '40%', row => this.topicCell(row), () => ({}), true)
    ];
    const count = (id: TopicTreeColumnId, value: (n: TopicTreeNode) => number | null | undefined, sortable = true) =>
      new EntityTableColumn<TopicTreeRow>(id, topicTreeColumns.find(c => c.id === id).titleKey, '0px',
        row => row.kind === 'node' ? `${value(row.node) ?? ''}` : '',
        row => row.kind === 'node' && !value(row.node) ? MUTED : {}, sortable, () => ({}), () => undefined, true);
    for (const id of this.columnIds) {
      switch (id) {
        case 'subscriptions': columns.push(count(id, n => n.subscriptionsTotal)); break;
        case 'clients': columns.push(count(id, n => n.clientsTotal, false)); break;
        case 'retained': columns.push(count(id, n => n.retainedTotal)); break;
        case 'shared': columns.push(count(id, n => n.sharedGroupsTotal)); break;
        case 'integrations': columns.push(count(id, n => n.integrationsTotal)); break;
        case 'online':
          columns.push(new EntityTableColumn<TopicTreeRow>(id, 'topic-tree.column.online', '0px', row => this.onlineCell(row),
            () => ({}), false, () => ({}), () => undefined, true));
          break;
      }
    }
    return columns;
  }

  private topicCell(row: TopicTreeRow): string {
    const indent = row.depth * 24;
    if (row.kind === 'more') {
      const text = escapeHtml(this.translate.instant('topic-tree.show-more', { count: row.remaining }));
      return `<span class="tb-tree-more" style="padding-left: ${indent + 32}px;">${text}</span>`;
    }
    const node = row.node;
    if (row.flat) {
      return `<span>${escapeHtml(node.path ?? this.translate.instant('topic-tree.root'))}</span>`;
    }
    const toggle = `<span class="tb-tree-toggle material-icons${node.hasChildren ? '' : ' tb-tree-toggle-hidden'}">` +
      `${row.expanded ? 'expand_more' : 'chevron_right'}</span>`;
    const segment = node.path === null ? this.translate.instant('topic-tree.root')
      : node.segment === '' ? '∅' : node.segment;
    const segmentClass = node.wildcard ? ' tb-tree-wildcard' : node.segment === '' ? ' tb-tree-empty' : '';
    const childrenCount = node.childrenCount ? `<span class="tb-tree-children">${node.childrenCount}</span>` : '';
    return `<span class="tb-tree-node" style="padding-left: ${indent}px;">${toggle}` +
      `<span class="tb-tree-segment${segmentClass}">${escapeHtml(segment)}</span>${childrenCount}</span>`;
  }

  private onlineCell(row: TopicTreeRow): string {
    if (row.kind !== 'node' || (row.node.clientsOnline ?? null) === null) {
      return '';
    }
    const online = row.node.clientsOnline;
    const offline = row.node.clientsOffline;
    const color = (value: number, state: ConnectionState) => value ? connectionStateColor.get(state).content : 'inherit';
    return `<span style="color: ${color(online, ConnectionState.CONNECTED)}">${online}</span> / ` +
      `<span style="color: ${color(offline, ConnectionState.DISCONNECTED)}">${offline}</span>`;
  }

  private onRowClick(event: Event, row: TopicTreeRow): boolean {
    if (row.kind === 'more') {
      this.childPages.set(key(row.parentPath), (this.childPages.get(key(row.parentPath)) ?? 1) + 1);
      this.reload();
    } else if (!row.flat && row.node.hasChildren && (event.target as Element)?.closest?.('.tb-tree-toggle')) {
      const nodeKey = key(row.node.path);
      if (this.expanded.has(nodeKey)) {
        this.expanded.delete(nodeKey);
      } else {
        this.expanded.add(nodeKey);
      }
      this.reload();
    } else {
      this.handlers.nodeClicked?.(row);
    }
    return true;
  }

  // Expanding or paging a branch reloads the table, but the branches already on screen are reused instead of refetched.
  private reload(): void {
    this.reuseChildren = true;
    this.getTable().updateData(false);
  }

  private fetch(pageLink: PageLink): Observable<PageData<TopicTreeRow>> {
    const reuse = this.reuseChildren;
    this.reuseChildren = false;
    if (!reuse) {
      this.childCache.clear();
    }
    const columns = topicTreeBackendColumns(this.columnIds);
    const text = (pageLink.textSearch ?? '').trim();
    const searchText = text || null;
    if (searchText || !isTopicTreeFilterEmpty(this.query)) {
      return this.service.search(searchText, this.query, columns, pageLink).pipe(
        map(page => ({ ...page, data: page.data.map(n => this.nodeRow(n, 0, null, true)) })));
    }
    return this.service.getChildren(null, columns, pageLink).pipe(
      switchMap(page => this.withRevealed(page, columns)),
      switchMap(page => this.expand(page.data.map(n => this.nodeRow(n, 0, null, false)), columns, pageLink.sortOrder).pipe(
        map(rows => ({ ...page, data: rows })))),
      tap(page => this.notifyRevealed(page.data))
    );
  }

  // A revealed path may live under a top-level topic on another page, so that topic is pinned to the current one.
  private withRevealed(page: PageData<TopicTreeNode>, columns: string[]): Observable<PageData<TopicTreeNode>> {
    if (this.revealPath === null) {
      return of(page);
    }
    const top = this.revealPath.split('/')[0];
    if (page.data.some(n => n.path === top)) {
      return of(page);
    }
    return this.service.getNode(top, columns, { ignoreErrors: true }).pipe(
      map(node => ({ ...page, data: [node, ...page.data] })),
      catchError(() => of(page)));
  }

  private notifyRevealed(rows: TopicTreeRow[]): void {
    const path = this.revealPath;
    if (path === null) {
      return;
    }
    this.revealPath = null;
    const row = rows.find(r => r.kind === 'node' && r.node.path === path);
    if (row) {
      this.handlers.nodeRevealed?.(row);
    } else {
      this.handlers.revealFailed?.(path);
    }
  }

  private expand(rows: TopicTreeRow[], columns: string[], sort: SortOrder | null): Observable<TopicTreeRow[]> {
    if (!rows.length) {
      return of([]);
    }
    return forkJoin(rows.map(row => {
      if (row.kind !== 'node' || !row.node.hasChildren || !this.expanded.has(key(row.node.path))) {
        return of([row]);
      }
      return this.children(row, columns, sort).pipe(
        switchMap(children => this.expand(children, columns, sort)),
        map(children => [{ ...row, expanded: true }, ...children]));
    })).pipe(map(groups => groups.flat()));
  }

  // "Show more" only requests the pages that are not loaded yet and appends them to the rows already on screen.
  private children(parent: TopicTreeRow, columns: string[], sort: SortOrder | null): Observable<TopicTreeRow[]> {
    const path = parent.node.path;
    const cacheKey = key(path);
    const pages = this.childPages.get(cacheKey) ?? 1;
    const cached = this.childCache.get(cacheKey);
    if (cached && cached.pages === pages) {
      return of(cached.rows);
    }
    const loaded = cached && cached.pages < pages ? cached.pages : 0;
    const known = loaded ? cached.rows.filter(r => r.kind === 'node') : [];
    return forkJoin(Array.from({ length: pages - loaded }, (_, i) =>
      this.service.getChildren(path, columns, new PageLink(CHILD_PAGE_SIZE, loaded + i, null, sort)))).pipe(
      map(results => {
        const last = results[results.length - 1];
        const rows = [...known, ...results.flatMap(r => r.data).map(n => this.nodeRow(n, parent.depth + 1, path, false))];
        if (last.hasNext) {
          rows.push({
            id: `more:${cacheKey}`, kind: 'more', depth: parent.depth + 1, expanded: false, flat: false, parentPath: path,
            remaining: last.totalElements - rows.length
          });
        }
        this.childCache.set(cacheKey, { pages, rows });
        return rows;
      }));
  }

  private nodeRow(node: TopicTreeNode, depth: number, parentPath: string | null, flat: boolean): TopicTreeRow {
    return { id: key(node.path), kind: 'node', node, depth, expanded: false, flat, parentPath };
  }
}
