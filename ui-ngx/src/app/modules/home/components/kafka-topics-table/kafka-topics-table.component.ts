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

import { Component, OnInit, ViewChild } from '@angular/core';
import { MatTableDataSource } from "@angular/material/table";
import { MatPaginator } from "@angular/material/paginator";
import { MatSort } from "@angular/material/sort";
import { EntityColumn, EntityTableColumn } from "@home/models/entity/entities-table-config.models";
import { BaseData } from "@shared/models/base-data";
import { isUndefined } from "@core/utils";
import { DomSanitizer, SafeHtml } from "@angular/platform-browser";

export interface KafkaTopic {
  name: string;
  partitions: number;
  replicas: number;
  size: number;
}

const ELEMENT_DATA: KafkaTopic[] = [
  {name: 'client_session_1', partitions: 50, replicas: 5, size: 71},
  {name: 'client_session_2', partitions: 50, replicas: 5, size: 71},
  {name: 'client_session_3', partitions: 50, replicas: 5, size: 71},
  {name: 'client_session_4', partitions: 50, replicas: 5, size: 71},
  {name: 'client_session_5', partitions: 50, replicas: 5, size: 71},
  {name: 'client_session_6', partitions: 50, replicas: 5, size: 71},
  {name: 'client_session_7', partitions: 50, replicas: 5, size: 71},
  {name: 'client_session_8', partitions: 50, replicas: 5, size: 71},
  {name: 'client_session_9', partitions: 50, replicas: 5, size: 71},
  {name: 'client_session_10', partitions: 50, replicas: 5, size: 71},
  {name: 'client_session_11', partitions: 50, replicas: 5, size: 71},
  {name: 'client_session_12', partitions: 50, replicas: 5, size: 71}
];

@Component({
  selector: 'tb-kafka-topics-table',
  templateUrl: './kafka-topics-table.component.html',
  styleUrls: ['./kafka-topics-table.component.scss']
})
export class KafkaTopicsTableComponent implements OnInit {

  displayedColumns: Array<string> = [];
  dataSource: MatTableDataSource<any>;
  columns: Array<EntityColumn<any>>;
  cellStyleCache: Array<any> = [];
  cellContentCache: Array<SafeHtml> = [];

  @ViewChild(MatPaginator) paginator: MatPaginator;
  @ViewChild(MatSort) sort: MatSort;

  constructor(private domSanitizer: DomSanitizer) { }

  ngOnInit(): void {
    this.columns = this.getColumns();
    this.columns.forEach(
      column => {
        this.displayedColumns.push(column.key);
      }
    );
    this.dataSource = new MatTableDataSource(ELEMENT_DATA);
  }

  getColumns() {
    const columns: Array<EntityColumn<any>> = [];
    columns.push(
      new EntityTableColumn<any>('name', 'kafka.name', '25%'),
      new EntityTableColumn<any>('partitions', 'kafka.partitions', '25%'),
      new EntityTableColumn<any>('replicas', 'kafka.replicas', '25%'),
      new EntityTableColumn<any>('size', 'kafka.size', '25%', entity => {
        return entity.size + ' B'
      })
    )
    return columns;
  }

  cellStyle(entity: BaseData, column: EntityColumn<BaseData>, row: number) {
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

  cellContent(entity: BaseData, column: EntityColumn<BaseData>, row: number) {
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

  ngAfterViewInit() {
    this.dataSource.paginator = this.paginator;
    this.dataSource.sort = this.sort;
  }

  applyFilter(filterValue: string) {
    filterValue = filterValue.trim(); // Remove whitespace
    filterValue = filterValue.toLowerCase(); // Datasource defaults to lowercase matches
    this.dataSource.filter = filterValue;
  }

}
