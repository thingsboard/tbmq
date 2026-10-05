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

import { AfterViewInit, Component, DestroyRef, ElementRef, OnInit, ViewChild, inject } from '@angular/core';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import { FormControl, ReactiveFormsModule } from '@angular/forms';
import { ActivatedRoute, Router } from '@angular/router';
import { debounceTime, distinctUntilChanged } from 'rxjs/operators';
import { MatToolbar } from '@angular/material/toolbar';
import { MatIconButton } from '@angular/material/button';
import { MatIcon } from '@angular/material/icon';
import { MatTooltip } from '@angular/material/tooltip';
import { MatFormField, MatLabel, MatSuffix } from '@angular/material/form-field';
import { MatInput } from '@angular/material/input';
import { MatDrawer, MatDrawerContainer, MatDrawerContent } from '@angular/material/sidenav';
import { TranslateModule } from '@ngx-translate/core';
import {
  TOPIC_TREE_COLUMNS_STORAGE_KEY, TopicTreeColumnId, TopicTreeNode, topicTreeColumns
} from '@shared/models/topic-tree.models';
import { TopicTreeTableComponent } from './topic-tree-table.component';
import { TopicTreeColumnsMenuComponent } from './topic-tree-columns-menu.component';
import { TopicTreeResolveComponent } from './topic-tree-resolve.component';
import { TopicTreeDetailsComponent, TopicTreeTab } from './topic-tree-details.component';

const FILTER_MIN_LENGTH = 2;

type ToolbarMode = 'default' | 'search' | 'subscribers';

@Component({
  selector: 'tb-topic-tree',
  templateUrl: './topic-tree.component.html',
  styleUrls: ['./topic-tree.component.scss'],
  imports: [ReactiveFormsModule, MatToolbar, MatIconButton, MatIcon, MatTooltip, MatFormField, MatLabel, MatSuffix, MatInput, MatDrawer,
    MatDrawerContainer, MatDrawerContent, TranslateModule, TopicTreeTableComponent, TopicTreeColumnsMenuComponent, TopicTreeDetailsComponent,
    TopicTreeResolveComponent]
})
export class TopicTreeComponent implements OnInit, AfterViewInit {

  @ViewChild('table') table: TopicTreeTableComponent;
  @ViewChild('resolve') resolve: TopicTreeResolveComponent;
  @ViewChild('searchInput') searchInput: ElementRef<HTMLInputElement>;

  readonly filterMinLength = FILTER_MIN_LENGTH;
  readonly filterControl = new FormControl<string>('');
  columns: TopicTreeColumnId[] = this.loadColumns();
  filter = '';
  selectedNode: TopicTreeNode | null = null;
  selectedTab: TopicTreeTab = 'details';
  toolbarMode: ToolbarMode = 'default';
  private initialPath: string | null = null;

  private readonly route = inject(ActivatedRoute);
  private readonly router = inject(Router);
  private readonly destroyRef = inject(DestroyRef);

  ngOnInit(): void {
    const params = this.route.snapshot.queryParamMap;
    if (params.get('columns')) {
      const ids = params.get('columns').split(',') as TopicTreeColumnId[];
      const valid = topicTreeColumns.map(c => c.id).filter(id => ids.includes(id));
      if (valid.length) {
        this.columns = valid;
      }
    }
    this.initialPath = params.get('path');
    const filter = params.get('filter') ?? '';
    this.filterControl.setValue(filter, { emitEvent: false });
    this.filter = filter;
    if (filter) {
      this.toolbarMode = 'search';
    }
    this.filterControl.valueChanges.pipe(
      debounceTime(300), distinctUntilChanged(), takeUntilDestroyed(this.destroyRef)
    ).subscribe(value => {
      this.filter = (value ?? '').trim();
      this.syncUrl();
    });
  }

  ngAfterViewInit(): void {
    if (this.initialPath !== null) {
      setTimeout(() => this.table.reveal(this.initialPath));
    }
  }

  onColumnsChange(columns: TopicTreeColumnId[]): void {
    this.columns = columns;
    try {
      localStorage.setItem(TOPIC_TREE_COLUMNS_STORAGE_KEY, JSON.stringify(columns));
    } catch { /* storage unavailable */ }
    this.syncUrl();
  }

  onNodeSelected(node: TopicTreeNode): void {
    this.selectedTab = 'details';
    this.selectedNode = node;
    this.syncUrl();
  }

  onChipClicked(event: { node: TopicTreeNode; column: TopicTreeColumnId }): void {
    const tabByColumn: Partial<Record<TopicTreeColumnId, TopicTreeTab>> = {
      subscriptions: 'subscriptions', clients: 'clients', online: 'clients', shared: 'shared',
      integrations: 'integrations', retained: 'retained', dropped: 'dropped'
    };
    this.selectedTab = tabByColumn[event.column] ?? 'subscriptions';
    this.selectedNode = event.node;
    this.syncUrl();
  }

  closeDetails(): void {
    this.selectedNode = null;
    this.syncUrl();
  }

  enterMode(mode: ToolbarMode): void {
    this.toolbarMode = mode;
    if (mode === 'search') {
      setTimeout(() => this.searchInput.nativeElement.focus());
    } else if (mode === 'subscribers') {
      this.resolve.focus();
    }
  }

  exitSearch(): void {
    this.toolbarMode = 'default';
    this.filterControl.setValue('');
  }

  revealPath(path: string): void {
    this.filterControl.setValue('');
    this.filter = '';
    this.selectedNode = null;
    // the table must see the cleared filter input before it reloads the tree
    setTimeout(() => this.table.reveal(path));
  }

  openSubscriptions(filter: string): void {
    this.router.navigate(['/subscriptions'], { queryParams: { topicFilter: filter, topicMatch: 'EXACT' } });
  }

  refresh(): void {
    this.table.reload();
  }

  private loadColumns(): TopicTreeColumnId[] {
    try {
      const stored = JSON.parse(localStorage.getItem(TOPIC_TREE_COLUMNS_STORAGE_KEY) ?? 'null');
      if (Array.isArray(stored)) {
        const valid = topicTreeColumns.map(c => c.id).filter(id => stored.includes(id));
        if (valid.length) {
          return valid;
        }
      }
    } catch { /* ignore */ }
    return topicTreeColumns.filter(c => c.default).map(c => c.id);
  }

  private syncUrl(): void {
    this.router.navigate([], {
      relativeTo: this.route,
      replaceUrl: true,
      queryParams: {
        path: this.selectedNode?.path ?? null,
        filter: this.filter || null,
        columns: this.columns.join(',')
      }
    });
  }
}

