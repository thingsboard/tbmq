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

import { ChangeDetectionStrategy, Component, computed, inject, input, output, signal } from '@angular/core';
import { Router } from '@angular/router';
import { TranslateModule, TranslateService } from '@ngx-translate/core';
import { MatButton, MatIconButton } from '@angular/material/button';
import { MatIcon } from '@angular/material/icon';
import { MatTooltip } from '@angular/material/tooltip';
import {
  resourceUsageStatusStyle,
  resourceUsageStatusTranslationMap,
  serviceTypeMap
} from '@shared/models/resource-usage.model';
import { formatLargeNumber } from '@core/utils';
import { ChartKey } from '@shared/models/chart.model';
import { HOUR, MINUTE } from '@shared/models/time/time.models';
import {
  ClusterMessagesChartComponent,
  MessageChartSeries
} from '@home/pages/monitoring/cluster/cluster-messages-chart.component';
import {
  ClusterKafkaVm,
  ClusterNodeVm,
  ClusterTotalVm,
  compactBytes,
  compactNumber,
  ClusterRateKey,
  emptyMessageSeries,
  HISTORY_WINDOW_MS,
  HISTORY_WINDOWS,
  MessageSeries,
  UsageLevel,
  usageLevel
} from '@home/pages/monitoring/cluster/cluster.models';

type ChartUnit = 'messages' | 'bytes';

interface MeterVm {
  label: string;
  value: number | null;
  level: UsageLevel;
}

@Component({
    selector: 'tb-cluster-node-inspector',
    templateUrl: './cluster-node-inspector.component.html',
    styleUrls: ['./cluster-node-inspector.component.scss'],
    imports: [TranslateModule, MatButton, MatIconButton, MatIcon, MatTooltip, ClusterMessagesChartComponent],
    changeDetection: ChangeDetectionStrategy.OnPush
})
export class ClusterNodeInspectorComponent {

  readonly node = input<ClusterNodeVm | null>(null);
  readonly total = input.required<ClusterTotalVm>();
  readonly kafka = input<ClusterKafkaVm | null>(null);
  readonly series = input<MessageSeries>(emptyMessageSeries());

  readonly historyWindow = input(HISTORY_WINDOW_MS);

  readonly cleared = output<void>();
  readonly historyWindowChange = output<number>();

  readonly statusStyle = computed(() => resourceUsageStatusStyle.get(this.node()?.status));
  readonly statusLabel = computed(() => resourceUsageStatusTranslationMap.get(this.node()?.status));
  readonly serviceTypeLabel = computed(() => serviceTypeMap.get(this.node()?.serviceType));
  readonly meters = computed<MeterVm[]>(() => {
    const node = this.node();
    if (!node) {
      return [];
    }
    return [
      {label: 'monitoring.resource-usage.cpu-usage', value: node.cpu, level: usageLevel(node.cpu)},
      {label: 'monitoring.resource-usage.memory-usage', value: node.ram, level: usageLevel(node.ram)},
      {label: 'monitoring.resource-usage.disk-usage', value: node.disk, level: usageLevel(node.disk)}
    ];
  });
  readonly staleSeconds = computed(() => Math.round((Date.now() - (this.node()?.lastUpdateTime ?? Date.now())) / 1000));
  readonly showChart = computed(() => !this.kafka() && this.node()?.kind !== 'executor');
  readonly unit = signal<ChartUnit>('messages');
  readonly chartSeries = computed<MessageChartSeries[]>(() => {
    const {rates} = this.node() ?? this.total();
    const series = this.series();
    const perMinute = this.translate.instant('monitoring.cluster.per-min');
    const asMessages = (value: number) => `${compactNumber(value)} ${this.translate.instant('monitoring.cluster.msg-per-min')}`;
    const asBytes = (value: number) => `${compactBytes(value)}${perMinute}`;
    const build = (key: ClusterRateKey, label: string, format: (value: number) => string): MessageChartSeries =>
      ({key, label, points: series[key], current: rates[key], format});
    return this.unit() === 'messages'
      ? [
        build(ChartKey.incomingMsgs, 'monitoring.cluster.incoming-messages', asMessages),
        build(ChartKey.outgoingMsgs, 'monitoring.cluster.outgoing-messages', asMessages),
        build(ChartKey.droppedMsgs, 'monitoring.cluster.dropped-messages', asMessages)
      ]
      : [
        build(ChartKey.inboundPayloadTraffic, 'monitoring.cluster.inbound-traffic', asBytes),
        build(ChartKey.outboundPayloadTraffic, 'monitoring.cluster.outbound-traffic', asBytes)
      ];
  });

  readonly formatLargeNumber = formatLargeNumber;
  readonly historyWindows = HISTORY_WINDOWS;
  readonly compactNumber = compactNumber;

  private readonly router = inject(Router);
  private readonly translate = inject(TranslateService);

  windowLabel(windowMs: number): string {
    return windowMs < HOUR ? `${windowMs / MINUTE}m` : `${windowMs / HOUR}h`;
  }

  open(path: string) {
    this.router.navigate([path]);
  }

  openSessions(nodeId?: string) {
    this.router.navigate(['/sessions'], nodeId ? {queryParams: {nodeIdList: nodeId}} : undefined);
  }
}
