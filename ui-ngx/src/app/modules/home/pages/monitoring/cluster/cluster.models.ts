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
import { ClientSessionNodeStatsInfo, ClientSessionStatsInfo } from '@shared/models/session.model';
import { KafkaBroker, KafkaConsumerGroup } from '@shared/models/kafka.model';
import { PageData } from '@shared/models/page/page-data';
import { ChartKey, TimeseriesData, TsValue } from '@shared/models/chart.model';
import { isDefinedAndNotNull } from '@core/utils';

export type UsageLevel = 'ok' | 'warn' | 'crit';
export type ClusterNodeKind = 'broker' | 'executor';

export const VIEWBOX_WIDTH = 1060;
export const BASE_HEIGHT = 476;
export const ROW_GAP = 104;
export const PAD_Y = 90;
export const COLUMN_X = {
  clients: 120,
  brokers: 430,
  kafka: 720,
  executors: 960
};
export const CLIENTS_PILL_HALF_WIDTH = 26;
export const BROKER_RING_RADIUS = 34;
export const EXECUTOR_RING_RADIUS = 22;
export const EXECUTOR_DISC_RADIUS = 11;
export const KAFKA_RADIUS = 46;
export const EDGE_GAP = 4;
export const EXECUTOR_EDGE_WEIGHT = 0.3;
export const MAX_SAMPLES = 40;
export const CRITICAL_USAGE = 85;
export const HIGH_USAGE = 75;

export interface ClusterNodeVm {
  id: string;
  kind: ClusterNodeKind;
  serviceType: ServiceType;
  status: ResourceUsageStatus;
  lastUpdateTime: number | null;
  cpu: number | null;
  ram: number | null;
  disk: number | null;
  cpuLevel: UsageLevel;
  sessions: ClientSessionStatsInfo | null;
  incomingRate: number;
  outgoingRate: number;
  x: number;
  y: number;
  ringR: number;
  discR: number;
  countLabel: string;
}

export interface ClusterEdgeVm {
  id: string;
  path: string;
  rel: number;
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
}

export interface ClusterKafkaVm {
  x: number;
  y: number;
  points: string;
  brokerCount: number | null;
  totalLag: number | null;
}

export interface ClusterMapVm {
  width: number;
  height: number;
  clients: ClusterClientsVm;
  brokers: ClusterNodeVm[];
  executors: ClusterNodeVm[];
  kafka: ClusterKafkaVm;
  edges: ClusterEdgeVm[];
}

export interface ClusterRawData {
  services: PageData<ResourceUsage> | null;
  sessionsByNode: ClientSessionNodeStatsInfo | null;
  kafkaBrokers: PageData<KafkaBroker> | null;
  consumerGroups: PageData<KafkaConsumerGroup> | null;
  latestByBroker: Record<string, TimeseriesData | null>;
}

interface MessageRates {
  incoming: number;
  outgoing: number;
}

const compactFormatter = new Intl.NumberFormat('en', {notation: 'compact', maximumFractionDigits: 1});

export function compactNumber(value: number | null): string {
  return isDefinedAndNotNull(value) ? compactFormatter.format(value) : '—';
}

export function usageLevel(percent: number | null): UsageLevel {
  if (percent === null) {
    return 'ok';
  }
  if (percent > CRITICAL_USAGE) {
    return 'crit';
  }
  return percent > HIGH_USAGE ? 'warn' : 'ok';
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

export function appendSample(samples: TsValue[], sample: TsValue): TsValue[] {
  const last = samples[samples.length - 1];
  if (last && last.ts === sample.ts) {
    return samples;
  }
  return [...samples, sample].slice(-MAX_SAMPLES);
}

export function emptyClusterVm(): ClusterMapVm {
  const centerY = BASE_HEIGHT / 2;
  return {
    width: VIEWBOX_WIDTH,
    height: BASE_HEIGHT,
    clients: {x: COLUMN_X.clients, y: centerY, hasSessionStats: false, totalSessions: 0, connectedSessions: 0},
    brokers: [],
    executors: [],
    kafka: {x: COLUMN_X.kafka, y: centerY, points: hexagonPoints(COLUMN_X.kafka, centerY, KAFKA_RADIUS), brokerCount: null, totalLag: null},
    edges: []
  };
}

export function assembleClusterVm(raw: ClusterRawData, prev: ClusterMapVm, statsIntervalMinutes: number): ClusterMapVm {
  const services = raw.services?.data ?? [];
  const brokerInfos = services.filter(s => s.serviceType === ServiceType.TBMQ).sort(byServiceId);
  const executorInfos = services.filter(s => s.serviceType === ServiceType.TBMQ_INTEGRATION_EXECUTOR).sort(byServiceId);

  const rows = Math.max(brokerInfos.length, executorInfos.length, 1);
  const height = Math.max(BASE_HEIGHT, 2 * PAD_Y + (rows - 1) * ROW_GAP);
  const centerY = height / 2;

  const rates = brokerInfos.map(info => messageRates(raw.latestByBroker[info.serviceId], statsIntervalMinutes));
  const sessions = brokerInfos.map(info => raw.sessionsByNode?.[info.serviceId] ?? null);
  const maxSessions = Math.max(0, ...sessions.map(s => s?.totalCount ?? 0));
  const maxIncoming = Math.max(0, ...rates.map(r => r.incoming));
  const maxOutgoing = Math.max(0, ...rates.map(r => r.outgoing));

  const brokers = brokerInfos.map((info, i) => buildNode(info, 'broker', {
    x: COLUMN_X.brokers,
    y: rowY(centerY, i, brokerInfos.length),
    ringR: BROKER_RING_RADIUS,
    discR: discRadius(sessions[i]?.totalCount ?? 0, maxSessions),
    sessions: sessions[i],
    rates: rates[i]
  }));
  const executors = executorInfos.map((info, i) => buildNode(info, 'executor', {
    x: COLUMN_X.executors,
    y: rowY(centerY, i, executorInfos.length),
    ringR: EXECUTOR_RING_RADIUS,
    discR: EXECUTOR_DISC_RADIUS,
    sessions: null,
    rates: {incoming: 0, outgoing: 0}
  }));

  const edges: ClusterEdgeVm[] = [];
  for (const broker of brokers) {
    edges.push(buildEdge(`e-in-${broker.id}`,
      COLUMN_X.clients + CLIENTS_PILL_HALF_WIDTH + EDGE_GAP, centerY,
      broker.x - broker.ringR - EDGE_GAP, broker.y,
      relativeTo(broker.incomingRate, maxIncoming)));
    edges.push(buildEdge(`e-out-${broker.id}`,
      broker.x + broker.ringR + EDGE_GAP, broker.y,
      COLUMN_X.kafka - KAFKA_RADIUS - EDGE_GAP, centerY,
      relativeTo(broker.outgoingRate, maxOutgoing)));
  }
  for (const executor of executors) {
    edges.push(buildEdge(`e-ie-${executor.id}`,
      COLUMN_X.kafka + KAFKA_RADIUS + EDGE_GAP, centerY,
      executor.x - executor.ringR - EDGE_GAP, executor.y,
      EXECUTOR_EDGE_WEIGHT));
  }

  const nodeStats = Object.values(raw.sessionsByNode ?? {});
  return {
    width: VIEWBOX_WIDTH,
    height,
    clients: {
      x: COLUMN_X.clients,
      y: centerY,
      hasSessionStats: isDefinedAndNotNull(raw.sessionsByNode),
      totalSessions: nodeStats.reduce((sum, s) => sum + s.totalCount, 0),
      connectedSessions: nodeStats.reduce((sum, s) => sum + s.connectedCount, 0)
    },
    brokers,
    executors,
    kafka: {
      x: COLUMN_X.kafka,
      y: centerY,
      points: hexagonPoints(COLUMN_X.kafka, centerY, KAFKA_RADIUS),
      brokerCount: raw.kafkaBrokers?.totalElements ?? prev.kafka.brokerCount,
      totalLag: raw.consumerGroups
        ? raw.consumerGroups.data.reduce((sum, group) => sum + (Number(group.lag) || 0), 0)
        : prev.kafka.totalLag
    },
    edges
  };
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

function latestValue(data: TimeseriesData | null, key: ChartKey): number {
  return Number(data?.[key]?.[0]?.value) || 0;
}

function messageRates(data: TimeseriesData | null, statsIntervalMinutes: number): MessageRates {
  const interval = statsIntervalMinutes > 0 ? statsIntervalMinutes : 1;
  return {
    incoming: latestValue(data, ChartKey.incomingMsgs) / interval,
    outgoing: latestValue(data, ChartKey.outgoingMsgs) / interval
  };
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
  rates: MessageRates;
}): ClusterNodeVm {
  const cpu = percentOrNull(info.cpuUsage);
  return {
    id: info.serviceId,
    kind,
    serviceType: info.serviceType,
    status: info.status,
    lastUpdateTime: info.lastUpdateTime || null,
    cpu,
    ram: percentOrNull(info.memoryUsage),
    disk: percentOrNull(info.diskUsage),
    cpuLevel: usageLevel(cpu),
    sessions: layout.sessions,
    incomingRate: layout.rates.incoming,
    outgoingRate: layout.rates.outgoing,
    x: layout.x,
    y: layout.y,
    ringR: layout.ringR,
    discR: layout.discR,
    countLabel: kind === 'broker' ? compactNumber(layout.sessions?.totalCount ?? null) : ''
  };
}

function buildEdge(id: string, x1: number, y1: number, x2: number, y2: number, rel: number): ClusterEdgeVm {
  const particles = rel <= 0 ? 0 : 1 + Math.round(3 * rel);
  return {
    id,
    path: cubicPath(x1, y1, x2, y2),
    rel,
    strokeWidth: 1.5 + 4.5 * rel,
    opacity: 0.25 + 0.6 * rel,
    particleSlots: Array.from({length: particles}, (_, i) => i),
    // Bucketed so a small rate change does not rewrite `dur`, which would restart the SMIL timeline.
    durSec: rel > 0.66 ? 2.4 : rel > 0.33 ? 3.6 : 5
  };
}
