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

import { BaseData } from '@shared/models/base-data';
import { STATUS_COLOR, StatusColor } from '@home/models/entity/entities-table-config.models';
import { defaultResourceUsageAlertSettings, ResourceUsageThresholds } from '@shared/models/settings.models';

export interface ResourceUsage extends BaseData {
  serviceId: string;
  serviceType: ServiceType;
  lastUpdateTime: number;
  cpuUsage: number;
  cpuCount: number;
  memoryUsage: number;
  totalMemory: number;
  diskUsage: number;
  totalDiskSpace: number;
  status: ResourceUsageStatus;
}

export enum ResourceUsageStatus {
  ACTIVE = 'ACTIVE',
  INACTIVE = 'INACTIVE',
  OUTDATED = 'OUTDATED'
}

export const resourceUsageStatusTranslationMap = new Map<ResourceUsageStatus, string>(
  [
    [ResourceUsageStatus.ACTIVE, 'monitoring.resource-usage.active'],
    [ResourceUsageStatus.INACTIVE, 'monitoring.resource-usage.inactive'],
    [ResourceUsageStatus.OUTDATED, 'monitoring.resource-usage.outdated']
  ]
);

export const resourceUsageTooltipTranslationMap = new Map<ResourceUsageStatus, string>(
  [
    [ResourceUsageStatus.ACTIVE, 'monitoring.resource-usage.active-hint'],
    [ResourceUsageStatus.INACTIVE, 'monitoring.resource-usage.inactive-hint'],
    [ResourceUsageStatus.OUTDATED, 'monitoring.resource-usage.outdated-hint']
  ]
);

export const resourceUsageStatusStyle = new Map<ResourceUsageStatus, StatusColor>(
  [
    [ResourceUsageStatus.ACTIVE, STATUS_COLOR.ACTIVE],
    [ResourceUsageStatus.INACTIVE, STATUS_COLOR.DISABLED],
    [ResourceUsageStatus.OUTDATED, STATUS_COLOR.INACTIVE]
  ]
);

export enum ServiceType {
  TBMQ = 'TBMQ',
  TBMQ_INTEGRATION_EXECUTOR = 'TBMQ_INTEGRATION_EXECUTOR',
}

// Mirrors ServiceInfoIds.timeseriesEntityId on the server: brokers keep the bare id, other services are prefixed by type.
export function serviceTimeseriesId(serviceType: ServiceType, serviceId: string): string {
  return serviceType === ServiceType.TBMQ ? serviceId : `${serviceType}:${serviceId}`;
}

export const serviceTypeMap = new Map<ServiceType, string>(
  [
    [ServiceType.TBMQ, 'TBMQ'],
    [ServiceType.TBMQ_INTEGRATION_EXECUTOR, 'TBMQ Integration Executor'],
  ]
);

export type UsageLevel = 'ok' | 'warn' | 'crit';

export function usageLevel(percent: number | null, thresholds: ResourceUsageThresholds = defaultResourceUsageAlertSettings): UsageLevel {
  if (percent === null) {
    return 'ok';
  }
  if (percent > thresholds.criticalUsage) {
    return 'crit';
  }
  return percent > thresholds.highUsage ? 'warn' : 'ok';
}
