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
  AfterViewInit,
  ChangeDetectionStrategy,
  Component,
  ComponentFactoryResolver,
  ElementRef,
  EventEmitter,
  OnInit,
  model,
  viewChild
} from '@angular/core';
import { PageComponent } from '@shared/components/page.component';
import { Store } from '@ngrx/store';
import { AppState } from '@core/core.state';
import { MAX_SAFE_PAGE_SIZE, PageLink } from '@shared/models/page/page-link';
import { MatDialog } from '@angular/material/dialog';
import { MatPaginator } from '@angular/material/paginator';
import { MatSort, MatSortHeader } from '@angular/material/sort';
import { EntitiesDataSource } from '@home/models/datasource/entity-datasource';
import { tap } from 'rxjs/operators';
import { Direction, SortOrder } from '@shared/models/page/sort-order';
import { merge, Observable, Subscription } from 'rxjs';
import { TranslateService, TranslateModule } from '@ngx-translate/core';
import { BaseData } from '@shared/models/base-data';
import { ActivatedRoute, QueryParamsHandling, Router } from '@angular/router';
import {
  CellActionDescriptor,
  CellActionDescriptorType,
  EntityActionTableColumn,
  EntityColumn,
  EntityTableColumn,
  EntityTableConfig,
  GroupActionDescriptor,
  HeaderActionDescriptor
} from '@home/models/entity/entities-table-config.models';
import { EntityTypeTranslation } from '@shared/models/entity-type.models';
import { DomSanitizer, SafeHtml } from '@angular/platform-browser';
import { TbAnchorComponent } from '@shared/components/tb-anchor.component';
import { isDefined, isEqual, isUndefined } from '@core/utils';
import { MqttJsClientService } from '@core/http/mqtt-js-client.service';
import { BreakpointObserver } from '@angular/cdk/layout';
import { MediaBreakpoints } from '@shared/models/constants';
import { MatTable, MatColumnDef, MatHeaderCellDef, MatHeaderCell, MatCellDef, MatCell, MatHeaderRowDef, MatHeaderRow, MatRowDef, MatRow } from '@angular/material/table';
import { MatCheckbox } from '@angular/material/checkbox';
import { NgTemplateOutlet, AsyncPipe } from '@angular/common';
import { MatTooltip } from '@angular/material/tooltip';
import { CopyButtonComponent } from '@shared/components/button/copy-button.component';
import { MatIconButton } from '@angular/material/button';
import { TbIconComponent } from '@shared/components/icon.component';
import { MatMenuTrigger, MatMenu, MatMenuItem } from '@angular/material/menu';
import { MatIcon } from '@angular/material/icon';

@Component({
    selector: 'tb-entities-table-ws',
    templateUrl: './entities-table-ws.component.html',
    styleUrls: ['./entities-table-ws.component.scss'],
    changeDetection: ChangeDetectionStrategy.OnPush,
    imports: [MatTable, MatSort, MatColumnDef, MatHeaderCellDef, MatHeaderCell, MatCheckbox, MatCellDef, MatCell, MatSortHeader, MatTooltip, CopyButtonComponent, MatIconButton, TbIconComponent, MatMenuTrigger, MatIcon, MatMenu, MatMenuItem, MatHeaderRowDef, MatHeaderRow, MatRowDef, MatRow, NgTemplateOutlet, MatPaginator, TranslateModule, AsyncPipe]
})
export class EntitiesTableWsComponent extends PageComponent implements AfterViewInit, OnInit {

  entitiesTableConfig = model<EntityTableConfig<BaseData>>();

  translations: EntityTypeTranslation;

  headerActionDescriptors: Array<HeaderActionDescriptor>;
  groupActionDescriptors: Array<GroupActionDescriptor<BaseData>>;
  cellActionDescriptors: Array<CellActionDescriptor<BaseData>>;

  actionColumns: Array<EntityActionTableColumn<BaseData>>;
  entityColumns: Array<EntityTableColumn<BaseData>>;

  displayedColumns: string[];

  headerCellStyleCache: Array<any> = [];

  cellContentCache: Array<SafeHtml> = [];
  cellTooltipCache: Array<string> = [];

  cellStyleCache: Array<any> = [];

  selectionEnabled;

  defaultPageSize = 10;
  displayPagination = true;
  pageSizeOptions;
  pageLink: PageLink;
  pageMode = true;
  textSearchMode = false;
  dataSource: EntitiesDataSource<BaseData>;

  cellActionType = CellActionDescriptorType;

  isDetailsOpen = false;
  detailsPanelOpened = new EventEmitter<boolean>();
  isFullscreen = false;
  hidePageSize = false;

  readonly entityTableHeaderAnchor = viewChild<TbAnchorComponent>('entityTableHeaderAnchor');
  readonly searchInputField = viewChild<ElementRef>('searchInput');
  readonly paginator = viewChild(MatPaginator);
  readonly sort = viewChild(MatSort);

  private sortSubscription: Subscription;
  private updateDataSubscription: Subscription;
  private viewInited = false;

  constructor(protected store: Store<AppState>,
              public route: ActivatedRoute,
              public translate: TranslateService,
              public dialog: MatDialog,
              private breakpointObserver: BreakpointObserver,
              private domSanitizer: DomSanitizer,
              private mqttJsClientService: MqttJsClientService,
              private router: Router,
              private componentFactoryResolver: ComponentFactoryResolver) {
    super(store);
  }

  ngOnInit() {
    const entitiesTableConfig = this.entitiesTableConfig();
    if (entitiesTableConfig) {
      this.init(entitiesTableConfig);
    } else {
      this.init(this.route.snapshot.data.entitiesTableConfig);
    }
  }

  private init(entitiesTableConfig: EntityTableConfig<BaseData>) {
    this.isDetailsOpen = false;
    this.entitiesTableConfig.set(entitiesTableConfig);
    const entitiesTableConfigValue = this.entitiesTableConfig();
    if (entitiesTableConfigValue.headerComponent) {
      const componentFactory = this.componentFactoryResolver.resolveComponentFactory(entitiesTableConfigValue.headerComponent);
      const viewContainerRef = this.entityTableHeaderAnchor().viewContainerRef;
      viewContainerRef.clear();
      const componentRef = viewContainerRef.createComponent(componentFactory);
      const headerComponent = componentRef.instance;
      headerComponent.entitiesTableConfig = entitiesTableConfigValue;
    }

    // @ts-ignore
    entitiesTableConfigValue.table = this;
    this.translations = entitiesTableConfigValue.entityTranslations;

    this.headerActionDescriptors = [...entitiesTableConfigValue.headerActionDescriptors];
    this.groupActionDescriptors = [...entitiesTableConfigValue.groupActionDescriptors];
    this.cellActionDescriptors = [...entitiesTableConfigValue.cellActionDescriptors];

    const enabledGroupActionDescriptors =
      this.groupActionDescriptors.filter((descriptor) => descriptor.isEnabled);

    this.selectionEnabled = entitiesTableConfigValue.selectionEnabled && enabledGroupActionDescriptors.length;

    this.columnsUpdated();

    let sortOrder: SortOrder = null;
    if (entitiesTableConfigValue.defaultSortOrder) {
      sortOrder = {
        property: entitiesTableConfigValue.defaultSortOrder.property,
        direction: entitiesTableConfigValue.defaultSortOrder.direction
      };
    }

    this.displayPagination = entitiesTableConfigValue.displayPagination;
    this.defaultPageSize = this.calcDefaultPageSize();
    this.pageSizeOptions = [3, 10, 15, 30];
    this.pageLink = new PageLink(10, 0, null, sortOrder);
    this.pageLink.pageSize = this.displayPagination ? this.defaultPageSize : MAX_SAFE_PAGE_SIZE;
    this.dataSource = entitiesTableConfigValue.dataSource(this.dataLoaded.bind(this));
    if (entitiesTableConfigValue.onLoadAction) {
      entitiesTableConfigValue.onLoadAction(this.route);
    }
    if (entitiesTableConfigValue.loadDataOnInit) {
      this.dataSource.loadEntities(this.pageLink);
    }
    if (this.viewInited) {
      setTimeout(() => {
        this.updatePaginationSubscriptions();
      }, 0);
    }
  }

  ngAfterViewInit() {
    this.mqttJsClientService.connection$.subscribe(
      () => {
        this.paginator().pageIndex = 0;
        this.init(this.entitiesTableConfig());
      }
    );
    this.updatePaginationSubscriptions();
    this.updateData();
    this.viewInited = true;
  }

  private updatePaginationSubscriptions() {
    if (this.sortSubscription) {
      this.sortSubscription.unsubscribe();
      this.sortSubscription = null;
    }
    if (this.updateDataSubscription) {
      this.updateDataSubscription.unsubscribe();
      this.updateDataSubscription = null;
    }
    if (this.displayPagination) {
      this.sortSubscription = this.sort().sortChange.subscribe(() => this.paginator().pageIndex = 0);
    }
    this.updateDataSubscription = ((this.displayPagination ? merge(this.sort().sortChange, this.paginator().page)
      : this.sort().sortChange) as Observable<any>)
      .pipe(
        tap(() => this.updateData())
      )
      .subscribe();
  }

  updateData(closeDetails: boolean = true) {
    if (closeDetails) {
      this.isDetailsOpen = false;
    }
    if (this.displayPagination) {
      this.pageLink.page = this.paginator().pageIndex;
      this.pageLink.pageSize = this.paginator().pageSize;
    } else {
      this.pageLink.page = 0;
    }
    const sort = this.sort();
    if (sort.active) {
      this.pageLink.sortOrder = {
        property: sort.active,
        direction: Direction[sort.direction.toUpperCase()]
      };
    } else {
      this.pageLink.sortOrder = null;
    }
    this.dataSource.loadEntities(this.pageLink);
  }

  private dataLoaded(col?: number, row?: number) {
    if (isFinite(col) && isFinite(row)) {
      this.clearCellCache(col, row);
    } else {
      this.headerCellStyleCache.length = 0;
      this.cellContentCache.length = 0;
      this.cellTooltipCache.length = 0;
      this.cellStyleCache.length = 0;
    }
  }

  onRowClick($event: Event, entity) {
    if (!this.entitiesTableConfig().handleRowClick($event, entity)) {
      this.toggleEntityDetails($event, entity);
    }
  }

  toggleEntityDetails($event: Event, entity) {
    if ($event) {
      $event.stopPropagation();
    }
    if (this.dataSource.toggleCurrentEntity(entity)) {
      this.isDetailsOpen = true;
    } else {
      this.isDetailsOpen = !this.isDetailsOpen;
    }
    this.detailsPanelOpened.emit(this.isDetailsOpen);
  }

  protected updatedRouterParamsAndData(queryParams: object, queryParamsHandling: QueryParamsHandling = 'merge') {
    if (this.pageMode) {
      this.router.navigate([], {
        relativeTo: this.route,
        queryParams,
        queryParamsHandling
      });
      if (queryParamsHandling === '' && isEqual(this.route.snapshot.queryParams, queryParams)) {
        this.updateData();
      }
    } else {
      this.updateData();
    }
  }

  columnsUpdated(resetData: boolean = false) {
    this.entityColumns = this.entitiesTableConfig().columns.filter(
      (column) => column instanceof EntityTableColumn)
      .map(column => column as EntityTableColumn<BaseData>);
    this.actionColumns = this.entitiesTableConfig().columns.filter(
      (column) => column instanceof EntityActionTableColumn)
      .map(column => column as EntityActionTableColumn<BaseData>);

    this.displayedColumns = [];

    if (this.selectionEnabled) {
      this.displayedColumns.push('select');
    }
    this.entitiesTableConfig().columns.forEach(
      (column) => {
        this.displayedColumns.push(column.key);
      }
    );
    this.displayedColumns.push('actions');
    this.headerCellStyleCache.length = 0;
    this.cellContentCache.length = 0;
    this.cellTooltipCache.length = 0;
    this.cellStyleCache.length = 0;
    if (resetData) {
      this.dataSource.reset();
    }
  }

  headerCellStyle(column: EntityColumn<BaseData>) {
    const index = this.entitiesTableConfig().columns.indexOf(column);
    let res = this.headerCellStyleCache[index];
    if (!res) {
      const widthStyle: any = {width: column.width};
      if (column.width !== '0px') {
        widthStyle.minWidth = column.width;
        widthStyle.maxWidth = column.width;
      }
      if (column instanceof EntityTableColumn) {
        res = {...column.headerCellStyleFunction(column.key), ...widthStyle};
      } else {
        res = widthStyle;
      }
      this.headerCellStyleCache[index] = res;
    }
    return res;
  }

  clearCellCache(col: number, row: number) {
    const index = row * this.entitiesTableConfig().columns.length + col;
    this.cellContentCache[index] = undefined;
    this.cellTooltipCache[index] = undefined;
    this.cellStyleCache[index] = undefined;
  }

  cellContent(entity: BaseData, column: EntityColumn<BaseData>, row: number) {
    if (column instanceof EntityTableColumn) {
      const col = this.entitiesTableConfig().columns.indexOf(column);
      const index = row * this.entitiesTableConfig().columns.length + col;
      let res = this.cellContentCache[index];
      if (isUndefined(res)) {
        res = this.domSanitizer.bypassSecurityTrustHtml(column.cellContentFunction(entity, column.key));
        this.cellContentCache[index] = res;
      }
      return res;
    } else {
      return '';
    }
  }

  cellTooltip(entity: BaseData, column: EntityColumn<BaseData>, row: number) {
    if (column instanceof EntityTableColumn) {
      const col = this.entitiesTableConfig().columns.indexOf(column);
      const index = row * this.entitiesTableConfig().columns.length + col;
      let res = this.cellTooltipCache[index];
      if (isUndefined(res)) {
        res = column.cellTooltipFunction(entity, column.key);
        res = isDefined(res) ? res : null;
        this.cellTooltipCache[index] = res;
      } else {
        return res !== null ? res : undefined;
      }
    } else {
      return undefined;
    }
  }

  cellStyle(entity: BaseData, column: EntityColumn<BaseData>, row: number) {
    const col = this.entitiesTableConfig().columns.indexOf(column);
    const index = row * this.entitiesTableConfig().columns.length + col;
    let res = this.cellStyleCache[index];
    if (!res) {
      const widthStyle: any = {width: column.width};
      if (column.width !== '0px') {
        widthStyle.minWidth = column.width;
        widthStyle.maxWidth = column.width;
      }
      if (column instanceof EntityTableColumn) {
        res = {...column.cellStyleFunction(entity, column.key), ...widthStyle};
      } else {
        res = widthStyle;
      }
      this.cellStyleCache[index] = res;
    }
    return res;
  }

  private calcDefaultPageSize(): number {
    if (this.breakpointObserver.isMatched(MediaBreakpoints['gt-xxl'])) {
      return 15;
    }
    if (this.breakpointObserver.isMatched(MediaBreakpoints['gt-md'])) {
      return 10;
    }
    return 3;
  }
}
