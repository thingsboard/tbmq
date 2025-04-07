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
  DateEntityTableColumn,
  EntityTableColumn,
  EntityTableConfig
} from '@home/models/entity/entities-table-config.models';
import { TranslateService } from '@ngx-translate/core';
import { DatePipe } from '@angular/common';
import { EntityType, entityTypeResources, entityTypeTranslations } from '@shared/models/entity-type.models';
import { Injectable } from '@angular/core';
import { Observable, of } from 'rxjs';
import { StatsService } from '@core/http/stats.service';
import { convertDataSizeUnits } from '@core/utils';
import { DataSizeUnitType, DataSizeUnitTypeTranslationMap } from '@shared/models/ws-client.model';
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
}

@Injectable()
export class ResourceUsageTableConfigResolver {

  private readonly config: EntityTableConfig<ResourceUsage> = new EntityTableConfig<ResourceUsage>();

  constructor(private statsService: StatsService,
              private translate: TranslateService,
              private datePipe: DatePipe) {

    this.config.entityType = EntityType.RESOURCE_USAGE;
    this.config.entityComponent = null;
    this.config.detailsPanelEnabled = false;
    this.config.entityTranslations = entityTypeTranslations.get(EntityType.RESOURCE_USAGE);
    this.config.entityResources = entityTypeResources.get(EntityType.RESOURCE_USAGE);
    this.config.tableTitle = this.translate.instant('monitoring.resource-usage.resource-usage');
    this.config.addEnabled = false;
    this.config.selectionEnabled = false;
    this.config.entitiesDeleteEnabled = false;
    this.config.searchEnabled = false;
    this.config.displayPagination = false;

    this.config.columns.push(
      new DateEntityTableColumn<ResourceUsage>('lastUpdateTime', 'monitoring.resource-usage.last-update-time', this.datePipe, '150px'),
      new EntityTableColumn<ResourceUsage>('serviceId', 'monitoring.resource-usage.service-id'),
      new EntityTableColumn<ResourceUsage>('serviceType', 'monitoring.resource-usage.service-type'),
      new EntityTableColumn<ResourceUsage>('cpuUsage', 'monitoring.resource-usage.cpu-usage', undefined, entity => entity.cpuUsage + '%'),
      new EntityTableColumn<ResourceUsage>('cpuCount', 'monitoring.resource-usage.cpu-count'),
      new EntityTableColumn<ResourceUsage>('memoryUsage', 'monitoring.resource-usage.memory-usage', undefined,
          entity => entity.memoryUsage + '%'),
      new EntityTableColumn<ResourceUsage>('totalMemory', 'monitoring.resource-usage.total-memory', undefined,
          entity => convertDataSizeUnits(entity.totalMemory, DataSizeUnitType.BYTE, DataSizeUnitType.GIGABYTE).toFixed().toString() + ' ' + DataSizeUnitTypeTranslationMap.get(DataSizeUnitType.GIGABYTE)),
      new EntityTableColumn<ResourceUsage>('diskUsage', 'monitoring.resource-usage.disk-usage', undefined, entity => entity.diskUsage + '%'),
      new EntityTableColumn<ResourceUsage>('totalDiskSpace', 'monitoring.resource-usage.total-disk-space', undefined,
          entity => convertDataSizeUnits(entity.totalDiskSpace, DataSizeUnitType.BYTE, DataSizeUnitType.GIGABYTE).toFixed() + ' ' + DataSizeUnitTypeTranslationMap.get(DataSizeUnitType.GIGABYTE)),
    );

    this.config.entitiesFetchFunction = () => this.statsService.getServiceInfos();
  }

  resolve(): Observable<EntityTableConfig<ResourceUsage>> {
    return of(this.config);
  }
}
