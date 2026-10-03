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

import { ChangeDetectionStrategy, ChangeDetectorRef, Component, EventEmitter, Input, OnChanges, Output, inject } from '@angular/core';
import { Router } from '@angular/router';
import { DecimalPipe } from '@angular/common';
import { Clipboard } from '@angular/cdk/clipboard';
import { Observable, of } from 'rxjs';
import { map } from 'rxjs/operators';
import { MatButton, MatIconButton } from '@angular/material/button';
import { MatIcon } from '@angular/material/icon';
import { MatProgressBar } from '@angular/material/progress-bar';
import { MatTab, MatTabGroup } from '@angular/material/tabs';
import { MatTooltip } from '@angular/material/tooltip';
import { TranslateModule } from '@ngx-translate/core';
import { TopicTreeService } from '@core/http/topic-tree.service';
import { SubscriptionService } from '@core/http/subscription.service';
import { ClientSubscriptionsQuery } from '@shared/models/subscription.model';
import { PageLink } from '@shared/models/page/page-link';
import { TopicTreeEntityKind, TopicTreeNode } from '@shared/models/topic-tree.models';

export const TOPIC_TREE_TABS = ['subscriptions', 'clients', 'shared', 'integrations', 'retained', 'dropped'] as const;
export type TopicTreeTab = typeof TOPIC_TREE_TABS[number];

interface DetailsRow {
  primary: string;
  secondary?: string;
  value?: string | number;
}

const PREVIEW_SIZE = 20;

@Component({
  selector: 'tb-topic-tree-details',
  templateUrl: './topic-tree-details.component.html',
  styleUrls: ['./topic-tree-details.component.scss'],
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [DecimalPipe, MatButton, MatIconButton, MatIcon, MatProgressBar, MatTab, MatTabGroup, MatTooltip, TranslateModule]
})
export class TopicTreeDetailsComponent implements OnChanges {

  @Input() node: TopicTreeNode;
  @Input() tab: TopicTreeTab = 'subscriptions';
  @Output() closed = new EventEmitter<void>();

  readonly tabs = TOPIC_TREE_TABS;
  full: TopicTreeNode;
  rows: DetailsRow[] = [];
  total = 0;
  loading = false;

  private readonly topicTree = inject(TopicTreeService);
  private readonly subscriptions = inject(SubscriptionService);
  private readonly router = inject(Router);
  private readonly clipboard = inject(Clipboard);
  private readonly cd = inject(ChangeDetectorRef);

  get tabIndex(): number {
    return this.tabs.indexOf(this.tab);
  }

  ngOnChanges(): void {
    if (!this.node) {
      return;
    }
    this.full = null;
    this.topicTree.getNode(this.node.path, ['clients', 'online', 'shared', 'integrations']).subscribe(full => {
      this.full = full;
      this.cd.markForCheck();
    });
    this.loadTab();
  }

  selectTab(index: number): void {
    this.tab = this.tabs[index];
    this.loadTab();
  }

  copyPath(): void {
    this.clipboard.copy(this.node.path ?? '');
  }

  openTarget(): string {
    switch (this.tab) {
      case 'clients': return 'sessions';
      case 'shared': return 'shared';
      case 'integrations': return 'integrations';
      case 'retained': return 'retained';
      case 'dropped': return 'dropped';
      default: return 'subscriptions';
    }
  }

  openIn(tab: TopicTreeTab = this.tab): void {
    const path = this.node.path;
    switch (tab) {
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

  private loadTab(): void {
    this.loading = true;
    this.rows = [];
    this.cd.markForCheck();
    const tab = this.tab;
    this.fetch(tab).subscribe({
      next: ({ rows, total }) => {
        if (tab !== this.tab) {
          return;
        }
        this.rows = rows;
        this.total = total;
        this.loading = false;
        this.cd.markForCheck();
      },
      error: () => {
        this.loading = false;
        this.cd.markForCheck();
      }
    });
  }

  private fetch(tab: TopicTreeTab): Observable<{ rows: DetailsRow[]; total: number }> {
    const path = this.node.path;
    switch (tab) {
      case 'subscriptions': {
        const query = new ClientSubscriptionsQuery(new PageLink(PREVIEW_SIZE, 0), { topicFilter: path, topicMatch: 'PREFIX' });
        return this.subscriptions.getClientSubscriptionsV2(query).pipe(map(p => ({
          total: p.totalElements,
          rows: p.data.map(s => ({ primary: s.clientId, secondary: s.subscription.topicFilter, value: 'QoS ' + s.subscription.qos }))
        })));
      }
      case 'clients':
      case 'shared':
      case 'integrations': {
        const kind = tab.toUpperCase() as TopicTreeEntityKind;
        return this.topicTree.getEntities(path, kind, 0, PREVIEW_SIZE).pipe(map(p => ({
          total: p.totalElements,
          rows: p.data.map(e => ({
            primary: e.id,
            secondary: e.clientType ?? (e.connected == null ? undefined : (e.connected ? 'online' : 'offline')),
            value: e.subscriptions
          }))
        })));
      }
      case 'retained':
        return of({ total: this.node.retainedTotal, rows: [] });
      case 'dropped':
        return of({ total: this.node.dropped24h ?? 0, rows: [] });
    }
  }
}
