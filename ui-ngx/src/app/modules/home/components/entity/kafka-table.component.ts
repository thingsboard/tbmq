import { Directive, OnInit, ViewChild } from '@angular/core';
import { BaseData } from '@shared/models/base-data';
import { MatSort } from '@angular/material/sort';
import { MatPaginator } from '@angular/material/paginator';
import { PageLink } from '@shared/models/page/page-link';
import { MatTableDataSource } from '@angular/material/table';
import { DomSanitizer, SafeHtml } from '@angular/platform-browser';
import { EntityColumn, EntityTableColumn } from '@home/models/entity/entities-table-config.models';
import { isUndefined } from '@core/utils';
import { EntitiesDataSource } from '@home/models/datasource/entity-datasource';
import { SortOrder } from '@shared/models/page/sort-order';

@Directive()
// tslint:disable-next-line:directive-class-suffix
export abstract class KafkaTableComponent<T extends BaseData> implements OnInit {

  @ViewChild(MatSort) sort: MatSort;
  @ViewChild(MatPaginator) paginator: MatPaginator;

  columns = [];
  dataSource: MatTableDataSource<T> = new MatTableDataSource();
  displayedColumns: Array<string> = [];

  defaultPageSize = 20;
  pageSizeOptions;
  pageLink: PageLink;

  cellContentCache: Array<SafeHtml> = [];
  cellTooltipCache: Array<string> = [];
  cellStyleCache: Array<any> = [];

  constructor(protected domSanitizer: DomSanitizer) {
  }

  ngOnInit(): void {
    this.dataSource.sort = this.sort;
    this.dataSource.paginator = this.paginator;
    this.pageSizeOptions = [this.defaultPageSize, this.defaultPageSize * 2, this.defaultPageSize * 3];
    this.pageLink = new PageLink(10, 0, null);
    this.columns = this.getColumns();
    this.columns.forEach(
      column => {
        this.displayedColumns.push(column.key);
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

  applyFilter(filterValue: string) {
    filterValue = filterValue.trim();
    filterValue = filterValue.toLowerCase();
    this.dataSource.filter = filterValue;
  }

  abstract getColumns();
}
