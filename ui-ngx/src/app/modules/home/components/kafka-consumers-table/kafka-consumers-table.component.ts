import { Component, OnInit, ViewChild } from '@angular/core';
import { MatTableDataSource } from "@angular/material/table";
import { MatPaginator } from "@angular/material/paginator";
import { MatSort } from "@angular/material/sort";
import { EntityColumn, EntityTableColumn } from "@home/models/entity/entities-table-config.models";
import { BaseData } from "@shared/models/base-data";
import { DomSanitizer, SafeHtml } from "@angular/platform-browser";
import { isUndefined } from "@core/utils";

export interface KafkaConsumerGroup {
  state: string;
  id: string;
  members: number;
  lag: number;
}

const ELEMENT_DATA: KafkaConsumerGroup[] = [
  {state: 'Stable', id: 'device-msg-consumer-group', members: 2, lag: 1},
  {state: 'Stable', id: 'publish-msg-consumer-group', members: 2, lag: 2},
  {state: 'Stable', id: 'retained-msg-consumer-group', members: 2, lag: 3}
];

@Component({
  selector: 'tb-kafka-consumers-table',
  templateUrl: './kafka-consumers-table.component.html',
  styleUrls: ['./kafka-consumers-table.component.scss']
})
export class KafkaConsumersTableComponent implements OnInit {

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

  ngAfterViewInit() {
    this.dataSource.paginator = this.paginator;
    this.dataSource.sort = this.sort;
  }

  getColumns() {
    const columns: Array<EntityColumn<any>> = [];
    columns.push(
      new EntityTableColumn<any>('state', 'kafka.state', '25%'),
      new EntityTableColumn<any>('id', 'kafka.id', '25%'),
      new EntityTableColumn<any>('members', 'kafka.members', '25%'),
      new EntityTableColumn<any>('lag', 'kafka.lag', '25%')
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

  applyFilter(filterValue: string) {
    filterValue = filterValue.trim(); // Remove whitespace
    filterValue = filterValue.toLowerCase(); // Datasource defaults to lowercase matches
    this.dataSource.filter = filterValue;
  }

}
