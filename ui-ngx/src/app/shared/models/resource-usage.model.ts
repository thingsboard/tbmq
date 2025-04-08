///
/// Copyright © 2016-2025 The Thingsboard Authors
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

export interface ResourceUsage extends BaseData {
  serviceId: string;
  serviceType: string;
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
