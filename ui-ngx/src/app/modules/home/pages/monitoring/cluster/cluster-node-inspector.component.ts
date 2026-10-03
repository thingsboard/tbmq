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
import { RateChartComponent, RateChartSeries } from '@shared/components/chart/rate-chart.component';
import { RateSeries } from '@shared/models/live-stats.model';
import { ResourceUsageThresholds } from '@shared/models/settings.models';
import {
  ChartUnit,
  ClusterKafkaVm,
  ClusterNodeVm,
  ClusterTotalVm,
  compactBytes,
  compactNumber,
  ClusterRateKey,
  DEFAULT_THRESHOLDS,
  HISTORY_WINDOW_MS,
  HISTORY_WINDOWS,
  RESOURCE_KEYS,
  SESSIONS_KEY,
  UsageLevel,
  usageLevel
} from '@home/pages/monitoring/cluster/cluster.models';

interface MeterVm {
  label: string;
  value: number | null;
  level: UsageLevel;
}

@Component({
    selector: 'tb-cluster-node-inspector',
    templateUrl: './cluster-node-inspector.component.html',
    styleUrls: ['./cluster-node-inspector.component.scss'],
    imports: [TranslateModule, MatButton, MatIconButton, MatIcon, MatTooltip, RateChartComponent],
    changeDetection: ChangeDetectionStrategy.OnPush
})
export class ClusterNodeInspectorComponent {

  readonly node = input<ClusterNodeVm | null>(null);
  readonly total = input.required<ClusterTotalVm>();
  readonly kafka = input<ClusterKafkaVm | null>(null);
  readonly series = input<RateSeries>({});

  readonly historyWindow = input(HISTORY_WINDOW_MS);
  readonly thresholds = input<ResourceUsageThresholds>(DEFAULT_THRESHOLDS);

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
      {label: 'monitoring.resource-usage.cpu-usage', value: node.cpu, level: usageLevel(node.cpu, this.thresholds())},
      {label: 'monitoring.resource-usage.memory-usage', value: node.ram, level: usageLevel(node.ram, this.thresholds())},
      {label: 'monitoring.resource-usage.disk-usage', value: node.disk, level: usageLevel(node.disk, this.thresholds())}
    ];
  });
  readonly staleSeconds = computed(() => Math.round((Date.now() - (this.node()?.lastUpdateTime ?? Date.now())) / 1000));
  readonly showChart = computed(() => !this.kafka() && this.node()?.kind !== 'executor');
  readonly unit = signal<ChartUnit>('messages');
  // Resource history exists per node only; the cluster total and Kafka have no CPU, memory or disk series.
  readonly unitOptions = computed<ChartUnit[]>(() => this.node() ? ['messages', 'bytes', 'sessions', 'resources'] : ['messages', 'bytes', 'sessions']);
  readonly activeUnit = computed<ChartUnit>(() => this.unitOptions().includes(this.unit()) ? this.unit() : 'messages');
  readonly chartSeries = computed<RateChartSeries[]>(() => {
    const {rates} = this.node() ?? this.total();
    const series = this.series();
    const perMinute = this.translate.instant('monitoring.cluster.per-min');
    const asMessages = (value: number) => `${compactNumber(value)} ${this.translate.instant('monitoring.cluster.msg-per-min')}`;
    const asBytes = (value: number) => `${compactBytes(value)}${perMinute}`;
    const asPercent = (value: number) => `${Math.round(value)}%`;
    const last = (key: string) => series[key]?.[series[key].length - 1]?.value ?? 0;
    const build = (key: string, label: string, current: number, format: (value: number) => string): RateChartSeries =>
      ({key, label, points: series[key] ?? [], current, format});
    switch (this.activeUnit()) {
      case 'bytes':
        return [
          build(ChartKey.inboundPayloadTraffic, 'monitoring.cluster.inbound-traffic', rates[ChartKey.inboundPayloadTraffic], asBytes),
          build(ChartKey.outboundPayloadTraffic, 'monitoring.cluster.outbound-traffic', rates[ChartKey.outboundPayloadTraffic], asBytes)
        ];
      case 'sessions':
        return [build(SESSIONS_KEY, 'monitoring.chart.sessions', last(SESSIONS_KEY), compactNumber)];
      case 'resources':
        return [
          build(RESOURCE_KEYS[0], 'monitoring.resource-usage.cpu-usage', this.node()?.cpu ?? 0, asPercent),
          build(RESOURCE_KEYS[1], 'monitoring.resource-usage.memory-usage', this.node()?.ram ?? 0, asPercent),
          build(RESOURCE_KEYS[2], 'monitoring.resource-usage.disk-usage', this.node()?.disk ?? 0, asPercent)
        ];
      default:
        return [
          build(ChartKey.incomingMsgs, 'monitoring.cluster.incoming-messages', rates[ChartKey.incomingMsgs], asMessages),
          build(ChartKey.outgoingMsgs, 'monitoring.cluster.outgoing-messages', rates[ChartKey.outgoingMsgs], asMessages),
          build(ChartKey.droppedMsgs, 'monitoring.cluster.dropped-messages', rates[ChartKey.droppedMsgs], asMessages)
        ];
    }
  });

  readonly formatLargeNumber = formatLargeNumber;
  readonly historyWindows = HISTORY_WINDOWS;
  readonly unitLabels: Record<ChartUnit, string> = {
    messages: 'monitoring.cluster.messages',
    bytes: 'monitoring.cluster.bytes',
    sessions: 'monitoring.chart.sessions',
    resources: 'monitoring.cluster.resources'
  };
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
