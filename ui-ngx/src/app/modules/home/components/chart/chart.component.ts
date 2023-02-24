import { AfterContentInit, AfterViewInit, Component, Input, OnDestroy, OnInit } from '@angular/core';
import { Subject } from "rxjs";
import { Store } from "@ngrx/store";
import { AppState } from "@core/core.state";
import { Router } from "@angular/router";
import { MailServerService } from "@core/http/mail-server.service";
import { TranslateService } from "@ngx-translate/core";
import { FormBuilder } from "@angular/forms";
import Chart, { ChartPoint, ChartType } from "chart.js";
import { guid } from "@core/utils";

export enum StatsType {
  DROPPED_MESSAGES = 'DROPPED_MESSAGES',
  INCOMING_MESSAGES = 'INCOMING_MESSAGES',
  OUTGOING_MESSAGES = 'OUTGOING_MESSAGES',
  RETAINED_MESSAGES = 'RETAINED_MESSAGES',
  CONNECTIONS = 'CONNECTIONS',
  SUBSCRIPTIONS = 'SUBSCRIPTIONS'
}

@Component({
  selector: 'tb-chart',
  templateUrl: './chart.component.html',
  styleUrls: ['./chart.component.scss']
})
export class ChartComponent implements OnInit, OnDestroy, AfterViewInit, AfterContentInit {

  @Input()
  title: string;

  @Input()
  type: ChartType;

  @Input()
  chatId: string;

  @Input()
  dataset: Array<ChartPoint>;

  ctx;

  data;

  private destroy$ = new Subject();

  constructor(protected store: Store<AppState>,
              private router: Router,
              private adminService: MailServerService,
              private translate: TranslateService,
              public fb: FormBuilder) {
  }

  ngAfterContentInit(): void {
    this.createChart();

  }

  ngOnInit() {

  }

  ngAfterViewInit(): void {
  }

  ngOnDestroy() {
    this.destroy$.next();
    this.destroy$.complete();
  }

  private createChart() {
    this.ctx = document.getElementById(this.chatId) as HTMLCanvasElement;
    this.data = {
      label: this.title,
      fill: false,
      backgroundColor: 'rgb(252,141,98, 0.5)',
      borderColor: 'rgb(252,141,98, 0.5)',
      hoverBackgroundColor: 'rgb(252,141,98, 0.75)',
      hoverBorderColor: 'rgb(252,141,98)',
      data: [
        { x: 1606943100000, y: 1.5 },
        { x: 1607943200000, y: 2.7 },
        { x: 1608943300000, y: 1.3 },
        { x: 1609943400000, y: 5.2 },
        { x: 1610943500000, y: 9.4 },
        { x: 1611943600000, y: 6.5 },
      ]
    };
    let chart = new Chart(this.ctx, {
      type: this.type || 'line',
      data: {
        datasets: [this.data]
      },
      options: {
        title: {
          display: true,
          text: this.title
        },
        scales: {
          xAxes: [{
            type: 'time',
            distribution: 'series',
            time: {
              unitStepSize: 500,
              unit: 'hour',
              displayFormats: {
                hour: 'hA',
                day: 'YYYY-MM-DD',
                month: 'YYYY-MM'
              }
            }
          }]
        },
        hover: {
          mode: 'point'
        }
      }
    });
  }

}
