import { Component, OnInit, ViewChild } from '@angular/core';
import { MatTableDataSource } from "@angular/material/table";
import { MatPaginator } from "@angular/material/paginator";
import { MatSort } from "@angular/material/sort";

export interface KafkaBroker {
  id: string;
  address: string;
  size: string;
}

const ELEMENT_DATA: KafkaBroker[] = [
  {id: 'id_1', address: 'localhost', size: '77.2 B'},
  {id: 'id_2', address: '123.124.22.21', size: '77.2 B'},
  {id: 'id_3', address: '125.224.42.21', size: '77.2 B'},
];

@Component({
  selector: 'tb-kafka-brokers-table',
  templateUrl: './kafka-brokers-table.component.html',
  styleUrls: ['./kafka-brokers-table.component.scss']
})
export class KafkaBrokersTableComponent implements OnInit {

  displayedColumns: string[] = ['id', 'address', 'size'];
  dataSource: MatTableDataSource<any>;

  @ViewChild(MatPaginator) paginator: MatPaginator;
  @ViewChild(MatSort) sort: MatSort;

  constructor() { }

  ngOnInit(): void {
    this.dataSource = new MatTableDataSource(ELEMENT_DATA);
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
