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

import { ChangeDetectionStrategy, Component, computed, inject, input, output } from '@angular/core';
import { NgTemplateOutlet } from '@angular/common';
import { TranslateModule, TranslateService } from '@ngx-translate/core';
import {
  ClusterMapVm,
  ClusterNodeVm,
  COLUMN_X,
  CLIENTS_RING_RADIUS,
  hexagonPoints,
  KAFKA_NODE_ID,
  KAFKA_RADIUS,
  ringArcDash
} from '@home/pages/monitoring/cluster/cluster.models';
import { compactNumber } from '@shared/models/live-stats.model';

interface ClientTypeVm {
  key: string;
  count: number | null;
  label: string;
}

@Component({
    selector: 'tb-cluster-map',
    templateUrl: './cluster-map.component.html',
    styleUrls: ['./cluster-map.component.scss'],
    imports: [NgTemplateOutlet, TranslateModule],
    changeDetection: ChangeDetectionStrategy.OnPush
})
export class ClusterMapComponent {

  readonly vm = input.required<ClusterMapVm>();
  readonly selectedId = input<string | null>(null);

  readonly nodeSelected = output<string>();

  readonly clientTypes = computed<ClientTypeVm[]>(() => {
    const {clients} = this.vm();
    const known = clients.hasSessionStats;
    return [
      {key: 'device', count: known ? clients.deviceSessions : null, label: 'monitoring.cluster.devices'},
      {key: 'application', count: known ? clients.applicationSessions : null, label: 'monitoring.cluster.applications'},
      {key: 'integration', count: known ? clients.integrationSessions : null, label: 'monitoring.cluster.integrations'}
    ];
  });
  readonly kafkaHaloPoints = computed(() => {
    const {kafka} = this.vm();
    return hexagonPoints(kafka.x, kafka.y, KAFKA_RADIUS + 7);
  });

  readonly columnX = COLUMN_X;
  readonly kafkaNodeId = KAFKA_NODE_ID;
  readonly clientsRingR = CLIENTS_RING_RADIUS;
  readonly compactNumber = compactNumber;
  readonly ringArcDash = ringArcDash;

  private readonly translate = inject(TranslateService);

  // Says what the glyph shows: sessions only for brokers, CPU only once reported, and the alert badge.
  nodeAriaLabel(node: ClusterNodeVm): string {
    const parts = [node.id, this.translate.instant(node.kind === 'broker' ? 'monitoring.cluster.kind-broker' : 'monitoring.cluster.kind-executor')];
    if (node.kind === 'broker' && node.sessions) {
      parts.push(this.translate.instant('monitoring.cluster.node-sessions', {count: node.sessions.totalCount}));
    }
    if (node.stale) {
      parts.push(this.translate.instant('monitoring.cluster.stale'));
    } else if (node.cpu !== null) {
      parts.push(this.translate.instant('monitoring.cluster.cpu-percent', {value: node.cpu}));
    }
    if (node.alertCount) {
      parts.push(this.translate.instant('monitoring.cluster.alert-count', {count: node.alertCount}));
    }
    return parts.join(', ');
  }

  onSelect(node: ClusterNodeVm, event?: Event) {
    event?.preventDefault();
    this.nodeSelected.emit(node.key);
  }

  onSelectKafka(event?: Event) {
    event?.preventDefault();
    this.nodeSelected.emit(KAFKA_NODE_ID);
  }

  particleBegin(slot: number, slots: number, durSec: number): string {
    return `-${(slot * durSec / slots).toFixed(2)}s`;
  }
}
