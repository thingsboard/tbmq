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

import {
  ChangeDetectionStrategy,
  ChangeDetectorRef,
  Component,
  DestroyRef,
  ElementRef,
  EventEmitter,
  inject,
  Input,
  OnChanges,
  OnInit,
  Output,
  SimpleChanges
} from '@angular/core';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import { Observable, Subscription, forkJoin, of } from 'rxjs';
import { catchError, map } from 'rxjs/operators';
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
  private treeStale = false;
  private searchPage = 0;
  private loadToken = 0;
  private rootSubscription: Subscription;
  private searchSubscription: Subscription;
  private readonly service = inject(TopicTreeService);
  private readonly cd = inject(ChangeDetectorRef);
  private readonly host = inject<ElementRef<HTMLElement>>(ElementRef);
  private readonly destroyRef = inject(DestroyRef);

  get displayedColumns(): string[] {
    return ['topic', ...this.columns];
  }

  private get filterActive(): boolean {
    return !!this.filter && this.filter.length >= this.filterMinLength;
  }

  ngOnInit(): void {
    if (this.filterActive) {
      this.treeStale = true;
      this.applyFilter();
    } else {
      this.loadRoot();
    }
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
      this.treeStale = true;
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
    this.loadRoot(ancestors, path);
  }

  toggle(row: TopicTreeRow): void {
    if (row.kind !== 'node' || !row.node.hasChildren || row.loading || this.searchMode) {
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
    this.runSearch(false);
  }

  trackRow(_: number, row: TopicTreeRow): string {
    return row.kind + ':' + (row.kind === 'more' ? row.parentPath + '#' + row.nextPage : row.node.path);
  }

  private backendColumns(): string[] {
    return topicTreeColumns.filter(c => this.columns.includes(c.id) && c.backend).map(c => c.backend);
  }

  private loadRoot(expandPaths: (string | null)[] = [], revealPath?: string): void {
    const token = ++this.loadToken;
    this.rootSubscription?.unsubscribe();
    this.treeStale = false;
    this.loading = true;
    this.cd.markForCheck();
    const root$ = this.rootPath === null
      ? this.fetchLevel(null, 0, 0)
      : this.service.getNode(this.rootPath, this.backendColumns()).pipe(map(node => [this.nodeRow(node, 0, null)]));
    this.rootSubscription = root$.pipe(takeUntilDestroyed(this.destroyRef)).subscribe({
      next: rows => {
        this.treeRows = rows;
        this.expandSequentially(token, expandPaths, () => {
          if (this.rootPath !== null && this.treeRows.length && !this.treeRows[0].expanded && this.treeRows[0].node.hasChildren) {
            this.expand(this.treeRows[0]).subscribe(() => this.finishLoad(token, revealPath));
          } else {
            this.finishLoad(token, revealPath);
          }
        });
      },
      error: () => {
        this.loading = false;
        this.cd.markForCheck();
      }
    });
  }

  private finishLoad(token: number, revealPath?: string): void {
    if (token !== this.loadToken) {
      return;
    }
    this.loading = false;
    if (this.filterActive) {
      this.applyFilter();
    } else {
      this.publish();
    }
    if (revealPath !== undefined) {
      this.selectRevealed(token, revealPath);
    }
  }

  private selectRevealed(token: number, path: string): void {
    this.ensureRow(token, path).subscribe(row => {
      if (row) {
        this.publish();
        this.nodeSelected.emit(row.node);
        setTimeout(() => this.host.nativeElement.querySelector('tr.selected')?.scrollIntoView({ block: 'center' }));
      }
    });
  }

  private ensureRow(token: number, path: string | null): Observable<TopicTreeRow | null> {
    const found = this.findRow(path);
    if (found || path === null) {
      return of(found ?? null);
    }
    const segments = path.split('/');
    const parentPath = segments.length > 1 ? segments.slice(0, -1).join('/') : null;
    const parentRow = parentPath === null ? null : this.findRow(parentPath);
    if (parentPath !== null && !parentRow) {
      return of(null);
    }
    return this.service.getNode(path, this.backendColumns()).pipe(
      takeUntilDestroyed(this.destroyRef),
      map(node => token === this.loadToken ? this.insertRow(node, parentRow, parentPath) : null),
      catchError(() => of(null))
    );
  }

  private insertRow(node: TopicTreeNode, parentRow: TopicTreeRow | null, parentPath: string | null): TopicTreeRow {
    const depth = parentRow ? parentRow.depth + 1 : 0;
    const start = parentRow ? this.treeRows.indexOf(parentRow) + 1 : 0;
    let end = start;
    while (end < this.treeRows.length && this.treeRows[end].depth >= depth) {
      end++;
    }
    const last = this.treeRows[end - 1];
    const insertAt = end > start && last.kind === 'more' && last.depth === depth ? end - 1 : end;
    const row = this.nodeRow(node, depth, parentPath);
    this.treeRows.splice(insertAt, 0, row);
    return row;
  }

  private findRow(path: string | null): TopicTreeRow | undefined {
    return this.treeRows.find(r => r.kind === 'node' && r.node.path === path);
  }

  private expandSequentially(token: number, paths: (string | null)[], done: () => void): void {
    if (token !== this.loadToken) {
      return;
    }
    if (!paths.length) {
      done();
      return;
    }
    const [head, ...rest] = paths;
    this.ensureRow(token, head).subscribe(row => {
      if (token !== this.loadToken) {
        return;
      }
      if (!row || row.expanded || !row.node.hasChildren) {
        this.expandSequentially(token, rest, done);
        return;
      }
      this.expand(row).subscribe(() => this.expandSequentially(token, rest, done));
    });
  }

  private expand(row: TopicTreeRow): Observable<void> {
    if (row.loading || row.expanded) {
      return of(undefined);
    }
    row.loading = true;
    this.publish();
    return this.fetchLevel(row.node.path, row.depth + 1, 0).pipe(
      map(children => {
        row.loading = false;
        const index = this.treeRows.indexOf(row);
        if (index >= 0) {
          row.expanded = true;
          this.treeRows.splice(index + 1, 0, ...children);
        }
      }),
      catchError(() => {
        row.loading = false;
        this.publish();
        return of(undefined);
      })
    );
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
    if (more.loading) {
      return;
    }
    more.loading = true;
    this.publish();
    this.fetchLevel(more.parentPath, more.depth, more.nextPage).subscribe({
      next: rows => {
        const index = this.treeRows.indexOf(more);
        if (index >= 0) {
          const loaded = new Set(this.treeRows.filter(r => r.kind === 'node' && r.parentPath === more.parentPath).map(r => r.node.path));
          this.treeRows.splice(index, 1, ...rows.filter(r => r.kind === 'more' || !loaded.has(r.node.path)));
        }
        this.publish();
      },
      error: () => {
        more.loading = false;
        this.publish();
      }
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
    if (!this.filterActive) {
      this.searchSubscription?.unsubscribe();
      this.searchMode = false;
      this.searchTotal = 0;
      if (this.treeStale) {
        this.loadRoot();
      } else {
        this.loading = false;
        this.publish();
      }
      return;
    }
    this.searchMode = true;
    this.searchPage = 0;
    this.runSearch(true);
  }

  private runSearch(reset: boolean): void {
    this.searchSubscription?.unsubscribe();
    this.loading = true;
    this.cd.markForCheck();
    const start = reset ? 0 : this.searchPage + 1;
    const pages = reset ? this.searchPage + 1 : 1;
    const requests = Array.from({ length: pages }, (_, i) =>
      this.service.search(this.filter, this.backendColumns(), start + i, this.pageSize));
    this.searchSubscription = forkJoin(requests).pipe(takeUntilDestroyed(this.destroyRef)).subscribe({
      next: results => {
        const nodes = results.flatMap(r => r.data);
        this.searchTotal = results[results.length - 1].totalElements;
        const searchRows = nodes.map(n => this.nodeRow(n, 0, null));
        this.rows = reset ? searchRows : [...this.rows, ...searchRows];
        if (!reset) {
          this.searchPage++;
        }
        this.loading = false;
        this.cd.markForCheck();
      },
      error: () => {
        this.loading = false;
        this.cd.markForCheck();
      }
    });
  }

  private publish(): void {
    if (!this.searchMode) {
      this.rows = [...this.treeRows];
    }
    this.cd.markForCheck();
  }
}
