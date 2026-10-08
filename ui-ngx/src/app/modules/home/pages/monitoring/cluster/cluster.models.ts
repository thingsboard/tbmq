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

import { ResourceUsage, ResourceUsageStatus, ServiceType, UsageLevel, usageLevel } from '@shared/models/resource-usage.model';
import { ClientSessionNodeStats, ClientSessionNodeStatsInfo, ClientSessionStatsInfo } from '@shared/models/session.model';
import { KafkaBroker, KafkaConsumerGroup } from '@shared/models/kafka.model';
import { PageData } from '@shared/models/page/page-data';
import { IntegrationInfo } from '@shared/models/integration.models';
import { ChartKey, TimeseriesData, TOTAL_ENTITY_ID } from '@shared/models/chart.model';
import { isDefinedAndNotNull } from '@core/utils';
import { compactNumber, LIVE_STATS_WINDOW_MS, LIVE_STATS_WINDOWS, scaledValue } from '@shared/models/live-stats.model';
import { ResourceUsageThresholds } from '@shared/models/settings.models';

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
// A broker disc grows with the square root of its share of the busiest broker's sessions, so area tracks the count.
const DISC_MIN_RADIUS = 13;
const DISC_RADIUS_GROWTH = 11;
// Edge styling scales with the edge's rate relative to the busiest edge of the same direction (0..1).
const EDGE_MIN_WIDTH = 1.5;
const EDGE_WIDTH_GROWTH = 1;
const EDGE_MIN_OPACITY = 0.3;
const EDGE_OPACITY_GROWTH = 0.6;
const EDGE_MAX_EXTRA_PARTICLES = 3;
// Bucketed so a small rate change does not rewrite `dur`, which would restart the SMIL timeline.
const PARTICLE_DURATION_BUCKETS = [{minRelative: 0.66, durSec: 2.4}, {minRelative: 0.33, durSec: 3.6}];
const PARTICLE_IDLE_DURATION_SEC = 5;
export const HISTORY_WINDOW_MS = LIVE_STATS_WINDOW_MS;
export const HISTORY_WINDOWS = LIVE_STATS_WINDOWS;
// Nodes report system info once per heartbeat; two missed ones mean the node is gone even if its status still reads ACTIVE.
export const STALE_AFTER_HEARTBEATS = 2;
export const KAFKA_NODE_ID = 'kafka';
export const KAFKA_TOP_GROUPS = 5;

export function nodeKey(kind: ClusterNodeKind, serviceId: string): string {
  return kind === 'broker' ? serviceId : `${kind}:${serviceId}`;
}

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
export const GAUGE_KEYS: readonly string[] = [SESSIONS_KEY, ...RESOURCE_KEYS];
// Everything the inspector charts for a broker or the cluster total; the total simply has no resource series.
export const NODE_SERIES_KEYS: readonly string[] = [...CLUSTER_RATE_KEYS, ...GAUGE_KEYS];
export type ChartUnit = 'messages' | 'bytes' | 'sessions' | 'resources';
export type MessageRates = Record<ClusterRateKey, number>;

export interface ClusterNodeVm {
  id: string;
  // Selection key: a broker and an executor may share a service id.
  key: string;
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
  alertLevel: ClusterAlertLevel | null;
  alertCount: number;
  x: number;
  y: number;
  ringR: number;
  discR: number;
  countLabel: string;
}

export type ClusterAlertLevel = 'warning' | 'critical';

// Derived on every refresh from data the map already polls; nothing is persisted or acknowledged.
export interface ClusterAlertVm {
  id: string;
  level: ClusterAlertLevel;
  // Selection key of the node the alert belongs to; null when no node on the map owns it.
  nodeKey: string | null;
  nodeId: string | null;
  messageKey: string;
  params: Record<string, string | number>;
  detail?: string;
}

export interface ClusterIntegrationsVm {
  total: number;
  failed: number;
}

export interface ClusterEdgeVm {
  id: string;
  path: string;
  rate: number | null;
  strokeWidth: number;
  opacity: number;
  // The node at the far end is not active: drawn dotted, without particles.
  offline: boolean;
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
  // Inactive brokers and executors left off the map by the "hide inactive" toggle.
  hiddenCount: number;
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
  // Critical first.
  alerts: ClusterAlertVm[];
}

// Each source is refreshed on its own cadence; null means it has not loaded yet.
export interface ClusterRawData {
  services: PageData<ResourceUsage> | null;
  sessionsByNode: ClientSessionNodeStatsInfo | null;
  // Latest timeseries keyed by entity id: every broker plus the cluster total.
  latest: Record<string, TimeseriesData> | null;
  kafkaBrokers: PageData<KafkaBroker> | null;
  consumerGroups: PageData<KafkaConsumerGroup> | null;
  integrations: PageData<IntegrationInfo> | null;
}

export interface ClusterCadence {
  statsIntervalMinutes: number;
  heartbeatMs: number;
}

export function emptyClusterRawData(): ClusterRawData {
  return {services: null, sessionsByNode: null, latest: null, kafkaBrokers: null, consumerGroups: null, integrations: null};
}

export function discRadius(sessions: number, maxSessions: number): number {
  const normalized = maxSessions > 0 ? sessions / maxSessions : 0;
  return DISC_MIN_RADIUS + DISC_RADIUS_GROWTH * Math.sqrt(normalized);
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

export function assembleClusterVm(raw: ClusterRawData, cadence: ClusterCadence, thresholds: ResourceUsageThresholds,
                                  hideInactive = false): ClusterMapVm {
  const services = raw.services?.data ?? [];
  const allBrokerInfos = services.filter(s => s.serviceType === ServiceType.TBMQ).sort(byServiceId);
  const allExecutorInfos = services.filter(s => s.serviceType === ServiceType.TBMQ_INTEGRATION_EXECUTOR).sort(byServiceId);
  const visible = (info: ResourceUsage) => !hideInactive || !isServiceInactive(info, cadence);
  const brokerInfos = allBrokerInfos.filter(visible);
  const executorInfos = allExecutorInfos.filter(visible);

  const rows = Math.max(brokerInfos.length, executorInfos.length, 1);
  const height = Math.max(BASE_HEIGHT, 2 * PAD_Y + (rows - 1) * ROW_GAP);
  const centerY = height / 2;

  const plainBrokers = buildBrokers(brokerInfos, raw, centerY, cadence, thresholds);
  const plainExecutors = buildExecutors(executorInfos, raw.integrations, centerY, cadence, thresholds);
  const alerts = buildAlerts([...plainBrokers, ...plainExecutors], raw.integrations, thresholds);
  const brokers = plainBrokers.map(node => withAlerts(node, alerts));
  const executors = plainExecutors.map(node => withAlerts(node, alerts));
  const clients = buildClients(raw.sessionsByNode, centerY);
  const totalRates = messageRates(raw.latest?.[TOTAL_ENTITY_ID], cadence.statsIntervalMinutes);
  return {
    width: VIEWBOX_WIDTH,
    height,
    clients,
    brokers,
    executors,
    kafka: buildKafka(raw, centerY),
    total: {
      brokerCount: allBrokerInfos.length,
      executorCount: allExecutorInfos.length,
      hiddenCount: allBrokerInfos.length + allExecutorInfos.length - brokers.length - executors.length,
      hasSessionStats: clients.hasSessionStats,
      totalSessions: clients.totalSessions,
      connectedSessions: clients.connectedSessions,
      rates: totalRates,
      hasDrops: totalRates[ChartKey.droppedMsgs] > 0
    },
    edges: buildEdges(brokers, executors, centerY),
    alerts
  };
}

const USAGE_ALERTS: ReadonlyArray<{metric: 'cpu' | 'ram' | 'disk'; messageKey: string}> = [
  {metric: 'cpu', messageKey: 'monitoring.cluster.alert-cpu'},
  {metric: 'ram', messageKey: 'monitoring.cluster.alert-ram'},
  {metric: 'disk', messageKey: 'monitoring.cluster.alert-disk'}
];
const ALERT_DETAIL_MAX_LENGTH = 200;

function buildAlerts(nodes: ClusterNodeVm[], integrations: PageData<IntegrationInfo> | null,
                     thresholds: ResourceUsageThresholds): ClusterAlertVm[] {
  const alerts: ClusterAlertVm[] = [];
  const add = (node: ClusterNodeVm | null, level: ClusterAlertLevel, kind: string, messageKey: string,
               params: Record<string, string | number> = {}, detail?: string) =>
    alerts.push({id: `${node?.key ?? 'cluster'}:${kind}`, level, nodeKey: node?.key ?? null, nodeId: node?.id ?? null, messageKey, params, detail});

  for (const node of nodes) {
    // Outdated services have not reported for over a week: decommissioned, not an incident.
    if (node.status === ResourceUsageStatus.OUTDATED) {
      continue;
    }
    if (node.status === ResourceUsageStatus.INACTIVE) {
      add(node, 'warning', 'down', 'monitoring.cluster.alert-inactive');
      continue;
    }
    if (node.stale) {
      add(node, 'critical', 'down', 'monitoring.cluster.alert-no-heartbeat',
        {seconds: Math.round((Date.now() - (node.lastUpdateTime ?? Date.now())) / 1000)});
      continue;
    }
    for (const {metric, messageKey} of USAGE_ALERTS) {
      const level = usageLevel(node[metric], thresholds);
      if (level !== 'ok') {
        add(node, level === 'crit' ? 'critical' : 'warning', metric, messageKey, {value: node[metric]});
      }
    }
    const dropped = node.rates[ChartKey.droppedMsgs];
    if (dropped > 0) {
      add(node, 'warning', 'drops', 'monitoring.cluster.alert-drops', {rate: compactNumber(dropped)});
    }
  }

  for (const integration of integrations?.data ?? []) {
    if (integration.enabled && integration.status?.success === false) {
      const executor = nodes.find(node => node.kind === 'executor' && node.id === integration.status.serviceId) ?? null;
      add(executor, 'critical', `integration-${integration.id}`, 'monitoring.cluster.alert-integration',
        {name: integration.name}, alertDetail(integration.status.error));
    }
  }
  return alerts.sort((a, b) => (a.level === b.level ? 0 : a.level === 'critical' ? -1 : 1)
    || (a.nodeId ?? '').localeCompare(b.nodeId ?? ''));
}

// Integration errors arrive as full stack traces; the first line names the cause.
function alertDetail(error: unknown): string | undefined {
  if (!isDefinedAndNotNull(error)) {
    return undefined;
  }
  const firstLine = (typeof error === 'string' ? error : JSON.stringify(error)).split('\n')[0].trim();
  return firstLine.length > ALERT_DETAIL_MAX_LENGTH ? `${firstLine.slice(0, ALERT_DETAIL_MAX_LENGTH)}…` : firstLine;
}

function withAlerts(node: ClusterNodeVm, alerts: ClusterAlertVm[]): ClusterNodeVm {
  const mine = alerts.filter(alert => alert.nodeKey === node.key);
  if (!mine.length) {
    return node;
  }
  return {...node, alertCount: mine.length, alertLevel: mine.some(alert => alert.level === 'critical') ? 'critical' : 'warning'};
}

function buildBrokers(infos: ResourceUsage[], raw: ClusterRawData, centerY: number, cadence: ClusterCadence,
                      thresholds: ResourceUsageThresholds): ClusterNodeVm[] {
  const sessions = infos.map(info => nodeSessions(raw.sessionsByNode, info.serviceId));
  const maxSessions = Math.max(0, ...sessions.map(s => s?.totalCount ?? 0));
  return infos.map((info, i) => buildNode(info, 'broker', {
    x: COLUMN_X.brokers,
    y: rowY(centerY, i, infos.length),
    ringR: BROKER_RING_RADIUS,
    discR: discRadius(sessions[i]?.totalCount ?? 0, maxSessions),
    sessions: sessions[i],
    integrations: null,
    rates: messageRates(raw.latest?.[info.serviceId], cadence.statsIntervalMinutes)
  }, cadence, thresholds));
}

function buildExecutors(infos: ResourceUsage[], integrations: PageData<IntegrationInfo> | null, centerY: number,
                        cadence: ClusterCadence, thresholds: ResourceUsageThresholds): ClusterNodeVm[] {
  const activeExecutors = infos.filter(info => info.status === ResourceUsageStatus.ACTIVE).length;
  return infos.map((info, i) => buildNode(info, 'executor', {
    x: COLUMN_X.executors,
    y: rowY(centerY, i, infos.length),
    ringR: EXECUTOR_RING_RADIUS,
    discR: EXECUTOR_DISC_RADIUS,
    sessions: null,
    integrations: executorIntegrations(integrations, info.serviceId, activeExecutors === 1 && info.status === ResourceUsageStatus.ACTIVE),
    rates: zeroRates()
  }, cadence, thresholds));
}

function buildClients(sessionsByNode: ClientSessionNodeStatsInfo | null, centerY: number): ClusterClientsVm {
  const nodeStats = Object.values(sessionsByNode ?? {});
  const sumOf = (pick: (stats: ClientSessionNodeStats) => number) => nodeStats.reduce((sum, stats) => sum + (pick(stats) || 0), 0);
  return {
    x: COLUMN_X.clients,
    y: centerY,
    hasSessionStats: isDefinedAndNotNull(sessionsByNode),
    totalSessions: sumOf(stats => stats.totalCount),
    connectedSessions: sumOf(stats => stats.connectedCount),
    deviceSessions: sumOf(stats => stats.deviceCount),
    applicationSessions: sumOf(stats => stats.applicationCount),
    integrationSessions: sumOf(stats => stats.integrationCount)
  };
}

function buildKafka(raw: ClusterRawData, centerY: number): ClusterKafkaVm {
  const groups = raw.consumerGroups?.data.map(group => ({groupId: group.groupId, lag: Number(group.lag) || 0})) ?? null;
  return {
    x: COLUMN_X.kafka,
    y: centerY,
    points: hexagonPoints(COLUMN_X.kafka, centerY, KAFKA_RADIUS),
    brokerCount: raw.kafkaBrokers?.totalElements ?? null,
    totalLag: groups ? groups.reduce((sum, group) => sum + group.lag, 0) : null,
    brokers: raw.kafkaBrokers?.data ?? [],
    groupCount: raw.consumerGroups?.totalElements ?? null,
    topGroups: groups ? [...groups].sort((a, b) => b.lag - a.lag).slice(0, KAFKA_TOP_GROUPS) : []
  };
}

function buildEdges(brokers: ClusterNodeVm[], executors: ClusterNodeVm[], centerY: number): ClusterEdgeVm[] {
  const maxIncoming = Math.max(0, ...brokers.map(broker => broker.rates[ChartKey.incomingMsgs]));
  const maxOutgoing = Math.max(0, ...brokers.map(broker => broker.rates[ChartKey.outgoingMsgs]));
  const edges: ClusterEdgeVm[] = [];
  for (const broker of brokers) {
    const incoming = broker.rates[ChartKey.incomingMsgs];
    const outgoing = broker.rates[ChartKey.outgoingMsgs];
    edges.push(buildEdge(`e-in-${broker.id}`,
      COLUMN_X.clients + CLIENTS_RING_RADIUS + EDGE_GAP, centerY,
      broker.x - broker.ringR - EDGE_GAP, broker.y,
      relativeTo(incoming, maxIncoming), incoming, isOffline(broker)));
    edges.push(buildEdge(`e-out-${broker.id}`,
      broker.x + broker.ringR + EDGE_GAP, broker.y,
      COLUMN_X.kafka - KAFKA_RADIUS - EDGE_GAP, centerY,
      relativeTo(outgoing, maxOutgoing), outgoing, isOffline(broker)));
  }
  for (const executor of executors) {
    edges.push(buildEdge(`e-ie-${executor.id}`,
      COLUMN_X.kafka + KAFKA_RADIUS + EDGE_GAP, centerY,
      executor.x - executor.ringR - EDGE_GAP, executor.y,
      EXECUTOR_EDGE_WEIGHT, null, isOffline(executor)));
  }
  return edges;
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

function messageRates(data: TimeseriesData | null | undefined, statsIntervalMinutes: number): MessageRates {
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
}, cadence: ClusterCadence, thresholds: ResourceUsageThresholds): ClusterNodeVm {
  const cpu = percentOrNull(info.cpuUsage);
  const stale = isStale(info, cadence);
  return {
    id: info.serviceId,
    key: nodeKey(kind, info.serviceId),
    kind,
    serviceType: info.serviceType,
    status: info.status,
    lastUpdateTime: info.lastUpdateTime || null,
    stale,
    cpu,
    ram: percentOrNull(info.memoryUsage),
    disk: percentOrNull(info.diskUsage),
    cpuLevel: usageLevel(cpu, thresholds),
    sessions: layout.sessions,
    integrations: layout.integrations,
    rates: layout.rates,
    hasDrops: layout.rates[ChartKey.droppedMsgs] > 0,
    alertLevel: null,
    alertCount: 0,
    x: layout.x,
    y: layout.y,
    ringR: layout.ringR,
    discR: layout.discR,
    countLabel: kind === 'broker' ? compactNumber(layout.sessions?.totalCount ?? null) : ''
  };
}

function isOffline(node: ClusterNodeVm): boolean {
  return node.status !== ResourceUsageStatus.ACTIVE || node.stale;
}

function isStale(info: ResourceUsage, cadence: ClusterCadence): boolean {
  return info.status === ResourceUsageStatus.ACTIVE && !!info.lastUpdateTime
    && Date.now() - info.lastUpdateTime > STALE_AFTER_HEARTBEATS * cadence.heartbeatMs;
}

function isServiceInactive(info: ResourceUsage, cadence: ClusterCadence): boolean {
  return info.status !== ResourceUsageStatus.ACTIVE || isStale(info, cadence);
}

function buildEdge(id: string, x1: number, y1: number, x2: number, y2: number, relative: number, rate: number | null,
                   offline: boolean): ClusterEdgeVm {
  const weight = offline ? 0 : relative;
  const particles = weight <= 0 ? 0 : 1 + Math.round(EDGE_MAX_EXTRA_PARTICLES * weight);
  return {
    id,
    path: cubicPath(x1, y1, x2, y2),
    rate,
    strokeWidth: EDGE_MIN_WIDTH + EDGE_WIDTH_GROWTH * weight,
    opacity: EDGE_MIN_OPACITY + EDGE_OPACITY_GROWTH * weight,
    offline,
    particleSlots: Array.from({length: particles}, (_, i) => i),
    durSec: PARTICLE_DURATION_BUCKETS.find(bucket => weight > bucket.minRelative)?.durSec ?? PARTICLE_IDLE_DURATION_SEC
  };
}
