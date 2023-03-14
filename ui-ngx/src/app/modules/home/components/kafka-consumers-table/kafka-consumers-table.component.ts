import { Component, OnInit, ViewChild } from '@angular/core';
import { MatTableDataSource } from "@angular/material/table";
import { MatPaginator } from "@angular/material/paginator";
import { MatSort } from "@angular/material/sort";

export interface KafkaConsumerGroup {
  state: string;
  id: string;
  coordination: number;
  protocol: string;
  members: number;
  lag: number;
}

const ELEMENT_DATA: KafkaConsumerGroup[] = [
  {state: 'Stable', id: 'device-msg-consumer-group', coordination: 1001, protocol: 'sticky', members: 2, lag: 0},
  {state: 'Stable', id: 'publish-msg-consumer-group', coordination: 1001, protocol: 'sticky', members: 2, lag: 0},
  {state: 'Stable', id: 'retained-msg-consumer-group', coordination: 1001, protocol: 'sticky', members: 2, lag: 0}
];

@Component({
  selector: 'tb-kafka-consumers-table',
  templateUrl: './kafka-consumers-table.component.html',
  styleUrls: ['./kafka-consumers-table.component.scss']
})
export class KafkaConsumersTableComponent implements OnInit {
  displayedColumns: string[] = ['state', 'id', 'coordination', 'protocol', 'members', 'lag'];
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
