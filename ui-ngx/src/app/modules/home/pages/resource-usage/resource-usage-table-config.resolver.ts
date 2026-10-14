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
  serviceTimeseriesId,
  serviceTypeMap,
  usageLevel
} from '@app/shared/models/resource-usage.model';
import { DialogService } from '@core/services/dialog.service';
import { forkJoin, Observable, of, Subscription } from 'rxjs';
import { SettingsService } from '@core/http/settings.service';
import { defaultResourceUsageAlertSettings, ResourceUsageThresholds } from '@shared/models/settings.models';
import { catchError, map, tap } from 'rxjs/operators';
import { SnapshotTrend } from '@shared/models/live-stats.model';
import { AggregationType, HOUR } from '@shared/models/time/time.models';
import { Direction } from '@shared/models/page/sort-order';
import { PageData } from '@shared/models/page/page-data';

const CPU_TREND_WINDOW_MS = HOUR;
const CPU_TREND_MAX_SAMPLES = 120;

@Injectable()
export class ResourceUsageTableConfigResolver {

  private readonly config: EntityTableConfig<ResourceUsage> = new EntityTableConfig<ResourceUsage>();
  private readonly cpuTrend = new SnapshotTrend(CPU_TREND_MAX_SAMPLES);
  private thresholds: ResourceUsageThresholds = defaultResourceUsageAlertSettings;
  private cpuTrendLoad: Subscription | null = null;

  constructor(private timeseriesService: TimeseriesService,
              private translate: TranslateService,
              private dialogService: DialogService,
              private datePipe: DatePipe,
              private settingsService: SettingsService) {

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
    this.config.entitiesFetchFunction = () => this.timeseriesService.getServiceInfos().pipe(
      tap(page => this.loadCpuTrend(page))
    );

    this.config.columns.push(
      new DateEntityTableColumn<ResourceUsage>('lastUpdateTime', 'common.update-time', this.datePipe, '150px', undefined, undefined, false),
      new EntityTableColumn<ResourceUsage>('serviceId', 'monitoring.resource-usage.service-id', '20%', undefined, undefined, false),
      new EntityTableColumn<ResourceUsage>('serviceType', 'monitoring.resource-usage.service-type', '20%',
          entity => serviceTypeMap.get(entity.serviceType), undefined, false),
      new EntityTableColumn<ResourceUsage>('cpuUsage', 'monitoring.resource-usage.cpu-usage', '100px', (entity) => this.usageCell(entity.cpuUsage),
        undefined, false, undefined, (entity) => this.usageTooltip(entity.cpuUsage, 'cpu', entity.cpuCount)),
      new EntityTableColumn<ResourceUsage>('cpuTrend', 'monitoring.resource-usage.cpu-trend', '120px',
        (entity) => this.cpuTrend.sparkline(this.trendId(entity)), undefined, false, undefined,
        (entity) => this.cpuTrendTooltip(entity)),
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

  resolve(): Observable<EntityTableConfig<ResourceUsage>> {
    this.cpuTrendLoad?.unsubscribe();
    return this.settingsService.getResourceUsageThresholds().pipe(
      map(thresholds => {
        this.thresholds = thresholds;
        return this.config;
      })
    );
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
          this.timeseriesService.deleteServiceInfo(entity.serviceId, entity.serviceType).subscribe(() => this.config.getTable().updateData());
        }
      }
    );
  }

  // CPU usage is stored as history, so the trend covers the last hour no matter how often the table is refreshed. The rows
  // are shown right away and the sparklines fill in once every history request has answered.
  private loadCpuTrend(page: PageData<ResourceUsage>) {
    this.cpuTrendLoad?.unsubscribe();
    if (!page.data.length) {
      return;
    }
    const endTs = Date.now();
    this.cpuTrendLoad = forkJoin(page.data.map(usage => {
      const id = this.trendId(usage);
      return this.timeseriesService.getEntityTimeseries(id, endTs - CPU_TREND_WINDOW_MS, endTs, ['cpuUsage'], CPU_TREND_MAX_SAMPLES,
        AggregationType.NONE, undefined, Direction.ASC).pipe(
        map(history => (history.cpuUsage ?? []).map(sample => Number(sample.value))),
        catchError(() => of<number[]>([])),
        map(values => [id, values] as const)
      );
    })).subscribe(entries => {
      this.cpuTrend.replace(entries);
      this.refreshCpuTrendCells(page.data.length);
    });
  }

  private refreshCpuTrendCells(rows: number) {
    const table = this.config.getTable();
    const column = this.config.columns.findIndex(column => column.key === 'cpuTrend');
    if (!table || column < 0) {
      return;
    }
    for (let row = 0; row < rows; row++) {
      table.clearCellCache(column, row);
    }
    table.detectChanges();
  }

  private trendId(usage: ResourceUsage): string {
    return serviceTimeseriesId(usage.serviceType, usage.serviceId);
  }

  private cpuTrendTooltip(usage: ResourceUsage): string {
    const range = this.cpuTrend.range(this.trendId(usage));
    if (!range) {
      return undefined;
    }
    return this.translate.instant('monitoring.resource-usage.cpu-trend-tooltip', {min: Math.round(range.min), max: Math.round(range.max)});
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
    const level = usageLevel(usage ?? null, this.thresholds);
    if (level === 'ok') {
      return value;
    }
    return cellWithIcon(value, 'warning', 'transparent', level === 'crit' ? STATUS_COLOR.INACTIVE.content : STATUS_COLOR.PENDING.content);
  }

  private usageTooltip(usage: number, type: string, value: number): string  {
    const level = usageLevel(usage ?? null, this.thresholds);
    if (level === 'crit') {
      return this.translate.instant(`monitoring.resource-usage.${type}-critical-text`);
    } else if (level === 'warn') {
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
}
