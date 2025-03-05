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

import { AfterViewInit, Directive, OnInit, viewChild } from '@angular/core';
import { PageLink } from '@shared/models/page/page-link';
import { MatSort } from '@angular/material/sort';
import { Observable } from 'rxjs';
import { BaseData } from '@shared/models/base-data';
import { EntityColumn, EntityTableColumn } from '@home/models/entity/entities-table-config.models';
import { DomSanitizer, SafeHtml } from '@angular/platform-browser';
import { isUndefined } from '@core/utils';
import { MatTableDataSource } from '@angular/material/table';

@Directive()
export abstract class EntitiesTableHomeNoPagination<T extends BaseData> implements OnInit, AfterViewInit {

  readonly sort = viewChild(MatSort);

  columns = [];
  dataSource: MatTableDataSource<T> = new MatTableDataSource();
  displayedColumns: Array<string> = [];
  pageLink: PageLink = new PageLink(999);

  cellContentCache: Array<SafeHtml> = [];
  cellStyleCache: Array<any> = [];

  abstract fetchEntities$: () => Observable<any>;

  abstract getColumns();

  constructor(protected domSanitizer: DomSanitizer) {
  }

  ngOnInit(): void {
    this.columns = this.getColumns();
    this.columns.forEach(
      column => {
        this.displayedColumns.push(column.key);
      }
    );
    this.updateData();
  }

  updateData() {
    this.loadEntities();
  }

  private loadEntities() {
    this.fetchEntities$().subscribe(
      data => {
        this.dataSource = new MatTableDataSource(data.data);
      }
    );
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

  ngAfterViewInit(): void {
    const sort = this.sort();
    if (sort) {
      sort.sortChange.subscribe(() => {
        this.dataSource = new MatTableDataSource(this.dataSource.data.sort(this.sortTable()));
      });
    }
  }

  private sortTable() {
    const sort = this.sort();
    if (sort?.direction) {
      let direction = sort.direction === 'desc' ? -1 : 1;
      let active = sort.active;
      return function(a, b) {
        return ((a[active] < b[active]) ? -1 : (a[active] > b[active]) ? 1 : 0) * direction;
      };
    }
  }
}
