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

import { AfterViewInit, Component, DestroyRef, OnInit, ViewChild, inject } from '@angular/core';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import { FormControl } from '@angular/forms';
import { ActivatedRoute, Router } from '@angular/router';
import { debounceTime, distinctUntilChanged } from 'rxjs/operators';
import { ReactiveFormsModule } from '@angular/forms';
import { MatToolbar } from '@angular/material/toolbar';
import { MatButton, MatIconButton } from '@angular/material/button';
import { MatIcon } from '@angular/material/icon';
import { MatTooltip } from '@angular/material/tooltip';
import { MatFormField, MatHint, MatPrefix, MatSuffix } from '@angular/material/form-field';
import { MatInput } from '@angular/material/input';
import { TranslateModule } from '@ngx-translate/core';
import {
  TOPIC_TREE_COLUMNS_STORAGE_KEY, TopicTreeColumnId, TopicTreeNode, topicTreeColumns
} from '@shared/models/topic-tree.models';
import { TopicTreeTableComponent } from './topic-tree-table.component';
import { TopicTreeColumnsMenuComponent } from './topic-tree-columns-menu.component';

const FILTER_MIN_LENGTH = 2;

@Component({
  selector: 'tb-topic-tree',
  templateUrl: './topic-tree.component.html',
  styleUrls: ['./topic-tree.component.scss'],
  imports: [ReactiveFormsModule, MatToolbar, MatButton, MatIconButton, MatIcon, MatTooltip, MatFormField, MatHint, MatPrefix, MatSuffix, MatInput, TranslateModule, TopicTreeTableComponent, TopicTreeColumnsMenuComponent]
})
export class TopicTreeComponent implements OnInit, AfterViewInit {

  @ViewChild('table') table: TopicTreeTableComponent;

  readonly filterMinLength = FILTER_MIN_LENGTH;
  readonly filterControl = new FormControl<string>('');
  columns: TopicTreeColumnId[] = this.loadColumns();
  filter = '';
  selectedNode: TopicTreeNode | null = null;
  private initialPath: string | null = null;

  private readonly route = inject(ActivatedRoute);
  private readonly router = inject(Router);
  private readonly destroyRef = inject(DestroyRef);

  ngOnInit(): void {
    const params = this.route.snapshot.queryParamMap;
    if (params.get('columns')) {
      const ids = params.get('columns').split(',') as TopicTreeColumnId[];
      this.columns = topicTreeColumns.map(c => c.id).filter(id => ids.includes(id));
    }
    this.initialPath = params.get('path');
    const filter = params.get('filter') ?? '';
    this.filterControl.setValue(filter, { emitEvent: false });
    this.filter = filter;
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
    this.selectedNode = node;
    this.syncUrl();
  }

  onChipClicked(event: { node: TopicTreeNode; column: TopicTreeColumnId }): void {
    this.selectedNode = event.node;
    this.syncUrl();
  }

  clearFilter(): void {
    this.filterControl.setValue('');
  }

  refresh(): void {
    this.table.reload();
  }

  openChart(): void {
    this.router.navigate(['/topic-tree/chart'], { queryParams: { filter: this.filter || null } });
  }

  private loadColumns(): TopicTreeColumnId[] {
    try {
      const stored = JSON.parse(localStorage.getItem(TOPIC_TREE_COLUMNS_STORAGE_KEY) ?? 'null');
      if (Array.isArray(stored)) {
        return topicTreeColumns.map(c => c.id).filter(id => stored.includes(id));
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

