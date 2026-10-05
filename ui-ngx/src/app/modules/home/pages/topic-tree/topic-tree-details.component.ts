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
import { DecimalPipe, NgTemplateOutlet } from '@angular/common';
import { Observable } from 'rxjs';
import { map } from 'rxjs/operators';
import { MatButton } from '@angular/material/button';
import { MatProgressBar } from '@angular/material/progress-bar';
import { MatTab, MatTabContent, MatTabGroup } from '@angular/material/tabs';
import { MatTableModule } from '@angular/material/table';
import { MatTooltip } from '@angular/material/tooltip';
import { TranslateModule } from '@ngx-translate/core';
import { DetailsPanelComponent } from '@home/components/details-panel.component';
import { CopyButtonComponent } from '@shared/components/button/copy-button.component';
import { TopicTreeService } from '@core/http/topic-tree.service';
import { SubscriptionService } from '@core/http/subscription.service';
import { RetainedMsgService } from '@core/http/retained-msg.service';
import { DroppedMsgService } from '@core/http/dropped-msg.service';
import { ClientSubscriptionsQuery } from '@shared/models/subscription.model';
import { RetainedMessagesQuery } from '@shared/models/retained-message.model';
import { DroppedMessagesQuery, droppedMsgReasonTranslationMap, MatchMode } from '@shared/models/dropped-message.model';
import { PageLink, TimePageLink } from '@shared/models/page/page-link';
import { Direction } from '@shared/models/page/sort-order';
import { qosFromName } from '@shared/models/session.model';
import { ClientType, clientTypeTranslationMap } from '@shared/models/client.model';
import { TopicTreeEntityKind, TopicTreeNode } from '@shared/models/topic-tree.models';

export const TOPIC_TREE_TABS = ['details', 'subscriptions', 'clients', 'shared', 'integrations', 'retained', 'dropped'] as const;
export type TopicTreeTab = typeof TOPIC_TREE_TABS[number];
type PreviewTab = Exclude<TopicTreeTab, 'details'>;

interface PreviewColumn {
  key: string;
  titleKey: string;
  number?: boolean;
  translate?: boolean;
}

type PreviewRow = Record<string, string | number>;

const PREVIEW_SIZE = 20;
const DAY_MS = 24 * 60 * 60 * 1000;

const PREVIEW_COLUMNS: Record<PreviewTab, PreviewColumn[]> = {
  subscriptions: [
    { key: 'clientId', titleKey: 'topic-tree.entity.client-id' },
    { key: 'topicFilter', titleKey: 'topic-tree.entity.topic-filter' },
    { key: 'qos', titleKey: 'topic-tree.entity.qos', number: true }
  ],
  clients: [
    { key: 'id', titleKey: 'topic-tree.entity.client-id' },
    { key: 'status', titleKey: 'topic-tree.entity.status', translate: true },
    { key: 'clientType', titleKey: 'topic-tree.entity.client-type', translate: true },
    { key: 'subscriptions', titleKey: 'topic-tree.entity.subscriptions', number: true }
  ],
  shared: [
    { key: 'id', titleKey: 'topic-tree.entity.group' },
    { key: 'subscriptions', titleKey: 'topic-tree.entity.subscriptions', number: true }
  ],
  integrations: [
    { key: 'id', titleKey: 'topic-tree.entity.integration' },
    { key: 'subscriptions', titleKey: 'topic-tree.entity.subscriptions', number: true }
  ],
  retained: [
    { key: 'topic', titleKey: 'topic-tree.entity.topic' },
    { key: 'payload', titleKey: 'topic-tree.entity.payload' },
    { key: 'qos', titleKey: 'topic-tree.entity.qos', number: true }
  ],
  dropped: [
    { key: 'topic', titleKey: 'topic-tree.entity.topic' },
    { key: 'clientId', titleKey: 'topic-tree.entity.client-id' },
    { key: 'reason', titleKey: 'topic-tree.entity.reason', translate: true },
    { key: 'count', titleKey: 'topic-tree.entity.count', number: true }
  ]
};

const OPEN_IN_KEYS: Record<PreviewTab, string> = {
  subscriptions: 'topic-tree.open-in-subscriptions',
  clients: 'topic-tree.open-in-sessions',
  shared: 'topic-tree.open-in-shared',
  integrations: 'topic-tree.open-in-integrations',
  retained: 'topic-tree.open-in-retained',
  dropped: 'topic-tree.open-in-dropped'
};

@Component({
  selector: 'tb-topic-tree-details',
  templateUrl: './topic-tree-details.component.html',
  styleUrls: ['./topic-tree-details.component.scss'],
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [DecimalPipe, NgTemplateOutlet, MatButton, MatProgressBar, MatTab, MatTabContent, MatTabGroup, MatTableModule,
    MatTooltip, TranslateModule, DetailsPanelComponent, CopyButtonComponent]
})
export class TopicTreeDetailsComponent implements OnChanges {

  @Input() node: TopicTreeNode;
  @Input() tab: TopicTreeTab = 'details';
  @Output() closed = new EventEmitter<void>();
  @Output() tabChange = new EventEmitter<TopicTreeTab>();

  readonly tabs = TOPIC_TREE_TABS;
  readonly openInKeys = OPEN_IN_KEYS;
  full: TopicTreeNode;
  rows: PreviewRow[] = [];
  total = 0;
  loading = false;

  private readonly topicTree = inject(TopicTreeService);
  private readonly subscriptions = inject(SubscriptionService);
  private readonly retained = inject(RetainedMsgService);
  private readonly dropped = inject(DroppedMsgService);
  private readonly router = inject(Router);
  private readonly cd = inject(ChangeDetectorRef);
  private requestId = 0;

  get tabIndex(): number {
    return this.tabs.indexOf(this.tab);
  }

  get previewTab(): PreviewTab | null {
    return this.tab === 'details' ? null : this.tab;
  }

  get previewColumns(): PreviewColumn[] {
    return this.previewTab ? PREVIEW_COLUMNS[this.previewTab] : [];
  }

  get previewColumnKeys(): string[] {
    return this.previewColumns.map(c => c.key);
  }

  ngOnChanges(changes: SimpleChanges): void {
    if (!this.node) {
      return;
    }
    if (changes.node) {
      this.loadFull();
    }
    this.loadTab();
  }

  selectTab(index: number): void {
    this.tab = this.tabs[index];
    this.tabChange.emit(this.tab);
    this.loadTab();
  }

  openTab(tab: TopicTreeTab): void {
    this.selectTab(this.tabs.indexOf(tab));
  }

  openIn(): void {
    const path = this.node.path;
    switch (this.previewTab) {
      case 'subscriptions':
        this.router.navigate(['/subscriptions'], { queryParams: { topicFilter: path, topicMatch: 'PREFIX' } });
        break;
      case 'clients':
        this.router.navigate(['/sessions'], { queryParams: { topicFilter: path, topicMatch: 'PREFIX' } });
        break;
      case 'retained':
        this.router.navigate(['/retained-messages'], { queryParams: { topicName: path, topicMatch: 'PREFIX' } });
        break;
      case 'dropped':
        this.router.navigate(['/dropped-messages'], { queryParams: { topicName: path, topicMatch: 'PREFIX' } });
        break;
      case 'shared':
        this.router.navigate(['/shared-subscriptions/manage']);
        break;
      case 'integrations':
        this.router.navigate(['/integrations']);
        break;
    }
  }

  private loadFull(): void {
    const node = this.node;
    this.full = null;
    this.topicTree.getNode(node.path, ['clients', 'online', 'shared', 'integrations', 'dropped']).subscribe({
      next: full => {
        if (node !== this.node) {
          return;
        }
        this.full = full;
        this.cd.markForCheck();
      },
      error: () => {}
    });
  }

  private loadTab(): void {
    const requestId = ++this.requestId;
    this.rows = [];
    this.total = 0;
    if (!this.previewTab) {
      this.loading = false;
      this.cd.markForCheck();
      return;
    }
    this.loading = true;
    this.cd.markForCheck();
    this.fetch(this.previewTab).subscribe({
      next: ({ rows, total }) => {
        if (requestId !== this.requestId) {
          return;
        }
        this.rows = rows;
        this.total = total;
        this.loading = false;
        this.cd.markForCheck();
      },
      error: () => {
        if (requestId === this.requestId) {
          this.loading = false;
          this.cd.markForCheck();
        }
      }
    });
  }

  private fetch(tab: PreviewTab): Observable<{ rows: PreviewRow[]; total: number }> {
    const path = this.node.path;
    switch (tab) {
      case 'subscriptions': {
        const query = new ClientSubscriptionsQuery(new PageLink(PREVIEW_SIZE, 0), { topicFilter: path, topicMatch: 'PREFIX' });
        return this.subscriptions.getClientSubscriptionsV2(query).pipe(map(p => ({
          total: p.totalElements,
          rows: p.data.map(s => ({ clientId: s.clientId, topicFilter: s.subscription.topicFilter, qos: qosFromName(s.subscription.qos) }))
        })));
      }
      case 'clients':
      case 'shared':
      case 'integrations': {
        const kind = tab.toUpperCase() as TopicTreeEntityKind;
        return this.topicTree.getEntities(path, kind, 0, PREVIEW_SIZE).pipe(map(p => ({
          total: p.totalElements,
          rows: p.data.map(e => ({
            id: e.id,
            subscriptions: e.subscriptions,
            status: e.connected == null ? '' : (e.connected ? 'topic-tree.online' : 'topic-tree.offline'),
            clientType: clientTypeTranslationMap.get(e.clientType as ClientType) ?? ''
          }))
        })));
      }
      case 'retained': {
        const query = new RetainedMessagesQuery(new TimePageLink(PREVIEW_SIZE, 0), { topicName: path, topicMatch: 'PREFIX' });
        return this.retained.getRetainedMessagesV2(query).pipe(map(p => ({
          total: p.totalElements,
          rows: p.data.map(m => ({ topic: m.topic, payload: m.payload, qos: m.qos }))
        })));
      }
      case 'dropped': {
        const now = Date.now();
        const pageLink = new TimePageLink(PREVIEW_SIZE, 0, null, { property: 'lastEventTime', direction: Direction.DESC }, now - DAY_MS, now);
        const query = new DroppedMessagesQuery(pageLink, { topicName: path, topicMatch: MatchMode.PREFIX });
        return this.dropped.getDroppedMessages(query).pipe(map(p => ({
          total: p.totalElements,
          rows: p.data.map(m => ({
            topic: m.topic,
            clientId: m.clientId,
            reason: droppedMsgReasonTranslationMap.get(m.reason) ?? m.reason ?? '',
            count: m.droppedCount
          }))
        })));
      }
    }
  }
}
