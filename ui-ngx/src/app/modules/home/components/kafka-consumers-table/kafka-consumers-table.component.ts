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

import { AfterViewInit, Component, OnInit, ViewChild } from '@angular/core';
import { MatTableDataSource } from "@angular/material/table";
import { MatPaginator } from "@angular/material/paginator";
import { MatSort } from "@angular/material/sort";
import { EntityColumn, EntityTableColumn } from "@home/models/entity/entities-table-config.models";
import { BaseData } from "@shared/models/base-data";
import { DomSanitizer, SafeHtml } from "@angular/platform-browser";
import { isUndefined } from "@core/utils";
import { KafkaService } from '@core/http/kafka.service';
import { PageLink } from '@shared/models/page/page-link';
import { KafkaConsumerGroup } from '@shared/models/kafka.model';

@Component({
  selector: 'tb-kafka-consumers-table',
  templateUrl: './kafka-consumers-table.component.html',
  styleUrls: ['./kafka-consumers-table.component.scss']
})
export class KafkaConsumersTableComponent implements OnInit, AfterViewInit {

  displayedColumns: Array<string> = [];
  dataSource: MatTableDataSource<KafkaConsumerGroup>  = new MatTableDataSource();
  columns: Array<EntityColumn<KafkaConsumerGroup>>;
  cellStyleCache: Array<any> = [];
  cellContentCache: Array<SafeHtml> = [];

  @ViewChild(MatPaginator) paginator: MatPaginator;
  @ViewChild(MatSort) sort: MatSort;

  constructor(private kafkaService: KafkaService,
              private domSanitizer: DomSanitizer) {
  }

  ngOnInit(): void {
    this.columns = this.getColumns();
    this.columns.forEach(
      column => {
        this.displayedColumns.push(column.key);
      }
    );
  }

  ngAfterViewInit() {
    this.dataSource.paginator = this.paginator;
    this.dataSource.sort = this.sort;
    const pageLink = new PageLink(100);
    this.kafkaService.getKafkaConsumerGroups(pageLink).subscribe(
      data => {
        this.dataSource = new MatTableDataSource(data.data);
      }
    );
  }

  getColumns() {
    const columns: Array<EntityColumn<KafkaConsumerGroup>> = [];
    columns.push(
      new EntityTableColumn<KafkaConsumerGroup>('state', 'kafka.state', '25%'),
      new EntityTableColumn<KafkaConsumerGroup>('id', 'kafka.id', '25%'),
      new EntityTableColumn<KafkaConsumerGroup>('members', 'kafka.members', '25%'),
      new EntityTableColumn<KafkaConsumerGroup>('lag', 'kafka.lag', '25%')
    );
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

  applyFilter(filterValue: string) {
    filterValue = filterValue.trim();
    filterValue = filterValue.toLowerCase();
    this.dataSource.filter = filterValue;
  }

}
