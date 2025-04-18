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
  CellActionDescriptor, CellActionDescriptorType, checkBoxCell,
  DateEntityTableColumn,
  EntityTableColumn,
  EntityTableConfig,
  GroupActionDescriptor
} from '@home/models/entity/entities-table-config.models';
import { TranslateService } from '@ngx-translate/core';
import { DatePipe } from '@angular/common';
import { EntityType, entityTypeResources, entityTypeTranslations } from '@shared/models/entity-type.models';
import { DialogService } from '@core/services/dialog.service';
import { forkJoin, Observable } from 'rxjs';
import { ContentType } from '@shared/models/constants';
import { MatDialog } from '@angular/material/dialog';
import {
  UnauthorizedClient,
  UnauthorizedClientFilterConfig,
  UnauthorizedClientQuery
} from '@shared/models/unauthorized-client.model';
import { UnauthorizedClientService } from '@core/http/unauthorized-client.service';
import { TimePageLink } from '@shared/models/page/page-link';
import { PageData } from '@shared/models/page/page-data';
import { deepClone } from '@core/utils';
import { ActivatedRoute, Router } from '@angular/router';
import { Direction } from '@shared/models/page/sort-order';
import { forAllTimeInterval } from '@shared/models/time/time.models';
import {
  UnauthorizedClientTableHeaderComponent
} from '@home/pages/unauthorized-client/unauthorized-client-table-header.component';
import {
  EventContentDialogComponentDialogData,
  EventContentDialogComponent
} from '@home/components/event/event-content-dialog.component';

export class UnauthorizedClientTableConfig extends EntityTableConfig<UnauthorizedClient, TimePageLink> {

  unauthorizedClientFilterConfig: UnauthorizedClientFilterConfig = {};

  constructor(private dialogService: DialogService,
              private unauthorizedClientService: UnauthorizedClientService,
              private translate: TranslateService,
              private dialog: MatDialog,
              private datePipe: DatePipe,
              public entityId: string = null,
              private route: ActivatedRoute,
              private router: Router) {
    super();

    this.entityType = EntityType.UNAUTHORIZED_CLIENT;
    this.entityComponent = null;

    this.detailsPanelEnabled = false;
    this.entityTranslations = entityTypeTranslations.get(EntityType.UNAUTHORIZED_CLIENT);
    this.entityResources = entityTypeResources.get(EntityType.UNAUTHORIZED_CLIENT);
    this.tableTitle = this.translate.instant('unauthorized-client.unauthorized-clients');
    this.entitiesDeleteEnabled = false;
    this.addEnabled = false;
    this.defaultCursor = true;
    this.rowPointer = true;
    this.defaultSortOrder = {property: 'ts', direction: Direction.DESC};

    this.detailsReadonly = () => true;
    this.handleRowClick = ($event, entity) => this.showReason($event, entity.reason);

    this.headerActionDescriptors.push({
      name: this.translate.instant('unauthorized-client.delete-unauthorized-clients-all'),
      icon: 'delete_forever',
      isEnabled: () => true,
      onAction: ($event) => {
        this.deleteAllUnauthorizedClients($event);
      }
    });

    this.groupActionDescriptors = this.configureGroupActions();
    this.cellActionDescriptors = this.configureCellActions();

    this.headerComponent = UnauthorizedClientTableHeaderComponent;
    this.useTimePageLink = true;
    this.forAllTimeEnabled = true;
    this.defaultTimewindowInterval = forAllTimeInterval();

    this.columns.push(
      new DateEntityTableColumn<UnauthorizedClient>('ts', 'common.update-time', this.datePipe, '150px'),
      new EntityTableColumn<UnauthorizedClient>('clientId', 'mqtt-client.client-id', '15%',
        undefined, () => undefined,
        true, () => ({}), () => undefined, false,
        {
          name: this.translate.instant('action.copy'),
          nameFunction: (entity) => this.translate.instant('action.copy') + ' ' + entity.clientId,
          icon: 'content_copy',
          style: {
            padding: '0px',
            'font-size': '16px',
            'line-height': '16px',
            height: '16px',
            color: 'rgba(0,0,0,.87)'
          },
          isEnabled: (entity) => !!entity.clientId?.length,
          onAction: ($event, entity) => entity.clientId,
          type: CellActionDescriptorType.COPY_BUTTON
        }),
      new EntityTableColumn<UnauthorizedClient>('username', 'common.username', '15%',
        undefined, () => undefined,
        true, () => ({}), () => undefined, false,
        {
          name: this.translate.instant('action.copy'),
          nameFunction: (entity) => this.translate.instant('action.copy') + ' ' + entity.username,
          icon: 'content_copy',
          style: {
            padding: '0px',
            'font-size': '16px',
            'line-height': '16px',
            height: '16px',
            color: 'rgba(0,0,0,.87)'
          },
          isEnabled: (entity) => !!entity.username?.length,
          onAction: ($event, entity) => entity.username,
          type: CellActionDescriptorType.COPY_BUTTON
        }),
      new EntityTableColumn<UnauthorizedClient>('passwordProvided', 'unauthorized-client.password', '60px',
          entity => checkBoxCell(entity?.passwordProvided), undefined, undefined, undefined,
        (entity) => this.passwordProvidedTooltip(entity)),
      new EntityTableColumn<UnauthorizedClient>('tlsUsed', 'unauthorized-client.tls', '60px',
        entity => checkBoxCell(entity?.tlsUsed), undefined, undefined, undefined,
        (entity) => this.tlsUsedTooltip(entity)),
      new EntityTableColumn<UnauthorizedClient>('ipAddress', 'mqtt-client-session.client-ip', '100px'),
      new EntityTableColumn<UnauthorizedClient>('reason', 'unauthorized-client.reason', '25%', (entity) => {
        const content = entity.reason;
        if (content.length) {
          return content.substring(0, 50) + '...';
        }
        return content;
      }, undefined, undefined, undefined, (entity) => entity.reason),
    );

    this.entitiesFetchFunction = pageLink => this.fetchUnauthorizedClients(pageLink as TimePageLink);
  }

  private configureGroupActions(): Array<GroupActionDescriptor<UnauthorizedClient>> {
    const actions: Array<GroupActionDescriptor<UnauthorizedClient>> = [];
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

  private configureCellActions(): Array<CellActionDescriptor<UnauthorizedClient>> {
    const actions: Array<CellActionDescriptor<UnauthorizedClient>> = [];
    actions.push(
      {
        name: this.translate.instant('unauthorized-client.show-reason'),
        icon: 'error_outline',
        isEnabled: () => true,
        onAction: ($event, entity) => this.showReason($event, entity.reason)
      },
      {
        name: this.translate.instant('action.delete'),
        icon: 'mdi:trash-can-outline',
        isEnabled: () => true,
        onAction: ($event, entity) => this.deleteClient($event, entity)
      }
    );
    return actions;
  }

  private deleteEntities($event: Event, entities: Array<UnauthorizedClient>) {
    if ($event) {
      $event.stopPropagation();
    }
    this.dialogService.confirm(
      this.translate.instant('unauthorized-client.delete-unauthorized-clients-title', {count: entities.length}),
      this.translate.instant('unauthorized-client.delete-unauthorized-clients-text'),
      this.translate.instant('action.no'),
      this.translate.instant('action.yes'),
      true
    ).subscribe((res) => {
        if (res) {
          const tasks: Observable<any>[] = [];
          entities.forEach(
            (entity) => {
              tasks.push(this.unauthorizedClientService.deleteUnauthorizedClient(entity.clientId));
            }
          );
          forkJoin(tasks).subscribe(
            () => {
              this.getTable().updateData();
            }
          );
        }
      }
    );
  }

  private deleteClient($event: Event, entity: UnauthorizedClient) {
    if ($event) {
      $event.stopPropagation();
    }
    this.dialogService.confirm(
      this.translate.instant('unauthorized-client.delete-unauthorized-client-title', {clientId: entity.clientId}),
      this.translate.instant('unauthorized-client.delete-unauthorized-client-text'),
      this.translate.instant('action.no'),
      this.translate.instant('action.yes'),
      true
    ).subscribe((result) => {
      if (result) {
        this.unauthorizedClientService.deleteUnauthorizedClient(entity.clientId).subscribe(
          () => {
            this.getTable().updateData();
          }
        );
      }
    });
  }

  private fetchUnauthorizedClients(pageLink: TimePageLink): Observable<PageData<UnauthorizedClient>> {
    const routerQueryParams: UnauthorizedClientFilterConfig = this.route.snapshot.queryParams;
    if (routerQueryParams) {
      const queryParams = deepClone(routerQueryParams);
      let replaceUrl = false;
      if (routerQueryParams?.passwordProvidedList) {
        this.unauthorizedClientFilterConfig.passwordProvidedList = routerQueryParams?.passwordProvidedList;
        delete queryParams.passwordProvidedList;
        replaceUrl = true;
      }
      if (routerQueryParams?.tlsUsedList) {
        this.unauthorizedClientFilterConfig.tlsUsedList = routerQueryParams?.tlsUsedList;
        delete queryParams.tlsUsedList;
      }
      if (routerQueryParams?.clientId) {
        this.unauthorizedClientFilterConfig.clientId = routerQueryParams?.clientId;
        delete queryParams.clientId;
      }
      if (routerQueryParams?.username) {
        this.unauthorizedClientFilterConfig.username = routerQueryParams?.username;
        delete queryParams.username;
      }
      if (routerQueryParams?.ipAddress) {
        this.unauthorizedClientFilterConfig.ipAddress = routerQueryParams?.ipAddress;
        delete queryParams.ipAddress;
      }
      if (routerQueryParams?.reason) {
        this.unauthorizedClientFilterConfig.reason = routerQueryParams?.reason;
        delete queryParams.reason;
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
    const unauthorizedClientFilter = this.resolveUnauthorizedClientsFilter(this.unauthorizedClientFilterConfig);
    const query = new UnauthorizedClientQuery(pageLink, unauthorizedClientFilter);
    return this.unauthorizedClientService.getUnauthorizedClientV2(query);
  }

  private resolveUnauthorizedClientsFilter(sessionFilterConfig?: UnauthorizedClientFilterConfig): UnauthorizedClientFilterConfig {
    const unauthorizedClientFilter: UnauthorizedClientFilterConfig = {};
    if (sessionFilterConfig) {
      unauthorizedClientFilter.clientId = sessionFilterConfig.clientId;
      unauthorizedClientFilter.username = sessionFilterConfig.username;
      unauthorizedClientFilter.ipAddress = sessionFilterConfig.ipAddress;
      unauthorizedClientFilter.reason = sessionFilterConfig.reason;
      unauthorizedClientFilter.passwordProvidedList = sessionFilterConfig.passwordProvidedList;
      unauthorizedClientFilter.tlsUsedList = sessionFilterConfig.tlsUsedList;
    }
    return unauthorizedClientFilter;
  }

  private showReason($event: Event, content: string): boolean {
    if ($event) {
      $event.stopPropagation();
    }
    this.dialog.open<EventContentDialogComponent, EventContentDialogComponentDialogData>(EventContentDialogComponent, {
      disableClose: true,
      panelClass: ['tb-dialog', 'tb-fullscreen-dialog'],
      data: {
        content,
        title: 'unauthorized-client.reason',
        contentType: ContentType.TEXT,
        icon: 'error_outline'
      }
    });
    return false;
  }

  private deleteAllUnauthorizedClients($event: Event) {
    if ($event) {
      $event.stopPropagation();
    }
    this.dialogService.confirm(
      this.translate.instant('unauthorized-client.delete-unauthorized-clients-all-title'),
      this.translate.instant('unauthorized-client.delete-unauthorized-clients-all-text'),
      this.translate.instant('action.no'),
      this.translate.instant('action.yes'),
      true
    ).subscribe((result) => {
      if (result) {
        this.unauthorizedClientService.deleteAllUnauthorizedClients().subscribe(
          () => {
            this.getTable().updateData();
          }
        );
      }
    });
  }

  private passwordProvidedTooltip(entity: UnauthorizedClient): string {
    const tooltip = entity.passwordProvided ? 'unauthorized-client.password-provided-true' : 'unauthorized-client.password-provided-false';
    return this.translate.instant(tooltip);
  }

  private tlsUsedTooltip(entity: UnauthorizedClient): string {
    const tooltip = entity.tlsUsed ? 'unauthorized-client.tls-used-true' : 'unauthorized-client.tls-used-false';
    return this.translate.instant(tooltip);
  }
}
