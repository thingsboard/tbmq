///
/// Copyright © 2016-2023 The Thingsboard Authors
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
import { EntitiesDataSource, EntitiesFetchFunction } from '@home/models/datasource/entity-datasource';
import { Observable, of } from 'rxjs';
import { emptyPageData } from '@shared/models/page/page-data';
import { DatePipe } from '@angular/common';
import { Direction, SortOrder } from '@shared/models/page/sort-order';
import { EntityType, EntityTypeResource, EntityTypeTranslation } from '@shared/models/entity-type.models';
import { EntityComponent } from '@home/components/entity/entity.component';
import { Type } from '@angular/core';
import { EntityAction } from './entity-component.models';
import { PageLink } from '@shared/models/page/page-link';
import { EntityTableHeaderComponent } from '@home/components/entity/entity-table-header.component';
import { ActivatedRoute } from '@angular/router';
import { EntityTabsComponent } from '../../components/entity/entity-tabs.component';
import { ClientType } from '@shared/models/client.model';
import { IEntitiesTableComponent } from './entity-table-component.models';
import { DAY, historyInterval } from "@shared/models/time/time.models";

export type EntityBooleanFunction<T extends BaseData> = (entity: T) => boolean;
export type EntityStringFunction<T extends BaseData> = (entity: T) => string;
export type EntityVoidFunction<T extends BaseData> = (entity: T) => void;
export type EntityIdsVoidFunction<T extends BaseData> = (ids: string[]) => void;
export type EntityCountStringFunction = (count: number) => string;
export type EntityTwoWayOperation<T extends BaseData> = (entity: T, originalEntity?: T) => Observable<T>;
export type EntityByIdOperation<T extends BaseData> = (id: string) => Observable<T>;
export type EntityIdOneWayOperation = (id: string) => Observable<any>;
export type EntityActionFunction<T extends BaseData> = (action: EntityAction<T>) => boolean;
export type CreateEntityOperation<T extends BaseData> = () => Observable<T>;
export type EntityRowClickFunction<T extends BaseData> = (event: Event, entity: T) => boolean;

export type CellContentFunction<T extends BaseData> = (entity: T, key: string) => string;
export type CellTooltipFunction<T extends BaseData> = (entity: T, key: string) => string | undefined;
export type HeaderCellStyleFunction<T extends BaseData> = (key: string) => object;
export type CellStyleFunction<T extends BaseData> = (entity: T, key: string) => object;
export type CopyCellContent<T extends BaseData> = (entity: T, key: string, length: number) => object;

export enum CellActionDescriptorType { 'DEFAULT', 'COPY_BUTTON'}

export interface CellActionDescriptor<T extends BaseData> {
  name: string;
  nameFunction?: (entity: T) => string;
  icon?: string;
  mdiIcon?: string;
  style?: any;
  isEnabled: (entity: T) => boolean;
  onAction: ($event: MouseEvent, entity: T) => any;
  type?: CellActionDescriptorType;
}

export interface GroupActionDescriptor<T extends BaseData> {
  name: string;
  icon: string;
  isMdiIcon?: boolean;
  isEnabled: boolean;
  onAction: ($event: MouseEvent, entities: T[]) => void;
}

export interface HeaderActionDescriptor {
  name: string;
  icon: string;
  isMdiIcon?: boolean;
  isEnabled: () => boolean;
  onAction: ($event: MouseEvent) => void;
}

export type EntityTableColumnType = 'content' | 'action';

export class BaseEntityTableColumn<T extends BaseData> {
  constructor(public type: EntityTableColumnType,
              public key: string,
              public title: string,
              public width: string = '0px',
              public sortable: boolean = true,
              public ignoreTranslate: boolean = false,
              public mobileHide: boolean = false) {
  }
}

export class EntityTableColumn<T extends BaseData> extends BaseEntityTableColumn<T> {
  constructor(public key: string,
              public title: string,
              public width: string = '0px',
              public cellContentFunction: CellContentFunction<T> = (entity, property) => entity[property] ? entity[property] : '',
              public cellStyleFunction: CellStyleFunction<T> = () => ({}),
              public sortable: boolean = true,
              public headerCellStyleFunction: HeaderCellStyleFunction<T> = () => ({}),
              public cellTooltipFunction: CellTooltipFunction<T> = () => undefined,
              public isNumberColumn: boolean = false,
              public actionCell: CellActionDescriptor<T> = null) {
    super('content', key, title, width, sortable);
  }
}

export class EntityActionTableColumn<T extends BaseData> extends BaseEntityTableColumn<T> {
  constructor(public key: string,
              public title: string,
              public actionDescriptor: CellActionDescriptor<T>,
              public width: string = '0px') {
    super('action', key, title, width, false);
  }
}

export class DateEntityTableColumn<T extends BaseData> extends EntityTableColumn<T> {
  constructor(key: string,
              title: string,
              datePipe: DatePipe,
              width: string = '0px',
              dateFormat: string = 'yyyy-MM-dd HH:mm:ss',
              cellStyleFunction: CellStyleFunction<T> = () => ({})) {
    super(key,
      title,
      width,
      (entity, property) => {
        if (entity[property] === 0) {
          return '';
        }
        return datePipe.transform(entity[property], dateFormat);
      },
      cellStyleFunction);
  }
}

export type EntityColumn<T extends BaseData> = EntityTableColumn<T> | EntityActionTableColumn<T>;

export class EntityTableConfig<T extends BaseData, P extends PageLink = PageLink, L extends BaseData = T> {

  constructor() {
  }

  private table: IEntitiesTableComponent = null;

  componentsData: any = null;
  demoData: any = null;

  loadDataOnInit = true;
  onLoadAction: (route: ActivatedRoute) => void = null;
  useTimePageLink = false;
  forAllTimeEnabled = false;
  rowPointer = false;
  defaultTimewindowInterval = historyInterval(DAY);
  entityType: EntityType = null;
  tableTitle = '';
  selectionEnabled = true;
  defaultCursor = false;
  searchEnabled = true;
  addEnabled = true;
  entitiesDeleteEnabled = true;
  detailsPanelEnabled = true;
  hideDetailsTabsOnEdit = true;
  actionsColumnTitle = null;
  entityTranslations: EntityTypeTranslation;
  entityResources: EntityTypeResource<T>;
  entityComponent: Type<EntityComponent<T, P, L>>;
  entityTabsComponent: Type<EntityTabsComponent<T, P, L>>;
  addDialogStyle = {};
  defaultSortOrder: SortOrder = {property: 'createdTime', direction: Direction.DESC};
  displayPagination = true;
  pageMode = true;
  defaultPageSize = 10;
  columns: Array<EntityColumn<L>> = [];
  cellActionDescriptors: Array<CellActionDescriptor<L>> = [];
  groupActionDescriptors: Array<GroupActionDescriptor<L>> = [];
  headerActionDescriptors: Array<HeaderActionDescriptor> = [];
  addActionDescriptors: Array<HeaderActionDescriptor> = [];
  headerComponent: Type<EntityTableHeaderComponent<T, P, L>>;
  addEntity: CreateEntityOperation<T> = null;
  dataSource: (dataLoadedFunction: (col?: number, row?: number) => void)
    => EntitiesDataSource<L> = (dataLoadedFunction: (col?: number, row?: number) => void) => {
    return new EntitiesDataSource(this.entitiesFetchFunction, this.entitySelectionEnabled, dataLoadedFunction);
  }
  detailsReadonly: EntityBooleanFunction<T> = () => false;
  entitySelectionEnabled: EntityBooleanFunction<L> = () => true;
  deleteEnabled: EntityBooleanFunction<T | L> = () => true;
  deleteEntityTitle: EntityStringFunction<L> = () => '';
  deleteEntityContent: EntityStringFunction<L> = () => '';
  deleteEntitiesTitle: EntityCountStringFunction = () => '';
  deleteEntitiesContent: EntityCountStringFunction = () => '';
  loadEntity: EntityByIdOperation<T | L> = () => of();
  saveEntity: EntityTwoWayOperation<T> = (entity, originalEntity) => of(entity);
  deleteEntity: EntityIdOneWayOperation = () => of();
  entitiesFetchFunction: EntitiesFetchFunction<L, P> = () => of(emptyPageData<L>());
  onEntityAction: EntityActionFunction<T> = () => false;
  handleRowClick: EntityRowClickFunction<L> = () => false;
  entityTitle: EntityStringFunction<T> = () => '';
  entityAdded: EntityVoidFunction<T> = () => {};
  entityUpdated: EntityVoidFunction<T> = () => {};
  entitiesDeleted: EntityIdsVoidFunction<T> = () => {};

  getTable(): IEntitiesTableComponent {
    return this.table;
  }

  setTable(table: IEntitiesTableComponent) {
    this.table = table;
    // this.entityDetailsPage = null;
  }

  /*getEntityDetailsPage(): IEntityDetailsPageComponent {
    return this.entityDetailsPage;
  }

  setEntityDetailsPage(entityDetailsPage: IEntityDetailsPageComponent) {
    this.entityDetailsPage = entityDetailsPage;
    this.table = null;
  }*/

  updateData(closeDetails = false) {
    if (this.table) {
      this.table.updateData(closeDetails);
    }
    // else if (this.entityDetailsPage) {
    //   this.entityDetailsPage.reload();
    // }
  }

  toggleEntityDetails($event: Event, entity: T) {
    if (this.table) {
      this.table.toggleEntityDetails($event, entity);
    }
  }

  isDetailsOpen(): boolean {
    if (this.table) {
      return this.table.isDetailsOpen;
    } else {
      return false;
    }
  }

  getActivatedRoute(): ActivatedRoute {
    if (this.table) {
      return this.table.route;
    } else {
      return null;
    }
  }
}

export const checkBoxCell =
  (value: boolean): string => `<mat-icon class="material-icons mat-icon">${value ? 'check_box' : 'check_box_outline_blank'}</mat-icon>`;


export const defaultCellStyle =
  (value: string | number) => '<span style="background: rgba(111, 116, 242, 0.07); border-radius: 16px; padding: 4px 8px;">' + value + '</span>';

export const credetialsTypeCell = (value: string) => {
  const color = value === 'Basic' ? 'rgba(111,116,242,0.07)' : 'rgba(139,242,111,0.07)';
  return `<span style="background: ${color}; border-radius: 16px; padding: 4px 8px;">${value}</span>`;
}

export const clientTypeCell = (value: ClientType) => {
  const icon = value.toUpperCase() === ClientType.DEVICE ? 'devices_other' : 'desktop_mac';
  const color = value.toUpperCase() === ClientType.DEVICE ? 'rgba(1, 116, 242, 0.1)' : 'rgba(111, 1, 242, 0.1)';
  return `<span style="background: ${color}; border-radius: 16px; padding: 4px 8px; white-space: nowrap"><mat-icon style="height: 18px; font-size: 20px;padding-right: 4px" class="material-icons mat-icon">${icon}</mat-icon>${value}</span>`;
}

export const clientTypeWarning =
  (value: string) => `<span style="background: rgba(255,236,128,0); border-radius: 16px; padding: 4px 8px; white-space: nowrap"><mat-icon style="height: 18px; font-size: 20px; padding-right: 4px; color: #ff9a00" class="material-icons mat-icon">warning</mat-icon>${value}</span>`;
