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

import {
  CellActionDescriptor,
  DateEntityTableColumn,
  EntityTableColumn,
  EntityTableConfig,
  cellWithIcon,
  cellStatus,
  STATUS_COLOR
} from '@home/models/entity/entities-table-config.models';
import { TranslateService } from '@ngx-translate/core';
import { DatePipe } from '@angular/common';
import { EntityType, entityTypeResources, entityTypeTranslations } from '@shared/models/entity-type.models';
import { Injectable } from '@angular/core';
import { TimeseriesService } from '@core/http/timeseries.service';
import { convertDataSizeUnits } from '@core/utils';
import { DataSizeUnit, DataSizeUnitTranslationMap } from '@shared/models/ws-client.model';
import {
  ResourceUsage,
  ResourceUsageStatus,
  resourceUsageStatusStyle,
  resourceUsageStatusTranslationMap,
  resourceUsageTooltipTranslationMap,
  serviceTypeMap
} from '@app/shared/models/resource-usage.model';
import { DialogService } from '@core/services/dialog.service';

@Injectable()
export class ResourceUsageTableConfigResolver {

  private readonly config: EntityTableConfig<ResourceUsage> = new EntityTableConfig<ResourceUsage>();

  constructor(private timeseriesService: TimeseriesService,
              private translate: TranslateService,
              private dialogService: DialogService,
              private datePipe: DatePipe) {

    this.config.entityType = EntityType.RESOURCE_USAGE;
    this.config.entityComponent = null;
    this.config.entityTranslations = entityTypeTranslations.get(EntityType.RESOURCE_USAGE);
    this.config.entityResources = entityTypeResources.get(EntityType.RESOURCE_USAGE);
    this.config.tableTitle = this.translate.instant('monitoring.resource-usage.resource-usage');

    this.config.detailsPanelEnabled = false;
    this.config.addEnabled = false;
    this.config.selectionEnabled = false;
    this.config.entitiesDeleteEnabled = false;
    this.config.searchEnabled = false;
    this.config.displayPagination = false;

    this.config.cellActionDescriptors = this.configureCellActions();
    this.config.entitiesFetchFunction = () => this.timeseriesService.getServiceInfos();

    this.config.columns.push(
      new DateEntityTableColumn<ResourceUsage>('lastUpdateTime', 'common.update-time', this.datePipe, '150px', undefined, undefined, false),
      new EntityTableColumn<ResourceUsage>('serviceId', 'monitoring.resource-usage.service-id', '20%', undefined, undefined, false),
      new EntityTableColumn<ResourceUsage>('serviceType', 'monitoring.resource-usage.service-type', '20%',
          entity => serviceTypeMap.get(entity.serviceType), undefined, false),
      new EntityTableColumn<ResourceUsage>('cpuUsage', 'monitoring.resource-usage.cpu-usage', '100px', (entity) => this.usageCell(entity.cpuUsage),
        undefined, false, undefined, (entity) => this.usageTooltip(entity.cpuUsage, 'cpu', entity.cpuCount)),
      new EntityTableColumn<ResourceUsage>('memoryUsage', 'monitoring.resource-usage.memory-usage', '100px', (entity) => this.usageCell(entity.memoryUsage),
        undefined, false, undefined, (entity) => this.usageTooltip(entity.memoryUsage, 'ram', entity.totalMemory)),
      new EntityTableColumn<ResourceUsage>('diskUsage', 'monitoring.resource-usage.disk-usage', '100px', (entity) => this.usageCell(entity.diskUsage),
        undefined, false, undefined, (entity) => this.usageTooltip(entity.diskUsage, 'disk', entity.totalDiskSpace)),
      new EntityTableColumn<ResourceUsage>('status', 'monitoring.resource-usage.status', '15%',
        (entity) => {
          const content = this.translate.instant(resourceUsageStatusTranslationMap.get(entity.status));
          const color = resourceUsageStatusStyle.get(entity.status).content;
          const background = resourceUsageStatusStyle.get(entity.status).background;
          return cellStatus(content, color, background);
        }, undefined, false, undefined,
        (entity) => this.translate.instant(resourceUsageTooltipTranslationMap.get(entity.status))),
    );
  }

  resolve(): EntityTableConfig<ResourceUsage> {
    return this.config;
  }

  private configureCellActions(): Array<CellActionDescriptor<ResourceUsage>> {
    const actions: Array<CellActionDescriptor<ResourceUsage>> = [];
    actions.push(
      {
        name: this.translate.instant('monitoring.resource-usage.delete-resource'),
        icon: 'mdi:trash-can-outline',
        isEnabled: (entity) => entity.status === ResourceUsageStatus.OUTDATED,
        onAction: ($event, entity) => this.deleteResource($event, entity)
      }
    );
    return actions;
  }

  private deleteResource($event: Event, entity: ResourceUsage) {
    if ($event) {
      $event.stopPropagation();
    }
    const title = this.translate.instant('monitoring.resource-usage.delete-resource-title', { id: entity.serviceId });
    const content = this.translate.instant('monitoring.resource-usage.delete-resource-text');
    this.dialogService.confirm(
      title,
      content,
      this.translate.instant('action.no'),
      this.translate.instant('action.yes'),
      true
    ).subscribe((res) => {
        if (res) {
          this.timeseriesService.deleteServiceInfo(entity.serviceId).subscribe(() => this.config.getTable().updateData());
        }
      }
    );
  }

  private toGb(value: number): string {
    return convertDataSizeUnits(value, DataSizeUnit.BYTE, DataSizeUnit.GIGABYTE).toFixed() + ' ' + DataSizeUnitTranslationMap.get(DataSizeUnit.GIGABYTE);
  }

  private toPercentage(value: number): string {
    if (!value) {
      return '';
    }
    return value + '%';
  }

  private usageCell(usage: number): string {
    const value = this.toPercentage(usage);
    let iconColor;
    if (this.isCritical(usage)) {
      iconColor = STATUS_COLOR.INACTIVE.content;
    } else if (this.isHigh(usage)) {
      iconColor = STATUS_COLOR.PENDING.content;
    } else {
      return value;
    }
    return cellWithIcon(value,  'warning', 'transparent', iconColor);
  }

  private usageTooltip(usage: number, type: string, value: number): string  {
    if (this.isCritical(usage)) {
      return this.translate.instant(`monitoring.resource-usage.${type}-critical-text`);
    } else if (this.isHigh(usage)) {
      return this.translate.instant(`monitoring.resource-usage.${type}-warning-text`);
    } else {
      const prefix = this.translate.instant('home.total');
      if (type === 'cpu') {
        return `${prefix} ${value} ${this.translate.instant('monitoring.resource-usage.cpu-count')}`;
      } else {
        return `${prefix} ${this.toGb(value)}`;
      }
    }
  }

  private isCritical(usage: number): boolean {
    return usage > 85;
  }

  private isHigh(usage: number): boolean {
    return usage > 75;
  }
}
