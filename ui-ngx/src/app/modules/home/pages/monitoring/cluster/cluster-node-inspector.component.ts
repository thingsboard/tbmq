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

import { ChangeDetectionStrategy, Component, computed, inject, input, output, signal } from '@angular/core';
import { NgTemplateOutlet } from '@angular/common';
import { RouterLink } from '@angular/router';
import { FormsModule } from '@angular/forms';
import { TranslateModule, TranslateService } from '@ngx-translate/core';
import { currentLangSignal } from '@core/translate/current-lang-signal';
import { TruncateWithTooltipDirective } from '@shared/directives/truncate-with-tooltip.directive';
import { MatAnchor } from '@angular/material/button';
import { MatIcon } from '@angular/material/icon';
import { MatTooltip } from '@angular/material/tooltip';
import {
  ResourceUsageStatus,
  resourceUsageStatusStyle,
  resourceUsageStatusTranslationMap,
  resourceUsageTooltipTranslationMap,
  UsageLevel,
  usageLevel
} from '@shared/models/resource-usage.model';
import { formatLargeNumber } from '@core/utils';
import { ChartDataKeyTranslationMap, ChartKey } from '@shared/models/chart.model';
import { HOUR, MINUTE } from '@shared/models/time/time.models';
import { RateChartComponent, RateChartSeries } from '@shared/components/chart/rate-chart.component';
import { ToggleSelectComponent } from '@shared/components/toggle-select.component';
import { ToggleHeaderOption } from '@shared/components/toggle-header.component';
import { compactBytes, compactNumber, RateSeries } from '@shared/models/live-stats.model';
import { defaultResourceUsageAlertSettings, ResourceUsageThresholds } from '@shared/models/settings.models';
import {
  ChartUnit,
  ClusterAlertVm,
  ClusterKafkaVm,
  ClusterNodeVm,
  ClusterTotalVm,
  HISTORY_WINDOW_MS,
  HISTORY_WINDOWS,
  RESOURCE_KEYS,
  secondsSince,
  SESSIONS_KEY
} from '@home/pages/monitoring/cluster/cluster.models';

interface MeterVm {
  label: string;
  value: number | null;
  level: UsageLevel;
}

// One params object serves the compact button label ("15m") and the spoken window of the chart ("15 minutes").
interface HistoryWindowOption {
  windowMs: number;
  shortKey: string;
  longKey: string;
  params: Record<string, number>;
}

// Alerts are sorted critical first, so the cut drops the least severe ones.
const MAX_LISTED_ALERTS = 50;

const historyWindowOption = (windowMs: number): HistoryWindowOption => windowMs < HOUR
  ? {windowMs, shortKey: 'monitoring.cluster.window-minutes', longKey: 'timewindow.minutes', params: {minutes: windowMs / MINUTE}}
  : {windowMs, shortKey: 'monitoring.cluster.window-hours', longKey: 'timewindow.hours', params: {hours: windowMs / HOUR}};

const UNIT_LABELS: Record<ChartUnit, string> = {
  messages: 'monitoring.cluster.messages',
  bytes: 'monitoring.cluster.bytes',
  sessions: 'monitoring.chart.sessions',
  resources: 'monitoring.cluster.resources'
};

@Component({
    selector: 'tb-cluster-node-inspector',
    templateUrl: './cluster-node-inspector.component.html',
    styleUrls: ['./cluster-node-inspector.component.scss'],
    imports: [NgTemplateOutlet, RouterLink, FormsModule, TranslateModule, MatAnchor, MatIcon, MatTooltip,
      RateChartComponent, ToggleSelectComponent, TruncateWithTooltipDirective],
    changeDetection: ChangeDetectionStrategy.OnPush
})
export class ClusterNodeInspectorComponent {

  private readonly translate = inject(TranslateService);
  private readonly lang = currentLangSignal(this.translate);

  readonly node = input<ClusterNodeVm | null>(null);
  readonly total = input.required<ClusterTotalVm>();
  readonly kafka = input<ClusterKafkaVm | null>(null);
  readonly series = input<RateSeries>({});
  readonly alerts = input<ClusterAlertVm[]>([]);
  readonly now = input(Date.now());

  readonly historyWindow = input(HISTORY_WINDOW_MS);
  readonly thresholds = input<ResourceUsageThresholds>(defaultResourceUsageAlertSettings);

  readonly historyWindowChange = output<number>();
  readonly alertNodeSelected = output<string>();

  private readonly scopedAlerts = computed(() => {
    const node = this.node();
    return node ? this.alerts().filter(alert => alert.nodeKey === node.key) : this.alerts();
  });
  readonly visibleAlerts = computed(() => this.scopedAlerts().slice(0, MAX_LISTED_ALERTS));
  readonly unlistedAlertCount = computed(() => Math.max(0, this.scopedAlerts().length - MAX_LISTED_ALERTS));

  readonly statusStyle = computed(() => resourceUsageStatusStyle.get(this.node()?.status));
  readonly statusLabel = computed(() => resourceUsageStatusTranslationMap.get(this.node()?.status));
  readonly statusHint = computed(() => resourceUsageTooltipTranslationMap.get(this.node()?.status));
  readonly activeStatus = ResourceUsageStatus.ACTIVE;
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
  readonly staleSeconds = computed(() => secondsSince(this.node()?.lastUpdateTime ?? null, this.now()));
  readonly showChart = computed(() => !this.kafka() && this.node()?.kind !== 'executor');
  readonly unit = signal<ChartUnit>('messages');
  // Resource history exists per node only; the cluster total and Kafka have no CPU, memory or disk series.
  readonly unitOptions = computed<ToggleHeaderOption[]>(() => (this.node() ? ['messages', 'bytes', 'sessions', 'resources'] : ['messages', 'bytes', 'sessions'])
    .map(unit => ({name: this.instant(UNIT_LABELS[unit]), value: unit})));
  readonly activeUnit = computed<ChartUnit>(() => this.unitOptions().some(option => option.value === this.unit()) ? this.unit() : 'messages');
  // Every legend value is the newest point of its series, so the legend always agrees with the line next to it.
  readonly chartSeries = computed<RateChartSeries[]>(() => {
    const series = this.series();
    const perMinute = this.instant('rate-chart.per-min');
    const asMessages = (value: number) => `${compactNumber(value)} ${this.instant('rate-chart.msg-per-min')}`;
    const asBytes = (value: number) => `${compactBytes(value)}${perMinute}`;
    const asPercent = (value: number) => `${Math.round(value)}%`;
    const build = (key: string, label: string, format: (value: number) => string, color?: string): RateChartSeries => {
      const points = series[key] ?? [];
      return {key, label, points, current: points[points.length - 1]?.value ?? 0, format, color};
    };
    const chartLabel = (key: ChartKey) => ChartDataKeyTranslationMap.get(key);
    switch (this.activeUnit()) {
      case 'bytes':
        return [
          build(ChartKey.inboundPayloadTraffic, chartLabel(ChartKey.inboundPayloadTraffic), asBytes),
          build(ChartKey.outboundPayloadTraffic, chartLabel(ChartKey.outboundPayloadTraffic), asBytes)
        ];
      case 'sessions':
        return [build(SESSIONS_KEY, chartLabel(ChartKey.sessions), compactNumber)];
      case 'resources':
        return [
          build(RESOURCE_KEYS[0], 'monitoring.resource-usage.cpu-usage', asPercent),
          build(RESOURCE_KEYS[1], 'monitoring.resource-usage.memory-usage', asPercent),
          build(RESOURCE_KEYS[2], 'monitoring.resource-usage.disk-usage', asPercent)
        ];
      default:
        return [
          build(ChartKey.incomingMsgs, chartLabel(ChartKey.incomingMsgs), asMessages),
          build(ChartKey.outgoingMsgs, chartLabel(ChartKey.outgoingMsgs), asMessages),
          build(ChartKey.droppedMsgs, chartLabel(ChartKey.droppedMsgs), asMessages, 'var(--tb-usage-warn)')
        ];
    }
  });

  readonly historyWindows = HISTORY_WINDOWS.map(historyWindowOption);
  readonly historyWindowOptions = computed<ToggleHeaderOption[]>(() => this.historyWindows
    .map(option => ({name: this.instant(option.shortKey, option.params), value: option.windowMs})));
  readonly chartWindowLabel = computed(() => {
    const option = historyWindowOption(this.historyWindow());
    return this.instant(option.longKey, option.params).trim();
  });

  readonly formatLargeNumber = formatLargeNumber;
  readonly compactNumber = compactNumber;

  // Reading the language signal makes computed() translations follow a runtime language switch.
  private instant(key: string, params?: object): string {
    this.lang();
    return this.translate.instant(key, params);
  }
}
