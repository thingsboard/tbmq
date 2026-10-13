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

import { ChangeDetectionStrategy, ChangeDetectorRef, Component, EventEmitter, Input, OnChanges, Output, SimpleChanges, inject } from '@angular/core';
import { Router } from '@angular/router';
import { HttpErrorResponse } from '@angular/common/http';
import { DatePipe, DecimalPipe, NgTemplateOutlet } from '@angular/common';
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
import { TopicTreeNode } from '@shared/models/topic-tree.models';
import { ConnectionState, connectionStateColor } from '@shared/models/session.model';
import { TopicTreeTableServices, TopicTreeTableTab, TopicTreeTabTableConfig } from './topic-tree-tab-table-config';

interface DetailsRow {
  tab: TopicTreeTab;
  icon: string;
  label: string;
  count: number | null;
  sub: string;
}

export const TOPIC_TREE_TABS = ['details', 'subscriptions', 'clients', 'shared', 'integrations', 'retained', 'dropped'] as const;
export type TopicTreeTab = typeof TOPIC_TREE_TABS[number];

@Component({
  selector: 'tb-topic-tree-details',
  templateUrl: './topic-tree-details.component.html',
  styleUrls: ['./topic-tree-details.component.scss'],
  changeDetection: ChangeDetectionStrategy.OnPush,
  providers: [DatePipe],
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
    datePipe: inject(DatePipe),
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
    this.topicTree.getNode(node.path, ['clients', 'online', 'shared', 'integrations', 'dropped'], { ignoreErrors: true }).subscribe({
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
        sub: t('topic-tree.row.clients')
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
      },
      {
        tab: 'dropped', icon: 'mdi:filter-remove-outline', label: 'topic-tree.column.dropped', count: full ? full.dropped24h : null,
        sub: t('topic-tree.column.dropped-hint')
      }
    ];
  }
}
