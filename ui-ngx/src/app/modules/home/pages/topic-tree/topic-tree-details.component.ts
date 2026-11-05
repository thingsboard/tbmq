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

import { ChangeDetectionStrategy, ChangeDetectorRef, Component, EventEmitter, Input, OnChanges, Output, SimpleChanges, inject } from '@angular/core';
import { Router } from '@angular/router';
import { HttpErrorResponse } from '@angular/common/http';
import { DecimalPipe, NgTemplateOutlet } from '@angular/common';
import { MatButton } from '@angular/material/button';
import { MatTab, MatTabContent, MatTabGroup } from '@angular/material/tabs';
import { TbIconComponent } from '@shared/components/icon.component';
import { TranslateModule, TranslateService } from '@ngx-translate/core';
import { DetailsPanelComponent } from '@home/components/details-panel.component';
import { EntitiesTableComponent } from '@home/components/entity/entities-table.component';
import { CdkCopyToClipboard } from '@angular/cdk/clipboard';
import { MatIcon } from '@angular/material/icon';
import { Store } from '@ngrx/store';
import { AppState } from '@core/core.state';
import { ActionNotificationShow } from '@core/notification/notification.actions';
import { ToastDirective } from '@shared/components/toast.directive';
import { HelpComponent } from '@shared/components/help.component';
import { TopicTreeService } from '@core/http/topic-tree.service';
import { topicTreeBackendColumns, TopicTreeNode } from '@shared/models/topic-tree.models';
import { ConnectionState, connectionStateColor } from '@shared/models/session.model';
import {
  TOPIC_TREE_TABLE_TABS,
  TopicTreeTableServices,
  TopicTreeTableTab,
  TopicTreeTabTableConfig
} from './topic-tree-tab-table-config';

interface DetailsRow {
  tab: TopicTreeTab;
  icon: string;
  label: string;
  count: number | null;
  sub: string;
}

export const TOPIC_TREE_TABS = ['details', ...TOPIC_TREE_TABLE_TABS] as const;
export type TopicTreeTab = typeof TOPIC_TREE_TABS[number];

@Component({
  selector: 'tb-topic-tree-details',
  templateUrl: './topic-tree-details.component.html',
  styleUrls: ['./topic-tree-details.component.scss'],
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [DecimalPipe, NgTemplateOutlet, MatButton, MatTab, MatTabContent, MatTabGroup, TranslateModule, DetailsPanelComponent,
    EntitiesTableComponent, HelpComponent, TbIconComponent, CdkCopyToClipboard, MatIcon, ToastDirective]
})
export class TopicTreeDetailsComponent implements OnChanges {

  @Input() node: TopicTreeNode;
  @Input() tab: TopicTreeTab = 'details';
  @Output() closed = new EventEmitter<void>();
  @Output() tabChange = new EventEmitter<TopicTreeTab>();
  @Output() refreshTree = new EventEmitter<void>();

  readonly tabs = TOPIC_TREE_TABS;
  readonly connectedColor = connectionStateColor.get(ConnectionState.CONNECTED);
  readonly disconnectedColor = connectionStateColor.get(ConnectionState.DISCONNECTED);
  full: TopicTreeNode;
  missing = false;
  tableConfigs: Partial<Record<TopicTreeTableTab, TopicTreeTabTableConfig>> = {};
  rows: DetailsRow[] = [];

  private readonly topicTree = inject(TopicTreeService);
  private readonly cd = inject(ChangeDetectorRef);
  private readonly store = inject(Store<AppState>);
  private readonly services: TopicTreeTableServices = {
    topicTree: this.topicTree,
    translate: inject(TranslateService),
    router: inject(Router)
  };

  get tabIndex(): number {
    return this.tabs.indexOf(this.tab);
  }

  ngOnChanges(changes: SimpleChanges): void {
    if (changes.node && this.node) {
      this.tableConfigs = {};
      this.full = null;
      this.missing = false;
      this.buildRows();
      this.loadFull();
    }
  }

  selectTab(index: number): void {
    this.tab = this.tabs[index];
    this.tabChange.emit(this.tab);
  }

  openTab(tab: TopicTreeTab): void {
    this.selectTab(this.tabs.indexOf(tab));
  }

  onTopicCopied(): void {
    this.store.dispatch(new ActionNotificationShow({
      message: this.services.translate.instant('topic-tree.topic-copied'),
      type: 'success',
      duration: 750,
      verticalPosition: 'bottom',
      horizontalPosition: 'right',
      target: 'topicTreeDetails'
    }));
  }

  tableConfig(tab: TopicTreeTableTab): TopicTreeTabTableConfig {
    return this.tableConfigs[tab] ??= new TopicTreeTabTableConfig(tab, this.node.path, this.services);
  }

  private loadFull(): void {
    const node = this.node;
    // The tree on screen can be older than the server snapshot, so a 404 means the topic is gone rather than an error.
    this.topicTree.getNode(node.path, topicTreeBackendColumns(), { ignoreErrors: true }).subscribe({
      next: full => {
        if (node !== this.node) {
          return;
        }
        this.full = full;
        this.buildRows();
        this.cd.markForCheck();
      },
      error: (error: HttpErrorResponse) => {
        if (node === this.node && error.status === 404) {
          this.missing = true;
          this.cd.markForCheck();
        }
      }
    });
  }

  // The rows that depend on the full node show no text until it has loaded.
  private buildRows(): void {
    const t = (key: string, params?: object) => this.services.translate.instant(key, params);
    const node = this.node;
    const full = this.full;
    this.rows = [
      {
        tab: 'subscriptions', icon: 'mdi:filter-outline', label: 'topic-tree.tab.subscriptions', count: node.subscriptionsTotal,
        sub: t('topic-tree.row.subscriptions', { exact: node.subscriptionsExact, nested: node.subscriptionsTotal - node.subscriptionsExact })
      },
      {
        tab: 'clients', icon: 'mdi:book-multiple', label: 'topic-tree.tab.clients', count: full ? full.clientsTotal : null,
        sub: full && !full.clientsTotal ? t('topic-tree.row.clients') : ''
      },
      {
        tab: 'shared', icon: 'mediation', label: 'topic-tree.column.shared', count: full ? full.sharedGroupsTotal : null,
        sub: full?.sharedSubscriptionsTotal == null ? '' : t('topic-tree.row.shared', { count: full.sharedSubscriptionsTotal })
      },
      {
        tab: 'integrations', icon: 'input', label: 'topic-tree.tab.integrations', count: full ? full.integrationsTotal : null,
        sub: t('topic-tree.row.integrations')
      },
      {
        tab: 'retained', icon: 'mdi:archive', label: 'topic-tree.column.retained', count: node.retainedTotal,
        sub: t('topic-tree.row.retained')
      }
    ];
  }
}
