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

import { ChangeDetectionStrategy, ChangeDetectorRef, Component, DestroyRef, EventEmitter, Input, OnChanges, OnInit, Output, SimpleChanges, inject } from '@angular/core';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import { Observable, forkJoin, of } from 'rxjs';
import { map } from 'rxjs/operators';
import { DecimalPipe } from '@angular/common';
import { MatButton, MatIconButton } from '@angular/material/button';
import { MatIcon } from '@angular/material/icon';
import { MatProgressBar } from '@angular/material/progress-bar';
import { MatProgressSpinner } from '@angular/material/progress-spinner';
import { MatTableModule } from '@angular/material/table';
import { MatTooltip } from '@angular/material/tooltip';
import { TranslateModule } from '@ngx-translate/core';
import { TopicTreeService } from '@core/http/topic-tree.service';
import { PageData } from '@shared/models/page/page-data';
import { TopicTreeColumnId, TopicTreeNode, TopicTreeRow, topicTreeColumns } from '@shared/models/topic-tree.models';

@Component({
  selector: 'tb-topic-tree-table',
  templateUrl: './topic-tree-table.component.html',
  styleUrls: ['./topic-tree-table.component.scss'],
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [DecimalPipe, MatButton, MatIconButton, MatIcon, MatProgressBar, MatProgressSpinner, MatTableModule, MatTooltip, TranslateModule]
})
export class TopicTreeTableComponent implements OnInit, OnChanges {

  @Input() columns: TopicTreeColumnId[] = topicTreeColumns.filter(c => c.default).map(c => c.id);
  @Input() rootPath: string | null = null;
  @Input() filter = '';
  @Input() compact = false;
  @Input() pageSize = 100;
  @Input() filterMinLength = 2;
  @Output() nodeSelected = new EventEmitter<TopicTreeNode>();
  @Output() chipClicked = new EventEmitter<{ node: TopicTreeNode; column: TopicTreeColumnId }>();

  rows: TopicTreeRow[] = [];
  selectedPath: string | null | undefined;
  searchMode = false;
  searchTotal = 0;
  loading = false;

  private treeRows: TopicTreeRow[] = [];
  private searchPage = 0;
  private readonly service = inject(TopicTreeService);
  private readonly cd = inject(ChangeDetectorRef);
  private readonly destroyRef = inject(DestroyRef);

  get displayedColumns(): string[] {
    return ['topic', ...this.columns];
  }

  ngOnInit(): void {
    this.loadRoot();
  }

  ngOnChanges(changes: SimpleChanges): void {
    if (changes.filter && !changes.filter.firstChange) {
      this.applyFilter();
    }
    if (changes.columns && !changes.columns.firstChange) {
      this.reload();
    }
    if (changes.rootPath && !changes.rootPath.firstChange) {
      this.loadRoot();
    }
  }

  reload(): void {
    if (this.searchMode) {
      this.runSearch(true);
      return;
    }
    const expanded = this.treeRows.filter(r => r.kind === 'node' && r.expanded).map(r => r.node.path);
    this.loadRoot(expanded);
  }

  reveal(path: string): void {
    const segments = path.split('/');
    const ancestors: (string | null)[] = [];
    for (let i = 1; i < segments.length; i++) {
      ancestors.push(segments.slice(0, i).join('/'));
    }
    this.selectedPath = path;
    this.loadRoot(ancestors);
  }

  toggle(row: TopicTreeRow): void {
    if (row.kind !== 'node' || !row.node.hasChildren || this.searchMode) {
      return;
    }
    if (row.expanded) {
      this.collapse(row);
    } else {
      this.expand(row).subscribe(() => this.publish());
    }
  }

  select(row: TopicTreeRow): void {
    if (row.kind === 'more') {
      this.loadMore(row);
      return;
    }
    this.selectedPath = row.node.path;
    this.nodeSelected.emit(row.node);
    this.cd.markForCheck();
  }

  clickChip(event: Event, row: TopicTreeRow, column: TopicTreeColumnId): void {
    event.stopPropagation();
    this.selectedPath = row.node.path;
    this.chipClicked.emit({ node: row.node, column });
  }

  loadNextSearchPage(): void {
    this.searchPage++;
    this.runSearch(false);
  }

  trackRow(_: number, row: TopicTreeRow): string {
    return row.kind + ':' + (row.kind === 'more' ? row.parentPath + '#' + row.nextPage : row.node.path);
  }

  private backendColumns(): string[] {
    return topicTreeColumns.filter(c => this.columns.includes(c.id) && c.backend).map(c => c.backend);
  }

  private loadRoot(expandPaths: (string | null)[] = []): void {
    this.loading = true;
    const root$ = this.rootPath === null
      ? this.fetchLevel(null, 0, 0)
      : this.service.getNode(this.rootPath, this.backendColumns()).pipe(map(node => [this.nodeRow(node, 0, null)]));
    root$.subscribe(rows => {
      this.treeRows = rows;
      this.expandSequentially(expandPaths.filter(p => p !== this.rootPath || this.rootPath !== null), () => {
        if (this.rootPath !== null && this.treeRows.length && !this.treeRows[0].expanded && this.treeRows[0].node.hasChildren) {
          this.expand(this.treeRows[0]).subscribe(() => this.finishLoad());
        } else {
          this.finishLoad();
        }
      });
    });
  }

  private finishLoad(): void {
    this.loading = false;
    if (this.filter && this.filter.length >= this.filterMinLength) {
      this.applyFilter();
    } else {
      this.publish();
    }
  }

  private expandSequentially(paths: (string | null)[], done: () => void): void {
    if (!paths.length) {
      done();
      return;
    }
    const [head, ...rest] = paths;
    const row = this.treeRows.find(r => r.kind === 'node' && r.node.path === head);
    if (!row || row.expanded || !row.node.hasChildren) {
      this.expandSequentially(rest, done);
      return;
    }
    this.expand(row).subscribe(() => this.expandSequentially(rest, done));
  }

  private expand(row: TopicTreeRow): Observable<void> {
    row.loading = true;
    this.publish();
    return this.fetchLevel(row.node.path, row.depth + 1, 0).pipe(map(children => {
      row.loading = false;
      row.expanded = true;
      const index = this.treeRows.indexOf(row);
      this.treeRows.splice(index + 1, 0, ...children);
    }));
  }

  private collapse(row: TopicTreeRow): void {
    const index = this.treeRows.indexOf(row);
    let end = index + 1;
    while (end < this.treeRows.length && this.treeRows[end].depth > row.depth) {
      end++;
    }
    this.treeRows.splice(index + 1, end - index - 1);
    row.expanded = false;
    this.publish();
  }

  private loadMore(more: TopicTreeRow): void {
    more.loading = true;
    this.publish();
    this.fetchLevel(more.parentPath, more.depth, more.nextPage).subscribe(rows => {
      const index = this.treeRows.indexOf(more);
      this.treeRows.splice(index, 1, ...rows);
      this.publish();
    });
  }

  private fetchLevel(path: string | null, depth: number, page: number): Observable<TopicTreeRow[]> {
    return this.service.getChildren(path, this.backendColumns(), page, this.pageSize).pipe(
      takeUntilDestroyed(this.destroyRef),
      map((data: PageData<TopicTreeNode>) => {
        const rows = data.data.map(n => this.nodeRow(n, depth, path));
        if (data.hasNext) {
          const remaining = data.totalElements - (page + 1) * this.pageSize;
          rows.push({ node: null, depth, expanded: false, loading: false, kind: 'more', parentPath: path, nextPage: page + 1, remaining });
        }
        return rows;
      })
    );
  }

  private nodeRow(node: TopicTreeNode, depth: number, parentPath: string | null): TopicTreeRow {
    return { node, depth, expanded: false, loading: false, kind: 'node', parentPath };
  }

  private applyFilter(): void {
    const active = !!this.filter && this.filter.length >= this.filterMinLength;
    if (!active) {
      this.searchMode = false;
      this.publish();
      return;
    }
    this.searchMode = true;
    this.searchPage = 0;
    this.runSearch(true);
  }

  private runSearch(reset: boolean): void {
    this.loading = true;
    this.cd.markForCheck();
    const pages = reset ? this.searchPage + 1 : 1;
    const start = reset ? 0 : this.searchPage;
    const requests = Array.from({ length: pages }, (_, i) =>
      this.service.search(this.filter, this.backendColumns(), start + i, this.pageSize));
    forkJoin(requests.length ? requests : [of(null)]).pipe(takeUntilDestroyed(this.destroyRef)).subscribe(results => {
      const nodes = results.filter(Boolean).flatMap(r => r.data);
      const last = results[results.length - 1];
      this.searchTotal = last ? last.totalElements : 0;
      const searchRows = nodes.map(n => this.nodeRow(n, 0, null));
      this.rows = reset ? searchRows : [...this.rows, ...searchRows];
      this.loading = false;
      this.cd.markForCheck();
    });
  }

  private publish(): void {
    if (!this.searchMode) {
      this.rows = [...this.treeRows];
    }
    this.cd.markForCheck();
  }
}

