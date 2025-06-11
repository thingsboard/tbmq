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

import { ActivatedRoute, ActivatedRouteSnapshot, Router } from '@angular/router';
import {
  CellActionDescriptor,
  copyContentActionCell,
  EntityTableColumn,
  EntityTableConfig,
  GroupActionDescriptor,
  cellStatus
} from '@home/models/entity/entities-table-config.models';
import { TranslateService } from '@ngx-translate/core';
import { DatePipe } from '@angular/common';
import { EntityType, entityTypeResources, entityTypeTranslations } from '@shared/models/entity-type.models';
import { TimePageLink } from '@shared/models/page/page-link';
import { forkJoin, mergeMap, Observable, of } from 'rxjs';
import { PageData } from '@shared/models/page/page-data';
import { deepClone } from '@core/utils';
import { Injectable } from '@angular/core';
import {
  BlockedClient,
  BlockedClientFilterConfig,
  BlockedClientsQuery,
  BlockedClientStatus,
  blockedClientStatusStyle,
  blockedClientStatusTranslationMap,
  blockedClientTypeTranslationMap,
  regexMatchTargetTranslationMap
} from '@shared/models/blocked-client.models';
import { BlockedClientService } from '@core/http/blocked-client.service';
import { BlockedClientsTableHeaderComponent } from '@home/pages/blocked-clients/blocked-clients-table-header.component';
import { DialogService } from '@core/services/dialog.service';
import { BlockedClientComponent } from '@home/pages/blocked-clients/blocked-client.component';
import { Direction } from '@shared/models/page/sort-order';
import { forAllTimeInterval, MINUTE } from '@shared/models/time/time.models';
import moment from 'moment';

@Injectable()
export class BlockedClientsTableConfigResolver {

  private readonly config: EntityTableConfig<BlockedClient> = new EntityTableConfig<BlockedClient>();

  blockedClientsFilterConfig: BlockedClientFilterConfig = {};
  private timeToLive = 0;

  constructor(private blockedClientService: BlockedClientService,
              private translate: TranslateService,
              private datePipe: DatePipe,
              private route: ActivatedRoute,
              private router: Router,
              private dialogService: DialogService) {

    this.config.entityType = EntityType.BLOCKED_CLIENT;
    this.config.entityTranslations = entityTypeTranslations.get(EntityType.BLOCKED_CLIENT);
    this.config.entityResources = entityTypeResources.get(EntityType.BLOCKED_CLIENT);
    this.config.tableTitle = this.translate.instant('blocked-client.blocked-clients');
    this.config.defaultSortOrder = {property: 'expirationTime', direction: Direction.ASC};
    this.config.addDialogStyle = {height: '550px'};
    this.config.entitiesDeleteEnabled = false;
    this.config.detailsPanelEnabled = false;
    this.config.entityComponent = BlockedClientComponent;
    this.config.headerComponent = BlockedClientsTableHeaderComponent;
    this.config.useTimePageLink = true;
    this.config.forAllTimeEnabled = true;
    this.config.defaultTimewindowInterval = forAllTimeInterval();

    this.config.groupActionDescriptors = this.configureGroupActions();
    this.config.cellActionDescriptors = this.configureCellActions();
    this.config.saveEntity = entity => this.blockedClientService.saveBlockedClient(entity);
    this.config.entitiesFetchFunction = pageLink => this.fetchEntities(pageLink as TimePageLink);

    this.config.columns.push(
      new EntityTableColumn<BlockedClient>('expirationTime', 'blocked-client.expiration-time', '150px',
        entity => {
          return this.isNeverExpires(entity)
            ? this.translate.instant('blocked-client.expiration-time-never')
            : this.datePipe.transform(entity.expirationTime, 'yyyy-MM-dd HH:mm:ss');
        },
        undefined, undefined, undefined,
          entity => {
            const label = this.isActive(entity) ? 'blocked-client.expires' : 'blocked-client.status-expired';
            if (this.isNeverExpires(entity)) {
              return;
            }
            return `${this.translate.instant(label)} ${moment(entity.expirationTime).fromNow()}`
          }
        ),
      new EntityTableColumn<BlockedClient>('type', 'blocked-client.type', '120px',
        entity => this.translate.instant(blockedClientTypeTranslationMap.get(entity.type))),
      new EntityTableColumn<BlockedClient>('regexMatchTarget', 'blocked-client.regex-match-target', '120px',
        entity => entity.regexMatchTarget ? this.translate.instant(regexMatchTargetTranslationMap.get(entity.regexMatchTarget)) : ''),
      new EntityTableColumn<BlockedClient>('value', 'blocked-client.value', '30%',
        entity => entity.value, undefined, true, undefined, undefined, false,
        copyContentActionCell('value', this.translate)),
      new EntityTableColumn<BlockedClient>('description', 'blocked-client.description', '30%',
        undefined, () => { return { textWrap: 'wrap' }}),
      new EntityTableColumn<BlockedClient>('status', 'blocked-client.status', '120px',
        entity => this.statusContent(entity), undefined, true, undefined, entity => this.statusTooltip(entity))
    );
    this.config.addActionDescriptors.push(
      {
        name: this.translate.instant('blocked-client.add-blocked-client'),
        icon: 'add',
        isEnabled: () => true,
        onAction: ($event) => this.config.getTable().addEntity($event)
      }
    );
  }

  resolve(route: ActivatedRouteSnapshot): Observable<EntityTableConfig<BlockedClient>> {
    this.config.componentsData = {};
    this.config.componentsData.blockedClientsFilterConfig = {};
    for (const key of Object.keys(route.queryParams)) {
      if (route.queryParams[key] && route.queryParams[key].length) {
        this.config.componentsData.blockedClientsFilterConfig[key] = route.queryParams[key];
      }
    }
    return this.blockedClientService.getBlockedClientsTimeToLive()
      .pipe(mergeMap(ttl => {
        this.timeToLive = ttl;
        return of(this.config);
      }));
  }

  private fetchEntities(pageLink: TimePageLink): Observable<PageData<BlockedClient>> {
    this.blockedClientsFilterConfig = deepClone(this.config.componentsData.blockedClientsFilterConfig);
    const routerQueryParams: BlockedClientFilterConfig = this.route.snapshot.queryParams;
    if (routerQueryParams) {
      const queryParams = deepClone(routerQueryParams);
      let replaceUrl = false;
      if (routerQueryParams?.value) {
        this.blockedClientsFilterConfig.value = routerQueryParams?.value;
        delete queryParams.value;
        replaceUrl = true;
      }
      if (routerQueryParams?.typeList) {
        this.blockedClientsFilterConfig.typeList = routerQueryParams?.typeList;
        delete queryParams.typeList;
      }
      if (routerQueryParams?.regexMatchTargetList) {
        this.blockedClientsFilterConfig.regexMatchTargetList = routerQueryParams?.regexMatchTargetList;
        delete queryParams.regexMatchTargetList;
      }
      if (replaceUrl) {
        this.router.navigate([], {
          relativeTo: this.route,
          queryParams,
          queryParamsHandling: '',
          replaceUrl: true
        });
      }
    }
    const filter = this.resolveFilter(this.blockedClientsFilterConfig);
    const query = new BlockedClientsQuery(pageLink, filter);
    return this.blockedClientService.getBlockedClientsV2(query);
  }

  private resolveFilter(filterConfig?: BlockedClientFilterConfig): BlockedClientFilterConfig {
    const filter: BlockedClientFilterConfig = {};
    if (filterConfig) {
      filter.value = filterConfig.value;
      filter.typeList = filterConfig.typeList;
      filter.regexMatchTargetList = filterConfig.regexMatchTargetList;
    }
    return filter;
  }

  private configureGroupActions(): Array<GroupActionDescriptor<BlockedClient>> {
    const actions: Array<GroupActionDescriptor<BlockedClient>> = [];
    actions.push(
      {
        name: this.translate.instant('action.delete'),
        icon: 'mdi:trash-can-outline',
        isEnabled: true,
        onAction: ($event, entities) => this.deleteEntities($event, entities)
      }
    );
    return actions;
  }

  private configureCellActions(): Array<CellActionDescriptor<BlockedClient>> {
    const actions: Array<CellActionDescriptor<BlockedClient>> = [];
    actions.push(
      {
        name: this.translate.instant('action.delete'),
        icon: 'mdi:trash-can-outline',
        isEnabled: () => true,
        onAction: ($event, entity) => this.deleteEntity($event, entity)
      }
    );
    return actions;
  }

  private deleteEntities($event: Event, entities: Array<BlockedClient>) {
    if ($event) {
      $event.stopPropagation();
    }
    this.dialogService.confirm(
      this.translate.instant('blocked-client.delete-blocked-clients-title', {count: entities.length}),
      this.translate.instant('blocked-client.delete-blocked-clients-text'),
      this.translate.instant('action.no'),
      this.translate.instant('action.yes'),
      true
    ).subscribe((res) => {
        if (res) {
          const tasks: Observable<any>[] = [];
          entities.forEach(
            (entity) => {
              tasks.push(this.blockedClientService.deleteBlockedClient(entity));
            }
          );
          forkJoin(tasks).subscribe(
            () => {
              this.config.getTable().updateData();
            }
          );
        }
      }
    );
  }

  private deleteEntity($event: Event, entity: BlockedClient) {
    if ($event) {
      $event.stopPropagation();
    }
    this.dialogService.confirm(
      this.translate.instant('blocked-client.delete-blocked-client-title', {value: entity.value}),
      this.translate.instant('blocked-client.delete-blocked-client-text'),
      this.translate.instant('action.no'),
      this.translate.instant('action.yes'),
      true
    ).subscribe((result) => {
      if (result) {
        this.blockedClientService.deleteBlockedClient(entity).subscribe(
          () => {
            this.config.getTable().updateData();
          }
        );
      }
    });
  }

  private statusContent(entity: BlockedClient): string {
    const status = this.translate.instant(entity.status);
    const content = this.translate.instant(blockedClientStatusTranslationMap.get(status));
    const color = blockedClientStatusStyle.get(status).content;
    const background = blockedClientStatusStyle.get(status).background;
    return cellStatus(content, color, background);
  }

  private statusTooltip(entity: BlockedClient): string {
    if (this.isNeverExpires(entity) || this.isActive(entity)) {
      return;
    }
    const timeLeftToLiveMs = this.timeToLive * MINUTE;
    const timeFromNow = entity.expirationTime < Date.now()
      ? Date.now() + timeLeftToLiveMs
      : entity.expirationTime + timeLeftToLiveMs;
    return `${this.translate.instant('blocked-client.will-be-deleted')} ${moment(timeFromNow).fromNow()}`;
  }

  private isNeverExpires(entity: BlockedClient): boolean {
    return entity.expirationTime === 0;
  }

  private isActive(entity: BlockedClient): boolean {
    return entity.status === BlockedClientStatus.ACTIVE;
  }
}
