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
  ChangeDetectorRef,
  Component,
  ComponentFactoryResolver,
  ElementRef,
  EventEmitter,
  OnChanges,
  OnInit,
  SimpleChanges,
  model,
  viewChild
} from '@angular/core';
import { PageComponent } from '@shared/components/page.component';
import { Store } from '@ngrx/store';
import { AppState } from '@core/core.state';
import { MAX_SAFE_PAGE_SIZE, PageLink, TimePageLink } from '@shared/models/page/page-link';
import { MatDialog } from '@angular/material/dialog';
import { MatPaginator } from '@angular/material/paginator';
import { MatSort, MatSortHeader } from '@angular/material/sort';
import { EntitiesDataSource } from '@home/models/datasource/entity-datasource';
import { catchError, debounceTime, distinctUntilChanged, map, takeUntil, tap } from 'rxjs/operators';
import { Direction, SortOrder } from '@shared/models/page/sort-order';
import { forkJoin, merge, Observable, of, Subject, Subscription } from 'rxjs';
import { TranslateService, TranslateModule } from '@ngx-translate/core';
import { BaseData } from '@shared/models/base-data';
import { ActivatedRoute, QueryParamsHandling, Router } from '@angular/router';
import {
  CellActionDescriptor,
  CellActionDescriptorType, ChipsTableColumn,
  EntityActionTableColumn,
  EntityColumn,
  EntityTableColumn,
  EntityTableConfig,
  GroupActionDescriptor,
  HeaderActionDescriptor
} from '@home/models/entity/entities-table-config.models';
import { EntityTypeTranslation } from '@shared/models/entity-type.models';
import { DialogService } from '@core/services/dialog.service';
import { AddEntityDialogComponent } from './add-entity-dialog.component';
import { AddEntityDialogData, EntityAction } from '@home/models/entity/entity-component.models';
import { DomSanitizer, SafeHtml } from '@angular/platform-browser';
import { TbAnchorComponent } from '@shared/components/tb-anchor.component';
import { isDefined, isEqual, isNotEmptyStr, isUndefined } from '@core/utils';
import { calculateIntervalStartEndTime, HistoryWindowType, Timewindow } from '@shared/models/time/time.models';
import { UntypedFormBuilder, FormsModule, ReactiveFormsModule } from '@angular/forms';
import { MatDrawerContainer, MatDrawer, MatDrawerContent } from '@angular/material/sidenav';
import { NgClass, NgStyle, AsyncPipe } from '@angular/common';
import { EntityDetailsPanelComponent } from './entity-details-panel.component';
import { FlexModule } from '@angular/flex-layout/flex';
import { MatToolbar } from '@angular/material/toolbar';
import { ExtendedModule } from '@angular/flex-layout/extended';
import { TimewindowComponent } from '@shared/components/time/timewindow.component';
import { MatIconButton } from '@angular/material/button';
import { MatTooltip } from '@angular/material/tooltip';
import { MatIcon } from '@angular/material/icon';
import { MatMenuTrigger, MatMenu, MatMenuItem } from '@angular/material/menu';
import { TbIconComponent } from '@shared/components/icon.component';
import { MatFormField, MatLabel } from '@angular/material/form-field';
import { MatInput } from '@angular/material/input';
import { MatTable, MatColumnDef, MatHeaderCellDef, MatHeaderCell, MatCellDef, MatCell, MatHeaderRowDef, MatHeaderRow, MatRowDef, MatRow } from '@angular/material/table';
import { MatCheckbox } from '@angular/material/checkbox';
import { CopyButtonComponent } from '@shared/components/button/copy-button.component';
import { MatChipSet, MatChip } from '@angular/material/chips';
import { MatDivider } from '@angular/material/divider';

@Component({
    selector: 'tb-entities-table',
    templateUrl: './entities-table.component.html',
    styleUrls: ['./entities-table.component.scss'],
    changeDetection: ChangeDetectionStrategy.OnPush,
    imports: [MatDrawerContainer, MatDrawer, EntityDetailsPanelComponent, MatDrawerContent, FlexModule, MatToolbar, ExtendedModule, TbAnchorComponent, TimewindowComponent, FormsModule, MatIconButton, MatTooltip, MatIcon, MatMenuTrigger, MatMenu, MatMenuItem, TbIconComponent, MatFormField, MatLabel, MatInput, ReactiveFormsModule, MatTable, MatSort, MatColumnDef, MatHeaderCellDef, MatHeaderCell, MatCheckbox, MatCellDef, MatCell, MatSortHeader, NgClass, NgStyle, CopyButtonComponent, MatChipSet, MatChip, MatHeaderRowDef, MatHeaderRow, MatRowDef, MatRow, MatDivider, MatPaginator, AsyncPipe, TranslateModule]
})
export class EntitiesTableComponent extends PageComponent implements AfterViewInit, OnInit, OnChanges {

  entitiesTableConfig = model<EntityTableConfig<BaseData>>();

  translations: EntityTypeTranslation;

  headerActionDescriptors: Array<HeaderActionDescriptor>;
  groupActionDescriptors: Array<GroupActionDescriptor<BaseData>>;
  cellActionDescriptors: Array<CellActionDescriptor<BaseData>>;

  actionColumns: Array<EntityActionTableColumn<BaseData>>;
  entityColumns: Array<EntityTableColumn<BaseData>>;
  chipsColumns: Array<ChipsTableColumn<BaseData>>;

  displayedColumns: string[];

  headerCellStyleCache: Array<any> = [];

  cellContentCache: Array<SafeHtml> = [];
  cellTooltipCache: Array<string> = [];

  cellStyleCache: Array<any> = [];

  selectionEnabled;
  defaultCursor;

  defaultPageSize = 10;
  displayPagination = true;
  pageSizeOptions;
  pageLink: PageLink;
  pageMode = true;
  textSearchMode = false;
  hidePageSize = false;
  timewindow: Timewindow;
  dataSource: EntitiesDataSource<BaseData>;

  cellActionType = CellActionDescriptorType;

  isDetailsOpen = false;
  detailsPanelOpened = new EventEmitter<boolean>();

  readonly entityTableHeaderAnchor = viewChild<TbAnchorComponent>('entityTableHeader');
  readonly searchInputField = viewChild<ElementRef>('searchInput');
  readonly paginator = viewChild(MatPaginator);
  readonly sort = viewChild(MatSort);

  textSearch = this.fb.control('', {nonNullable: true});

  private sortSubscription: Subscription;
  private updateDataSubscription: Subscription;
  private viewInited = false;
  private destroy$ = new Subject<void>();

  constructor(protected store: Store<AppState>,
              public route: ActivatedRoute,
              public translate: TranslateService,
              public dialog: MatDialog,
              private dialogService: DialogService,
              private domSanitizer: DomSanitizer,
              private cd: ChangeDetectorRef,
              private router: Router,
              private componentFactoryResolver: ComponentFactoryResolver,
              private fb: UntypedFormBuilder) {
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
    this.entitiesTableConfig.set(entitiesTableConfig);
    this.pageMode = this.entitiesTableConfig().pageMode;
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
    entitiesTableConfigValue.setTable(this);
    this.translations = entitiesTableConfigValue.entityTranslations;

    this.headerActionDescriptors = [...entitiesTableConfigValue.headerActionDescriptors];
    this.groupActionDescriptors = [...entitiesTableConfigValue.groupActionDescriptors];
    this.cellActionDescriptors = [...entitiesTableConfigValue.cellActionDescriptors];

    if (entitiesTableConfigValue.entitiesDeleteEnabled) {
      this.cellActionDescriptors.push(
        {
          name: this.translate.instant('action.delete'),
          icon: 'mdi:trash-can-outline',
          isEnabled: entity => this.entitiesTableConfig().deleteEnabled(entity),
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

    this.selectionEnabled = entitiesTableConfigValue.selectionEnabled && enabledGroupActionDescriptors.length;
    this.defaultCursor = entitiesTableConfigValue.defaultCursor;

    this.columnsUpdated();

    let sortOrder: SortOrder = null;
    if (entitiesTableConfigValue.defaultSortOrder) {
      sortOrder = {
        property: entitiesTableConfigValue.defaultSortOrder.property,
        direction: entitiesTableConfigValue.defaultSortOrder.direction
      };
    }

    this.displayPagination = entitiesTableConfigValue.displayPagination;
    this.defaultPageSize = entitiesTableConfigValue.defaultPageSize;
    this.pageSizeOptions = [this.defaultPageSize, this.defaultPageSize * 2, this.defaultPageSize * 3];

    if (entitiesTableConfigValue.useTimePageLink) {
      this.timewindow = entitiesTableConfigValue.defaultTimewindowInterval;
      const interval = this.getTimePageLinkInterval();
      this.pageLink = new TimePageLink(10, 0, null, sortOrder,
        interval.startTime, interval.endTime);
    } else {
      this.pageLink = new PageLink(10, 0, null, sortOrder);
    }
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
    this.textSearch.valueChanges.pipe(
      debounceTime(150),
      distinctUntilChanged((prev, current) => (this.pageLink.textSearch ?? '') === current.trim()),
      takeUntil(this.destroy$)
    ).subscribe(value => {
      this.pageLink.textSearch = isNotEmptyStr(value) ? value.trim() : null;
      if (this.displayPagination) {
        this.paginator().pageIndex = 0;
      }
      this.updateData();
    });

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
      this.sortSubscription = this.sort().sortChange.subscribe(() => this.paginator().pageIndex = 0);
    }
    this.updateDataSubscription = ((this.displayPagination ? merge(this.sort().sortChange, this.paginator().page)
      : this.sort().sortChange) as Observable<any>)
      .pipe(
        tap(() => this.updateData())
      )
      .subscribe();
  }

  addEnabled() {
    return this.entitiesTableConfig().addEnabled;
  }

  clearSelection() {
    this.dataSource.selection.clear();
    this.cd.detectChanges();
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
    if (this.entitiesTableConfig().useTimePageLink) {
      const timePageLink = this.pageLink as TimePageLink;
      const interval = this.getTimePageLinkInterval();
      timePageLink.startTime = interval.startTime;
      timePageLink.endTime = interval.endTime;
    }
    this.dataSource.loadEntities(this.pageLink);
  }

  private getTimePageLinkInterval(): { startTime?: number, endTime?: number } {
    const interval: { startTime?: number, endTime?: number } = {};
    switch (this.timewindow.history.historyType) {
      case HistoryWindowType.LAST_INTERVAL:
        const currentTime = Date.now();
        interval.startTime = currentTime - this.timewindow.history.timewindowMs;
        interval.endTime = currentTime;
        break;
      case HistoryWindowType.FIXED:
        interval.startTime = this.timewindow.history.fixedTimewindow.startTimeMs;
        interval.endTime = this.timewindow.history.fixedTimewindow.endTimeMs;
        break;
      case HistoryWindowType.INTERVAL:
        const startEndTime = calculateIntervalStartEndTime(this.timewindow.history.quickInterval);
        interval.startTime = startEndTime[0];
        interval.endTime = startEndTime[1];
        break;
      case HistoryWindowType.FOR_ALL_TIME:
        interval.startTime = null;
        interval.endTime = null;
        break;
    }
    return interval;
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

  addEntity($event: Event) {
    let entity$: Observable<BaseData>;
    const entitiesTableConfig = this.entitiesTableConfig();
    if (entitiesTableConfig.addEntity) {
      entity$ = entitiesTableConfig.addEntity();
    } else {
      entity$ = this.dialog.open<AddEntityDialogComponent, AddEntityDialogData<BaseData>,
        BaseData>(AddEntityDialogComponent, {
        disableClose: true,
        panelClass: ['tb-dialog', 'tb-fullscreen-dialog'],
        data: {
          entitiesTableConfig: entitiesTableConfig
        }
      }).afterClosed();
    }
    entity$.subscribe(
      (entity) => {
        if (entity) {
          this.updateData();
          this.entitiesTableConfig().entityAdded(entity);
        }
      }
    );
  }

  onEntityUpdated(entity: BaseData) {
    this.updateData(false);
    this.entitiesTableConfig().entityUpdated(entity);
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
      this.entitiesTableConfig().deleteEntityTitle(entity),
      this.entitiesTableConfig().deleteEntityContent(entity),
      this.translate.instant('action.no'),
      this.translate.instant('action.yes'),
      true
    ).subscribe((result) => {
      if (result) {
        this.entitiesTableConfig().deleteEntity(entity.id).subscribe(
          () => {
            this.updateData();
            this.entitiesTableConfig().entitiesDeleted([entity.id]);
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
      this.entitiesTableConfig().deleteEntitiesTitle(entities.length),
      this.entitiesTableConfig().deleteEntitiesContent(entities.length),
      this.translate.instant('action.no'),
      this.translate.instant('action.yes'),
      true
    ).subscribe((result) => {
      if (result) {
        const tasks: Observable<string>[] = [];
        entities.forEach((entity) => {
          const entitiesTableConfig = this.entitiesTableConfig();
          if (entitiesTableConfig.deleteEnabled(entity)) {
            tasks.push(entitiesTableConfig.deleteEntity(entity.id).pipe(
              map(() => entity.id),
              catchError(() => of(null)
              )));
          }
        });
        forkJoin(tasks).subscribe(
          (ids) => {
            this.updateData();
            this.entitiesTableConfig().entitiesDeleted(ids.filter(id => id !== null));
          }
        );
      }
    });
  }

  onTimewindowChange() {
    if (this.displayPagination) {
      this.paginator().pageIndex = 0;
    }
    this.updateData();
  }

  enterFilterMode() {
    this.textSearchMode = true;
    setTimeout(() => {
      this.searchInputField().nativeElement.focus();
      this.searchInputField().nativeElement.setSelectionRange(0, 0);
    }, 10);
  }

  exitFilterMode() {
    this.textSearchMode = false;
    this.textSearch.reset();
  }

  resetSortAndFilter(update: boolean = true, preserveTimewindow: boolean = false) {
    this.textSearchMode = false;
    this.pageLink.textSearch = null;
    this.textSearch.reset('', {emitEvent: false});
    const entitiesTableConfig = this.entitiesTableConfig();
    if (entitiesTableConfig.useTimePageLink && !preserveTimewindow) {
      this.timewindow = entitiesTableConfig.defaultTimewindowInterval;
    }
    if (this.displayPagination) {
      this.paginator().pageIndex = 0;
    }
    const sortable = this.sort().sortables.get(entitiesTableConfig.defaultSortOrder.property);
    const sort = this.sort();
    sort.active = sortable.id;
    sort.direction = entitiesTableConfig.defaultSortOrder.direction === Direction.ASC ? 'asc' : 'desc';
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
    this.entityColumns = this.entitiesTableConfig().columns.filter(
      (column) => column instanceof EntityTableColumn)
      .map(column => column as EntityTableColumn<BaseData>);
    this.actionColumns = this.entitiesTableConfig().columns.filter(
      (column) => column instanceof EntityActionTableColumn)
      .map(column => column as EntityActionTableColumn<BaseData>);
    this.chipsColumns = this.entitiesTableConfig().columns.filter(
      (column) => column instanceof ChipsTableColumn)
      .map(column => column as ChipsTableColumn<BaseData>);

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

  cellActionDescriptorsUpdated() {
    this.cellActionDescriptors = [...this.entitiesTableConfig().cellActionDescriptors];
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

  cellChipsContent(entity: BaseData, column: EntityColumn<BaseData>, row: number) {
    if (column instanceof ChipsTableColumn) {
      const col = this.entitiesTableConfig().columns.indexOf(column);
      const index = row * this.entitiesTableConfig().columns.length + col;
      let res = column.cellContentFunction(entity, column.key)?.split(',');
      this.cellContentCache[index] = res;
      return res;
    } else {
      return '';
    }
  }

  cellChipAction(value: string, entity: BaseData, column: EntityColumn<BaseData>) {
    if (column instanceof ChipsTableColumn) {
      return column.chipActionFunction(entity, value);
    } else {
      return undefined;
    }
  }

  cellChipIconColor(value: string, entity: BaseData, column: EntityColumn<BaseData>) {
    if (column instanceof ChipsTableColumn) {
      return column.cellStyleFunction(entity, value);
    } else {
      return undefined;
    }
  }

  cellChipTooltip(value: string, entity: BaseData, column: EntityColumn<BaseData>) {
    if (column instanceof ChipsTableColumn) {
      return column.cellChipTooltip(entity, value);
    } else {
      return undefined;
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

  trackByColumnKey(column: EntityColumn<BaseData>) {
    return column.key;
  }
}
