import { Component, OnInit, ViewChild } from '@angular/core';
import { MatTableDataSource } from "@angular/material/table";
import { MatPaginator } from "@angular/material/paginator";
import { MatSort } from "@angular/material/sort";

export interface KafkaTopic {
  name: string;
  partitions: number;
  replicas: number;
  cleanupPolicy: string;
  size: string;
}

const ELEMENT_DATA: KafkaTopic[] = [
  {name: 'client_session_1', partitions: 50, replicas: 5, cleanupPolicy: 'compact', size: '71 B'},
  {name: 'client_session_2', partitions: 50, replicas: 5, cleanupPolicy: 'compact', size: '71 B'},
  {name: 'client_session_3', partitions: 50, replicas: 5, cleanupPolicy: 'compact', size: '71 B'},
  {name: 'client_session_4', partitions: 50, replicas: 5, cleanupPolicy: 'compact', size: '71 B'},
  {name: 'client_session_5', partitions: 50, replicas: 5, cleanupPolicy: 'compact', size: '71 B'},
  {name: 'client_session_6', partitions: 50, replicas: 5, cleanupPolicy: 'compact', size: '71 B'},
  {name: 'client_session_7', partitions: 50, replicas: 5, cleanupPolicy: 'compact', size: '71 B'},
  {name: 'client_session_8', partitions: 50, replicas: 5, cleanupPolicy: 'compact', size: '71 B'},
  {name: 'client_session_9', partitions: 50, replicas: 5, cleanupPolicy: 'compact', size: '71 B'},
  {name: 'client_session_10', partitions: 50, replicas: 5, cleanupPolicy: 'compact', size: '71 B'},
  {name: 'client_session_11', partitions: 50, replicas: 5, cleanupPolicy: 'compact', size: '71 B'},
  {name: 'client_session_12', partitions: 50, replicas: 5, cleanupPolicy: 'compact', size: '71 B'}
];

@Component({
  selector: 'tb-kafka-topics-table',
  templateUrl: './kafka-topics-table.component.html',
  styleUrls: ['./kafka-topics-table.component.scss']
})
export class KafkaTopicsTableComponent implements OnInit {

  displayedColumns: string[] = ['name', 'partitions', 'replicas', 'cleanupPolicy', 'size'];
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
