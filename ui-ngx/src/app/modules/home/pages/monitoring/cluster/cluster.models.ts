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

import { ResourceUsage, ResourceUsageStatus, ServiceType } from '@shared/models/resource-usage.model';
import { ClientSessionNodeStats, ClientSessionNodeStatsInfo, ClientSessionStatsInfo } from '@shared/models/session.model';
import { KafkaBroker, KafkaConsumerGroup } from '@shared/models/kafka.model';
import { PageData } from '@shared/models/page/page-data';
import { IntegrationInfo } from '@shared/models/integration.models';
import { ChartKey, TimeseriesData } from '@shared/models/chart.model';
import { MINUTE } from '@shared/models/time/time.models';
import { isDefinedAndNotNull } from '@core/utils';
import { compactNumber, LIVE_STATS_WINDOW_MS, LIVE_STATS_WINDOWS, RateSeries, scaledValue } from '@shared/models/live-stats.model';
import { defaultResourceUsageAlertSettings, ResourceUsageThresholds } from '@shared/models/settings.models';

export { compactBytes, compactNumber } from '@shared/models/live-stats.model';

export type UsageLevel = 'ok' | 'warn' | 'crit';
export type ClusterNodeKind = 'broker' | 'executor';

export const VIEWBOX_WIDTH = 1060;
export const BASE_HEIGHT = 476;
export const ROW_GAP = 118;
export const PAD_Y = 90;
export const COLUMN_X = {
  clients: 120,
  brokers: 430,
  kafka: 720,
  executors: 960
};
export const CLIENTS_RING_RADIUS = 34;
export const BROKER_RING_RADIUS = 34;
export const EXECUTOR_RING_RADIUS = 22;
export const EXECUTOR_DISC_RADIUS = 11;
export const KAFKA_RADIUS = 46;
export const EDGE_GAP = 4;
export const EXECUTOR_EDGE_WEIGHT = 0.3;
export const HISTORY_WINDOW_MS = LIVE_STATS_WINDOW_MS;
export const HISTORY_WINDOWS = LIVE_STATS_WINDOWS;
// Heartbeats arrive once per stats interval; two missed ones mean the node is gone even if its status still reads ACTIVE.
export const STALE_AFTER_INTERVALS = 2;
export const KAFKA_NODE_ID = 'kafka';
export const KAFKA_TOP_GROUPS = 5;
export const CLUSTER_RATE_KEYS = [
  ChartKey.incomingMsgs,
  ChartKey.outgoingMsgs,
  ChartKey.droppedMsgs,
  ChartKey.inboundPayloadTraffic,
  ChartKey.outboundPayloadTraffic
] as const;

export type ClusterRateKey = typeof CLUSTER_RATE_KEYS[number];
export const SESSIONS_KEY = ChartKey.sessions;
export const RESOURCE_KEYS = ['cpuUsage', 'memoryUsage', 'diskUsage'] as const;
export type ChartUnit = 'messages' | 'bytes' | 'sessions' | 'resources';
export type MessageRates = Record<ClusterRateKey, number>;
export type MessageSeries = RateSeries;

export interface ClusterNodeVm {
  id: string;
  kind: ClusterNodeKind;
  serviceType: ServiceType;
  status: ResourceUsageStatus;
  lastUpdateTime: number | null;
  stale: boolean;
  cpu: number | null;
  ram: number | null;
  disk: number | null;
  cpuLevel: UsageLevel;
  sessions: ClientSessionStatsInfo | null;
  integrations: ClusterIntegrationsVm | null;
  rates: MessageRates;
  hasDrops: boolean;
  x: number;
  y: number;
  ringR: number;
  discR: number;
  countLabel: string;
}

export interface ClusterIntegrationsVm {
  total: number;
  failed: number;
}

export interface ClusterEdgeVm {
  id: string;
  path: string;
  rel: number;
  rate: number | null;
  strokeWidth: number;
  opacity: number;
  particleSlots: number[];
  durSec: number;
}

export interface ClusterClientsVm {
  x: number;
  y: number;
  hasSessionStats: boolean;
  totalSessions: number;
  connectedSessions: number;
  deviceSessions: number;
  applicationSessions: number;
  integrationSessions: number;
}

export interface ClusterKafkaGroupVm {
  groupId: string;
  state: string;
  members: number;
  lag: number;
}

export interface ClusterKafkaVm {
  x: number;
  y: number;
  points: string;
  brokerCount: number | null;
  totalLag: number | null;
  brokers: KafkaBroker[];
  groupCount: number | null;
  topGroups: ClusterKafkaGroupVm[];
}

export interface ClusterTotalVm {
  brokerCount: number;
  executorCount: number;
  hasSessionStats: boolean;
  totalSessions: number;
  connectedSessions: number;
  rates: MessageRates;
  hasDrops: boolean;
}

export interface ClusterMapVm {
  width: number;
  height: number;
  clients: ClusterClientsVm;
  brokers: ClusterNodeVm[];
  executors: ClusterNodeVm[];
  kafka: ClusterKafkaVm;
  total: ClusterTotalVm;
  edges: ClusterEdgeVm[];
}

export interface ClusterRawData {
  services: PageData<ResourceUsage> | null;
  sessionsByNode: ClientSessionNodeStatsInfo | null;
  kafkaBrokers: PageData<KafkaBroker> | null;
  consumerGroups: PageData<KafkaConsumerGroup> | null;
  latestByBroker: Record<string, TimeseriesData | null>;
  latestTotal: TimeseriesData | null;
  integrations: PageData<IntegrationInfo> | null;
}

export const DEFAULT_THRESHOLDS: ResourceUsageThresholds = {
  highUsage: defaultResourceUsageAlertSettings.highUsage,
  criticalUsage: defaultResourceUsageAlertSettings.criticalUsage
};

export function usageLevel(percent: number | null, thresholds: ResourceUsageThresholds = DEFAULT_THRESHOLDS): UsageLevel {
  if (percent === null) {
    return 'ok';
  }
  if (percent > thresholds.criticalUsage) {
    return 'crit';
  }
  return percent > thresholds.highUsage ? 'warn' : 'ok';
}

export function discRadius(sessions: number, maxSessions: number): number {
  const normalized = maxSessions > 0 ? sessions / maxSessions : 0;
  return 13 + 11 * Math.sqrt(normalized);
}

export function cubicPath(x1: number, y1: number, x2: number, y2: number): string {
  const mx = (x1 + x2) / 2;
  return `M ${x1} ${y1} C ${mx} ${y1}, ${mx} ${y2}, ${x2} ${y2}`;
}

export function hexagonPoints(cx: number, cy: number, r: number): string {
  const points: string[] = [];
  for (let i = 0; i < 6; i++) {
    const angle = Math.PI / 180 * (60 * i - 90);
    points.push(`${(cx + r * Math.cos(angle)).toFixed(1)},${(cy + r * Math.sin(angle)).toFixed(1)}`);
  }
  return points.join(' ');
}

export function ringArcDash(ringR: number, percent: number | null): string {
  const circumference = 2 * Math.PI * ringR;
  const filled = circumference * Math.min(100, Math.max(0, percent ?? 0)) / 100;
  return `${filled.toFixed(1)} ${circumference.toFixed(1)}`;
}

export function zeroRates(): MessageRates {
  return byRateKey(() => 0);
}

function byRateKey<T>(build: (key: ClusterRateKey) => T): Record<ClusterRateKey, T> {
  return Object.fromEntries(CLUSTER_RATE_KEYS.map(key => [key, build(key)])) as Record<ClusterRateKey, T>;
}

export function emptyClusterVm(): ClusterMapVm {
  const centerY = BASE_HEIGHT / 2;
  return {
    width: VIEWBOX_WIDTH,
    height: BASE_HEIGHT,
    clients: {x: COLUMN_X.clients, y: centerY, hasSessionStats: false, totalSessions: 0, connectedSessions: 0, deviceSessions: 0, applicationSessions: 0, integrationSessions: 0},
    brokers: [],
    executors: [],
    kafka: {x: COLUMN_X.kafka, y: centerY, points: hexagonPoints(COLUMN_X.kafka, centerY, KAFKA_RADIUS), brokerCount: null, totalLag: null, brokers: [], groupCount: null, topGroups: []},
    total: {brokerCount: 0, executorCount: 0, hasSessionStats: false, totalSessions: 0, connectedSessions: 0, rates: zeroRates(), hasDrops: false},
    edges: []
  };
}

export function assembleClusterVm(raw: ClusterRawData, prev: ClusterMapVm, statsIntervalMinutes: number,
                                  thresholds: ResourceUsageThresholds = DEFAULT_THRESHOLDS): ClusterMapVm {
  const services = raw.services?.data ?? [];
  const brokerInfos = services.filter(s => s.serviceType === ServiceType.TBMQ).sort(byServiceId);
  const executorInfos = services.filter(s => s.serviceType === ServiceType.TBMQ_INTEGRATION_EXECUTOR).sort(byServiceId);

  const rows = Math.max(brokerInfos.length, executorInfos.length, 1);
  const height = Math.max(BASE_HEIGHT, 2 * PAD_Y + (rows - 1) * ROW_GAP);
  const centerY = height / 2;

  const rates = brokerInfos.map(info => messageRates(raw.latestByBroker[info.serviceId], statsIntervalMinutes));
  const sessions = brokerInfos.map(info => nodeSessions(raw.sessionsByNode, info.serviceId));
  const maxSessions = Math.max(0, ...sessions.map(s => s?.totalCount ?? 0));
  const maxIncoming = Math.max(0, ...rates.map(r => r[ChartKey.incomingMsgs]));
  const maxOutgoing = Math.max(0, ...rates.map(r => r[ChartKey.outgoingMsgs]));

  const brokers = brokerInfos.map((info, i) => buildNode(info, 'broker', {
    x: COLUMN_X.brokers,
    y: rowY(centerY, i, brokerInfos.length),
    ringR: BROKER_RING_RADIUS,
    discR: discRadius(sessions[i]?.totalCount ?? 0, maxSessions),
    sessions: sessions[i],
    integrations: null,
    rates: rates[i],
    statsIntervalMinutes,
    thresholds
  }));
  const activeExecutors = executorInfos.filter(info => info.status === ResourceUsageStatus.ACTIVE).length;
  const executors = executorInfos.map((info, i) => buildNode(info, 'executor', {
    x: COLUMN_X.executors,
    y: rowY(centerY, i, executorInfos.length),
    ringR: EXECUTOR_RING_RADIUS,
    discR: EXECUTOR_DISC_RADIUS,
    sessions: null,
    integrations: executorIntegrations(raw.integrations, info.serviceId, activeExecutors === 1 && info.status === ResourceUsageStatus.ACTIVE),
    rates: zeroRates(),
    statsIntervalMinutes,
    thresholds
  }));

  const edges: ClusterEdgeVm[] = [];
  for (const broker of brokers) {
    edges.push(buildEdge(`e-in-${broker.id}`,
      COLUMN_X.clients + CLIENTS_RING_RADIUS + EDGE_GAP, centerY,
      broker.x - broker.ringR - EDGE_GAP, broker.y,
      relativeTo(broker.rates[ChartKey.incomingMsgs], maxIncoming), broker.rates[ChartKey.incomingMsgs]));
    edges.push(buildEdge(`e-out-${broker.id}`,
      broker.x + broker.ringR + EDGE_GAP, broker.y,
      COLUMN_X.kafka - KAFKA_RADIUS - EDGE_GAP, centerY,
      relativeTo(broker.rates[ChartKey.outgoingMsgs], maxOutgoing), broker.rates[ChartKey.outgoingMsgs]));
  }
  for (const executor of executors) {
    edges.push(buildEdge(`e-ie-${executor.id}`,
      COLUMN_X.kafka + KAFKA_RADIUS + EDGE_GAP, centerY,
      executor.x - executor.ringR - EDGE_GAP, executor.y,
      EXECUTOR_EDGE_WEIGHT));
  }

  const nodeStats = Object.values(raw.sessionsByNode ?? {});
  const hasSessionStats = isDefinedAndNotNull(raw.sessionsByNode);
  const sumOf = (pick: (stats: ClientSessionNodeStats) => number) => nodeStats.reduce((sum, stats) => sum + (pick(stats) || 0), 0);
  const totalSessions = sumOf(stats => stats.totalCount);
  const connectedSessions = sumOf(stats => stats.connectedCount);
  const totalRates = messageRates(raw.latestTotal, statsIntervalMinutes);
  const groups = raw.consumerGroups?.data.map(group => ({
    groupId: group.groupId,
    state: group.state,
    members: group.members,
    lag: Number(group.lag) || 0
  })) ?? null;
  return {
    width: VIEWBOX_WIDTH,
    height,
    clients: {
      x: COLUMN_X.clients,
      y: centerY,
      hasSessionStats,
      totalSessions,
      connectedSessions,
      deviceSessions: sumOf(stats => stats.deviceCount),
      applicationSessions: sumOf(stats => stats.applicationCount),
      integrationSessions: sumOf(stats => stats.integrationCount)
    },
    brokers,
    executors,
    kafka: {
      x: COLUMN_X.kafka,
      y: centerY,
      points: hexagonPoints(COLUMN_X.kafka, centerY, KAFKA_RADIUS),
      brokerCount: raw.kafkaBrokers?.totalElements ?? prev.kafka.brokerCount,
      totalLag: groups ? groups.reduce((sum, group) => sum + group.lag, 0) : prev.kafka.totalLag,
      brokers: raw.kafkaBrokers?.data ?? prev.kafka.brokers,
      groupCount: raw.consumerGroups?.totalElements ?? prev.kafka.groupCount,
      topGroups: groups ? [...groups].sort((a, b) => b.lag - a.lag).slice(0, KAFKA_TOP_GROUPS) : prev.kafka.topGroups
    },
    total: {
      brokerCount: brokers.length,
      executorCount: executors.length,
      hasSessionStats,
      totalSessions,
      connectedSessions,
      rates: totalRates,
      hasDrops: totalRates[ChartKey.droppedMsgs] > 0
    },
    edges
  };
}

// A node absent from a successful by-node response simply has no sessions; null means the stats are unavailable.
function nodeSessions(byNode: ClientSessionNodeStatsInfo | null, serviceId: string): ClientSessionStatsInfo | null {
  if (!byNode) {
    return null;
  }
  return byNode[serviceId] ?? {connectedCount: 0, disconnectedCount: 0, totalCount: 0, deviceCount: 0, applicationCount: 0, integrationCount: 0};
}

// Integrations report the executor they run on in status.serviceId; those not started yet have no status.
// A failed status names the executor that reported it; a healthy status does not, so healthy integrations can only be
// attributed when a single active executor runs them all.
function executorIntegrations(integrations: PageData<IntegrationInfo> | null, serviceId: string, soleActiveExecutor: boolean): ClusterIntegrationsVm | null {
  if (!integrations) {
    return null;
  }
  const mine = integrations.data.filter(integration =>
    integration.status?.serviceId === serviceId || (soleActiveExecutor && integration.enabled && integration.status?.success));
  return {total: mine.length, failed: mine.filter(integration => integration.status?.success === false).length};
}

function byServiceId(a: ResourceUsage, b: ResourceUsage): number {
  return a.serviceId.localeCompare(b.serviceId);
}

function rowY(centerY: number, index: number, count: number): number {
  return centerY + (index - (count - 1) / 2) * ROW_GAP;
}

function relativeTo(value: number, max: number): number {
  return max > 0 ? value / max : 0;
}

function messageRates(data: TimeseriesData | null, statsIntervalMinutes: number): MessageRates {
  return byRateKey(key => scaledValue(data?.[key]?.[0]?.value, statsIntervalMinutes));
}

function percentOrNull(value: number | null | undefined): number | null {
  return isDefinedAndNotNull(value) ? Math.round(value) : null;
}

function buildNode(info: ResourceUsage, kind: ClusterNodeKind, layout: {
  x: number;
  y: number;
  ringR: number;
  discR: number;
  sessions: ClientSessionStatsInfo | null;
  integrations: ClusterIntegrationsVm | null;
  rates: MessageRates;
  statsIntervalMinutes: number;
  thresholds: ResourceUsageThresholds;
}): ClusterNodeVm {
  const cpu = percentOrNull(info.cpuUsage);
  const stale = info.status === ResourceUsageStatus.ACTIVE && !!info.lastUpdateTime
    && Date.now() - info.lastUpdateTime > STALE_AFTER_INTERVALS * layout.statsIntervalMinutes * MINUTE;
  return {
    id: info.serviceId,
    kind,
    serviceType: info.serviceType,
    status: info.status,
    lastUpdateTime: info.lastUpdateTime || null,
    stale,
    cpu,
    ram: percentOrNull(info.memoryUsage),
    disk: percentOrNull(info.diskUsage),
    cpuLevel: usageLevel(cpu, layout.thresholds),
    sessions: layout.sessions,
    integrations: layout.integrations,
    rates: layout.rates,
    hasDrops: layout.rates[ChartKey.droppedMsgs] > 0,
    x: layout.x,
    y: layout.y,
    ringR: layout.ringR,
    discR: layout.discR,
    countLabel: kind === 'broker' ? compactNumber(layout.sessions?.totalCount ?? null) : ''
  };
}

function buildEdge(id: string, x1: number, y1: number, x2: number, y2: number, rel: number, rate: number | null = null): ClusterEdgeVm {
  const particles = rel <= 0 ? 0 : 1 + Math.round(3 * rel);
  return {
    id,
    path: cubicPath(x1, y1, x2, y2),
    rel,
    rate,
    strokeWidth: 1.5 + 2.5 * rel,
    opacity: 0.25 + 0.45 * rel,
    particleSlots: Array.from({length: particles}, (_, i) => i),
    // Bucketed so a small rate change does not rewrite `dur`, which would restart the SMIL timeline.
    durSec: rel > 0.66 ? 2.4 : rel > 0.33 ? 3.6 : 5
  };
}
