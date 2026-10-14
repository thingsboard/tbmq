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

import { Component, OnDestroy, OnInit, TemplateRef, ViewChild, ViewContainerRef, inject } from '@angular/core';
import { ActivatedRoute, Router } from '@angular/router';
import { Overlay, OverlayRef } from '@angular/cdk/overlay';
import { TemplatePortal } from '@angular/cdk/portal';
import { MatButton } from '@angular/material/button';
import { MatCheckbox } from '@angular/material/checkbox';
import { MatDivider } from '@angular/material/divider';
import { MatDrawer, MatDrawerContainer, MatDrawerContent } from '@angular/material/sidenav';
import { Store } from '@ngrx/store';
import { TranslateModule, TranslateService } from '@ngx-translate/core';
import { AppState } from '@core/core.state';
import { ActionNotificationShow } from '@core/notification/notification.actions';
import { EntitiesTableComponent } from '@home/components/entity/entities-table.component';
import { TopicTreeService } from '@core/http/topic-tree.service';
import {
  defaultTopicTreeColumns,
  knownTopicTreeColumns,
  TOPIC_TREE_COLUMNS_STORAGE_KEY,
  TopicTreeColumnId,
  TopicTreeNode,
  TopicTreeRow,
  topicTreeColumns
} from '@shared/models/topic-tree.models';
import { TopicTreeDetailsComponent, TopicTreeTab } from './topic-tree-details.component';
import { TopicTreeTableConfig } from './topic-tree-table-config';
import { TopicTreeTableHeaderComponent } from './topic-tree-table-header.component';

@Component({
  selector: 'tb-topic-tree',
  templateUrl: './topic-tree.component.html',
  styleUrls: ['./topic-tree.component.scss'],
  imports: [MatButton, MatCheckbox, MatDivider, MatDrawer, MatDrawerContainer, MatDrawerContent, TranslateModule,
    EntitiesTableComponent, TopicTreeDetailsComponent]
})
export class TopicTreeComponent implements OnInit, OnDestroy {

  @ViewChild('columnsPanel') columnsPanel: TemplateRef<unknown>;

  readonly allColumns = topicTreeColumns;
  columns: TopicTreeColumnId[] = this.loadColumns();
  tableConfig: TopicTreeTableConfig;
  selectedNode: TopicTreeNode | null = null;
  selectedTab: TopicTreeTab = 'details';

  private columnsOverlay: OverlayRef;
  private readonly route = inject(ActivatedRoute);
  private readonly router = inject(Router);
  private readonly store = inject(Store<AppState>);
  private readonly service = inject(TopicTreeService);
  private readonly translate = inject(TranslateService);
  private readonly overlay = inject(Overlay);
  private readonly viewContainerRef = inject(ViewContainerRef);

  ngOnInit(): void {
    const params = this.route.snapshot.queryParamMap;
    const fromUrl = knownTopicTreeColumns(params.get('columns')?.split(','));
    if (fromUrl.length) {
      this.columns = fromUrl;
    }
    this.tableConfig = new TopicTreeTableConfig(this.service, this.translate, this.columns, {
      nodeClicked: row => this.onNodeClicked(row),
      nodeRevealed: row => this.openDetails(row),
      revealFailed: path => this.openDetailsByPath(path),
      openColumns: event => this.openColumns(event)
    }, TopicTreeTableHeaderComponent);
    const path = params.get('path');
    if (path !== null) {
      this.tableConfig.reveal(path);
    }
  }

  ngOnDestroy(): void {
    this.columnsOverlay?.dispose();
  }

  toggleColumn(id: TopicTreeColumnId): void {
    const next = this.columns.includes(id) ? this.columns.filter(c => c !== id) : [...this.columns, id];
    this.applyColumns(knownTopicTreeColumns(next));
  }

  resetColumns(): void {
    this.applyColumns(defaultTopicTreeColumns());
  }

  closeDetails(): void {
    this.selectedNode = null;
    this.setCurrentRow(null);
    this.syncUrl();
  }

  refreshAfterMissing(): void {
    this.closeDetails();
    this.tableConfig.getTable().updateData();
  }

  private applyColumns(columns: TopicTreeColumnId[]): void {
    this.columns = columns;
    try {
      localStorage.setItem(TOPIC_TREE_COLUMNS_STORAGE_KEY, JSON.stringify(columns));
    } catch { /* storage unavailable */ }
    this.tableConfig.setColumns(columns);
    this.syncUrl();
  }

  // Same as the other entity tables: clicking the open row closes its details.
  private onNodeClicked(row: TopicTreeRow): void {
    if (this.selectedNode?.path === row.node.path) {
      this.closeDetails();
    } else {
      this.openDetails(row);
    }
  }

  private openDetails(row: TopicTreeRow): void {
    this.selectedTab = 'details';
    this.selectedNode = row.node;
    this.setCurrentRow(row);
    this.syncUrl();
  }

  // A deep-linked topic can sit beyond the loaded page of its branch, or be gone by now: it is opened from the server
  // directly, without a row to highlight, or reported as missing.
  private openDetailsByPath(path: string): void {
    this.service.getNode(path, [], { ignoreErrors: true }).subscribe({
      next: node => {
        this.selectedTab = 'details';
        this.selectedNode = node;
        this.setCurrentRow(null);
      },
      error: () => {
        this.store.dispatch(new ActionNotificationShow({
          message: this.translate.instant('topic-tree.topic-not-found', { path }),
          type: 'error'
        }));
        this.syncUrl();
      }
    });
  }

  private setCurrentRow(row: TopicTreeRow | null): void {
    const table = this.tableConfig.getTable();
    if (table) {
      table.dataSource.currentEntity = row;
    }
  }

  private openColumns(event: MouseEvent): void {
    this.columnsOverlay?.dispose();
    const anchor = (event.currentTarget ?? event.target) as HTMLElement;
    this.columnsOverlay = this.overlay.create({
      hasBackdrop: true,
      backdropClass: 'cdk-overlay-transparent-backdrop',
      positionStrategy: this.overlay.position().flexibleConnectedTo(anchor).withPositions([
        { originX: 'end', originY: 'bottom', overlayX: 'end', overlayY: 'top' }
      ])
    });
    this.columnsOverlay.backdropClick().subscribe(() => this.columnsOverlay.dispose());
    this.columnsOverlay.attach(new TemplatePortal(this.columnsPanel, this.viewContainerRef));
  }

  private loadColumns(): TopicTreeColumnId[] {
    try {
      const stored = knownTopicTreeColumns(JSON.parse(localStorage.getItem(TOPIC_TREE_COLUMNS_STORAGE_KEY) ?? 'null'));
      if (stored.length) {
        return stored;
      }
    } catch { /* ignore */ }
    return defaultTopicTreeColumns();
  }

  private syncUrl(): void {
    this.router.navigate([], {
      relativeTo: this.route,
      replaceUrl: true,
      queryParams: {
        path: this.selectedNode?.path ?? null,
        columns: this.columns.join(',')
      }
    });
  }
}
