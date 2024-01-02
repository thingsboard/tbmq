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

import {
  AfterViewInit,
  ChangeDetectionStrategy,
  Component,
  ComponentFactoryResolver,
  ElementRef,
  EventEmitter,
  Input,
  OnChanges,
  OnInit,
  SimpleChanges,
  ViewChild,
} from '@angular/core';
import { PageComponent } from '@shared/components/page.component';
import { Store } from '@ngrx/store';
import { AppState } from '@core/core.state';
import { MAX_SAFE_PAGE_SIZE, PageLink } from '@shared/models/page/page-link';
import { MatDialog } from '@angular/material/dialog';
import { MatPaginator } from '@angular/material/paginator';
import { MatSort } from '@angular/material/sort';
import { EntitiesDataSource } from '@home/models/datasource/entity-datasource';
import { catchError, debounceTime, distinctUntilChanged, map, tap } from 'rxjs/operators';
import { Direction, SortOrder } from '@shared/models/page/sort-order';
import { forkJoin, fromEvent, Observable, of, Subscription } from 'rxjs';
import { TranslateService } from '@ngx-translate/core';
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
import { DialogService } from '@core/services/dialog.service';
import { AddEntityDialogData, EntityAction } from '@home/models/entity/entity-component.models';
import { DomSanitizer, SafeHtml } from '@angular/platform-browser';
import { TbAnchorComponent } from '@shared/components/tb-anchor.component';
import { isDefined, isEqual, isUndefined } from '@core/utils';
import { BreakpointObserver } from '@angular/cdk/layout';
import { MediaBreakpoints } from '@shared/models/constants';
import { homePageTitleConfig, HomePageTitleType } from '@shared/models/home-page.model';
import { AddEntityDialogComponent } from '@home/components/entity/add-entity-dialog.component';
import { WsClientService } from '@core/http/ws-client.service';

@Component({
  selector: 'tb-ws-client-table',
  templateUrl: './ws-client-table.component.html',
  styleUrls: ['./ws-client-table.component.scss'],
  changeDetection: ChangeDetectionStrategy.OnPush
})
export class WsClientTableComponent extends PageComponent implements AfterViewInit, OnInit, OnChanges {

  @Input()
  entitiesTableConfig: EntityTableConfig<BaseData>;

  @Input()
  cardType: HomePageTitleType;

  translations: EntityTypeTranslation;

  headerActionDescriptors: Array<HeaderActionDescriptor>;
  groupActionDescriptors: Array<GroupActionDescriptor<BaseData>>;
  cellActionDescriptors: Array<CellActionDescriptor<BaseData>>;
  cellMoreActionDescriptors: Array<CellActionDescriptor<BaseData>>;
  cellHiddenActionDescriptors: Array<CellActionDescriptor<BaseData>>;

  actionColumns: Array<EntityActionTableColumn<BaseData>>;
  entityColumns: Array<EntityTableColumn<BaseData>>;

  displayedColumns: string[];

  headerCellStyleCache: Array<any> = [];

  cellContentCache: Array<SafeHtml> = [];
  cellTooltipCache: Array<string> = [];

  cellStyleCache: Array<any> = [];

  selectionEnabled: boolean;
  defaultPageSize = 5;
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
  hidePageSize: Observable<boolean>;

  @ViewChild('entityTableHeaderAnchor', {static: true}) entityTableHeaderAnchor: TbAnchorComponent;

  @ViewChild('searchInput') searchInputField: ElementRef;
  // @ViewChild(MatPaginator) paginator: MatPaginator;

  @ViewChild(MatSort) sort: MatSort;
  private sortSubscription: Subscription;
  private updateDataSubscription: Subscription;
  private viewInited = false;
  private homePageTitleResources = homePageTitleConfig;

  // TEST //
  dataArray;
  hasColorBadge: boolean;
  selectedRow = 0;

  constructor(protected store: Store<AppState>,
              public route: ActivatedRoute,
              public translate: TranslateService,
              public dialog: MatDialog,
              private dialogService: DialogService,
              private domSanitizer: DomSanitizer,
              private breakpointObserver: BreakpointObserver,
              private wsClientService: WsClientService,
              private router: Router,
              private componentFactoryResolver: ComponentFactoryResolver) {
    super(store);
  }

  ngOnInit() {
    if (this.entitiesTableConfig) {
      this.init(this.entitiesTableConfig);
    } else {
      this.init(this.route.snapshot.data.entitiesTableConfig);
    }
  }

  ngOnChanges(changes: SimpleChanges): void {
    for (const propName of Object.keys(changes)) {
      const change = changes[propName];
      if (!change.firstChange && change.currentValue !== change.previousValue) {
        if (propName === 'entitiesTableConfig' && change.currentValue) {
          this.init(change.currentValue);
        }
      }
    }
  }

  private init(entitiesTableConfig: EntityTableConfig<BaseData>) {
    this.isDetailsOpen = false;
    this.entitiesTableConfig = entitiesTableConfig;
    if (this.entitiesTableConfig.headerComponent) {
      const componentFactory = this.componentFactoryResolver.resolveComponentFactory(this.entitiesTableConfig.headerComponent);
      const viewContainerRef = this.entityTableHeaderAnchor.viewContainerRef;
      viewContainerRef.clear();
      const componentRef = viewContainerRef.createComponent(componentFactory);
      const headerComponent = componentRef.instance;
      headerComponent.entitiesTableConfig = this.entitiesTableConfig;
    }

    // @ts-ignore
    this.entitiesTableConfig.table = this;
    this.translations = this.entitiesTableConfig.entityTranslations;

    this.headerActionDescriptors = [...this.entitiesTableConfig.headerActionDescriptors];
    this.groupActionDescriptors = [...this.entitiesTableConfig.groupActionDescriptors];
    this.cellActionDescriptors = [...this.entitiesTableConfig.cellActionDescriptors];
    this.cellMoreActionDescriptors = [...this.entitiesTableConfig.cellMoreActionDescriptors];
    this.cellHiddenActionDescriptors = [...this.entitiesTableConfig.cellHiddenActionDescriptors];

    if (this.entitiesTableConfig.entitiesDeleteEnabled) {
      this.cellActionDescriptors.push(
        {
          name: this.translate.instant('action.delete'),
          icon: 'mdi:trash-can-outline',
          isEnabled: entity => this.entitiesTableConfig.deleteEnabled(entity),
          onAction: ($event, entity) => this.deleteEntity($event, entity)
        }
      );
      this.groupActionDescriptors.push(
        {
          name: this.translate.instant('action.delete'),
          icon: 'mdi:trash-can-outline',
          isEnabled: true,
          onAction: ($event, entities) => this.deleteEntities($event, entities)
        }
      );
    }

    const enabledGroupActionDescriptors =
      this.groupActionDescriptors.filter((descriptor) => descriptor.isEnabled);

    this.selectionEnabled = this.entitiesTableConfig.selectionEnabled; // && enabledGroupActionDescriptors.length;
    this.hasColorBadge = this.entitiesTableConfig.showColorBadge; // && enabledGroupActionDescriptors.length;

    this.columnsUpdated();

    let sortOrder: SortOrder = null;
    if (this.entitiesTableConfig.defaultSortOrder) {
      sortOrder = {
        property: this.entitiesTableConfig.defaultSortOrder.property,
        direction: this.entitiesTableConfig.defaultSortOrder.direction
      };
    }

    this.displayPagination = this.entitiesTableConfig.displayPagination;
    // this.hidePageSize = this.breakpointObserver.observe(MediaBreakpoints['xs']).pipe(map(({matches}) => matches));
    this.hidePageSize = of(true);
    this.defaultPageSize = 10;  //this.calcDefaultPageSize();
    this.pageSizeOptions = [5, 10, 20];
    this.pageLink = new PageLink(this.defaultPageSize, 0, null, sortOrder);
    this.pageLink.pageSize = this.displayPagination ? this.defaultPageSize : MAX_SAFE_PAGE_SIZE;
    this.dataSource = this.entitiesTableConfig.dataSource(this.dataLoaded.bind(this));
    this.dataSource.pageData$.subscribe(res => {
      this.dataArray = res.data;
    });
    if (this.entitiesTableConfig.onLoadAction) {
      this.entitiesTableConfig.onLoadAction(this.route);
    }
    if (this.entitiesTableConfig.loadDataOnInit) {
      this.dataSource.loadEntities(this.pageLink);
    }
    if (this.viewInited) {
      setTimeout(() => {
        this.updatePaginationSubscriptions();
      }, 0);
    }
  }

  ngAfterViewInit() {

    fromEvent(this.searchInputField.nativeElement, 'keyup')
      .pipe(
        debounceTime(150),
        distinctUntilChanged(),
        tap(() => {
          if (this.displayPagination) {
            // this.paginator.pageIndex = 0;
          }
          this.updateData();
        })
      )
      .subscribe();

    this.updatePaginationSubscriptions();
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
      // this.sortSubscription = this.sort.sortChange.subscribe(() => this.paginator.pageIndex = 0);
    }
    /*this.updateDataSubscription = ((this.displayPagination ? this.paginator.page : this.sort.sortChange) as Observable<any>)
      .pipe(
        tap(() => this.updateData())
      )
      .subscribe();*/
  }

  addEnabled() {
    return this.entitiesTableConfig.addEnabled;
  }

  updateData(closeDetails: boolean = true) {
    if (closeDetails) {
      this.isDetailsOpen = false;
    }
    if (this.displayPagination) {
      // this.pageLink.page = this.paginator.pageIndex;
      // this.pageLink.pageSize = this.paginator.pageSize;
    } else {
      this.pageLink.page = 0;
    }
    if (this.sort?.active) {
      /*this.pageLink.sortOrder = {
        property: this.sort.active,
        direction: Direction[this.sort.direction.toUpperCase()]
      };*/
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

  onRowClick($event: Event, entity, index) {
    this.selectedRow = index;
    if (!this.entitiesTableConfig.handleRowClick($event, entity)) {
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

  addEntity($event: Event) {
    let entity$: Observable<BaseData>;
    if (this.entitiesTableConfig.addEntity) {
      entity$ = this.entitiesTableConfig.addEntity();
    } else {
      entity$ = this.dialog.open<AddEntityDialogComponent, AddEntityDialogData<BaseData>,
        BaseData>(AddEntityDialogComponent, {
        disableClose: true,
        panelClass: ['tb-dialog', 'tb-fullscreen-dialog'],
        data: {
          entitiesTableConfig: this.entitiesTableConfig
        }
      }).afterClosed();
    }
    entity$.subscribe(
      (entity) => {
        if (entity) {
          this.updateData();
          this.entitiesTableConfig.entityAdded(entity);
        }
      }
    );
  }

  onEntityUpdated(entity: BaseData) {
    this.updateData(false);
    this.entitiesTableConfig.entityUpdated(entity);
  }

  onEntityAction(action: EntityAction<BaseData>) {
    if (action.action === 'delete') {
      this.deleteEntity(action.event, action.entity);
    }
  }

  deleteEntity($event: Event, entity: BaseData) {
    if ($event) {
      $event.stopPropagation();
    }
    this.dialogService.confirm(
      this.entitiesTableConfig.deleteEntityTitle(entity),
      this.entitiesTableConfig.deleteEntityContent(entity),
      this.translate.instant('action.no'),
      this.translate.instant('action.yes'),
      true
    ).subscribe((result) => {
      if (result) {
        this.entitiesTableConfig.deleteEntity(entity.id).subscribe(
          () => {
            this.updateData();
            this.entitiesTableConfig.entitiesDeleted([entity.id]);
          }
        );
      }
    });
  }

  deleteEntities($event: Event, entities: BaseData[]) {
    if ($event) {
      $event.stopPropagation();
    }
    this.dialogService.confirm(
      this.entitiesTableConfig.deleteEntitiesTitle(entities.length),
      this.entitiesTableConfig.deleteEntitiesContent(entities.length),
      this.translate.instant('action.no'),
      this.translate.instant('action.yes'),
      true
    ).subscribe((result) => {
      if (result) {
        const tasks: Observable<string>[] = [];
        entities.forEach((entity) => {
          if (this.entitiesTableConfig.deleteEnabled(entity)) {
            tasks.push(this.entitiesTableConfig.deleteEntity(entity.id).pipe(
              map(() => entity.id),
              catchError(() => of(null)
              )));
          }
        });
        forkJoin(tasks).subscribe(
          (ids) => {
            this.updateData();
            this.entitiesTableConfig.entitiesDeleted(ids.filter(id => id !== null));
          }
        );
      }
    });
  }

  enterFilterMode() {
    this.textSearchMode = true;
    this.pageLink.textSearch = '';
    setTimeout(() => {
      this.searchInputField.nativeElement.focus();
      this.searchInputField.nativeElement.setSelectionRange(0, 0);
    }, 10);
  }

  exitFilterMode() {
    const updateData = this.pageLink.textSearch.length;
    this.textSearchMode = false;
    this.pageLink.textSearch = null;
    if (this.displayPagination) {
      // this.paginator.pageIndex = 0;
    }
    if (updateData) {
      this.updateData();
    }
  }

  resetSortAndFilter(update: boolean = true, preserveTimewindow: boolean = false) {
    this.textSearchMode = false;
    this.pageLink.textSearch = null;
    if (this.displayPagination) {
      // this.paginator.pageIndex = 0;
    }
    const sortable = this.sort.sortables.get(this.entitiesTableConfig.defaultSortOrder.property);
    this.sort.active = sortable.id;
    this.sort.direction = this.entitiesTableConfig.defaultSortOrder.direction === Direction.ASC ? 'asc' : 'desc';
    if (update) {
      this.updatedRouterParamsAndData({}, '');
    }
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
    this.entityColumns = this.entitiesTableConfig.columns.filter(
      (column) => column instanceof EntityTableColumn)
      .map(column => column as EntityTableColumn<BaseData>);
    this.actionColumns = this.entitiesTableConfig.columns.filter(
      (column) => column instanceof EntityActionTableColumn)
      .map(column => column as EntityActionTableColumn<BaseData>);

    this.displayedColumns = [];

    /*if (this.selectionEnabled) {
      this.displayedColumns.push('select');
    }*/
    this.entitiesTableConfig.columns.forEach(
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
    const index = this.entitiesTableConfig.columns.indexOf(column);
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
    const index = row * this.entitiesTableConfig.columns.length + col;
    this.cellContentCache[index] = undefined;
    this.cellTooltipCache[index] = undefined;
    this.cellStyleCache[index] = undefined;
  }

  cellContent(entity: BaseData, column: EntityColumn<BaseData>, row: number) {
    if (column instanceof EntityTableColumn) {
      const col = this.entitiesTableConfig.columns.indexOf(column);
      const index = row * this.entitiesTableConfig.columns.length + col;
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
      const col = this.entitiesTableConfig.columns.indexOf(column);
      const index = row * this.entitiesTableConfig.columns.length + col;
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
    const col = this.entitiesTableConfig.columns.indexOf(column);
    const index = row * this.entitiesTableConfig.columns.length + col;
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

  trackByColumnKey(index, column: EntityTableColumn<BaseData>) {
    return column.key;
  }

  trackByEntityId(index: number, entity: any) {
    return entity.id;
  }

  calcTableHeight(): string {
    return this.breakpointObserver.isMatched(MediaBreakpoints['gt-xxl']) ? '300px' : '34vh';
  }

  navigate() {
    this.router.navigate([this.homePageTitleResources.get(this.cardType).link]);
  }

  private calcDefaultPageSize(): number {
    return this.breakpointObserver.isMatched(MediaBreakpoints['gt-xxl']) ? 15 : 5;
  }

  onConnect(connection) {

  }

  editConnection(connection) {

  }

  deleteConnection(connection) {

  }

  onCommentMouseEnter(displayDataIndex: number): void {
    this.dataArray[displayDataIndex].showActions = true;
  }

  onCommentMouseLeave(displayDataIndex: number): void {
    this.dataArray[displayDataIndex].showActions = false;
  }
}
