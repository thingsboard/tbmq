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

import { AfterViewInit, Directive, ElementRef, OnInit, ViewChild } from '@angular/core';
import { BaseData } from '@shared/models/base-data';
import { MatSort } from '@angular/material/sort';
import { MatPaginator, PageEvent } from '@angular/material/paginator';
import { MAX_SAFE_PAGE_SIZE, PageLink } from '@shared/models/page/page-link';
import { MatTableDataSource } from '@angular/material/table';
import { DomSanitizer, SafeHtml } from '@angular/platform-browser';
import { EntityColumn, EntityTableColumn } from '@home/models/entity/entities-table-config.models';
import { isUndefined } from '@core/utils';
import { fromEvent, Observable, of } from 'rxjs';
import { EntityTypeTranslation } from '@shared/models/entity-type.models';
import { debounceTime, distinctUntilChanged, tap } from 'rxjs/operators';
import { Direction } from '@shared/models/page/sort-order';

@Directive()
// tslint:disable-next-line:directive-class-suffix
export abstract class KafkaTableComponent<T extends BaseData> implements OnInit, AfterViewInit {

  @ViewChild(MatSort) sort: MatSort;
  @ViewChild(MatPaginator) paginator: MatPaginator;
  @ViewChild('searchInput') searchInputField: ElementRef;

  isLoading$: Observable<boolean> = of(false);

  translations = {
    search: 'action.search',
    refresh: 'action.refresh',
    close: 'action.close'
  };

  columns = [];
  dataSource: MatTableDataSource<T> = new MatTableDataSource();
  displayedColumns: Array<string> = [];
  displayPagination = true;
  // translations: EntityTypeTranslation;

  defaultPageSize = 5;
  pageSizeOptions;
  pageLink: PageLink;

  cellContentCache: Array<SafeHtml> = [];
  cellTooltipCache: Array<string> = [];
  cellStyleCache: Array<any> = [];
  totalElements: number;
  searchEnabled = true;
  textSearchMode = false;

  abstract fetchEntities$: () => Observable<any>;

  constructor(protected domSanitizer: DomSanitizer) {
  }

  ngOnInit(): void {
    this.dataSource.sort = this.sort;
    this.dataSource.paginator = this.paginator;
    this.pageSizeOptions = [this.defaultPageSize, this.defaultPageSize * 2, this.defaultPageSize * 3];
    this.pageLink = new PageLink(10, 0, '');
    this.pageLink.textSearch = '';
    this.pageLink.pageSize = this.displayPagination ? this.defaultPageSize : MAX_SAFE_PAGE_SIZE;
    this.columns = this.getColumns();
    this.columns.forEach(
      column => {
        this.displayedColumns.push(column.key);
      }
    );
    this.loadEntities();
  }

  ngAfterViewInit() {
    if (this.searchInputField?.nativeElement) {
      fromEvent(this.searchInputField.nativeElement, 'keyup')
        .pipe(
          debounceTime(150),
          distinctUntilChanged(),
          tap(() => {
            if (this.displayPagination) {
              this.paginator.pageIndex = 0;
            }
            this.updateData();
          })
        )
        .subscribe();
    }
  }

  updateData(closeDetails: boolean = true) {
    if (this.displayPagination) {
      this.pageLink.page = this.paginator.pageIndex;
      this.pageLink.pageSize = this.paginator.pageSize;
    } else {
      this.pageLink.page = 0;
    }
    if (this.sort.active) {
      this.pageLink.sortOrder = {
        property: this.sort.active,
        direction: Direction[this.sort.direction.toUpperCase()]
      };
    } else {
      this.pageLink.sortOrder = null;
    }
    this.loadEntities();
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
    this.textSearchMode = false;
    this.pageLink.textSearch = null;
    if (this.displayPagination) {
      this.paginator.pageIndex = 0;
    }
    this.updateData();
  }

  private loadEntities() {
    this.fetchEntities$().subscribe(
      data => {
        this.totalElements = data.totalElements;
        this.dataSource = new MatTableDataSource(data.data);
      }
    );
  }

  pageChanged(event: PageEvent) {
    this.pageLink.pageSize = event.pageSize;
    this.pageLink.page = event.pageIndex;
    this.loadEntities();
  }

  cellStyle(entity: T, column: EntityColumn<T>, row: number) {
    const col = this.columns.indexOf(column);
    const index = row * this.columns.length + col;
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

  cellContent(entity: T, column: EntityColumn<T>, row: number) {
    if (column instanceof EntityTableColumn) {
      const col = this.columns.indexOf(column);
      const index = row * this.columns.length + col;
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

  applyFilter(filterValue: string) {
    filterValue = filterValue.trim();
    filterValue = filterValue.toLowerCase();
    this.dataSource.filter = filterValue;
  }

  abstract getColumns();
}

export function formatBytes(bytes, decimals = 1) {
  if (!+bytes) { return '0 B'; }
  const k = 1024;
  const dm = decimals < 0 ? 0 : decimals;
  const sizes = ['B', 'KB', 'MB', 'GB', 'TB', 'PB', 'EB', 'ZB', 'YB'];
  const i = Math.floor(Math.log(bytes) / Math.log(k));
  return `${parseFloat((bytes / Math.pow(k, i)).toFixed(dm))} ${sizes[i]}`;
}
