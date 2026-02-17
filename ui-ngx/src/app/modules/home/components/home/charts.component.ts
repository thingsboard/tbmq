///
/// Copyright © 2016-2026 The Thingsboard Authors
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

import { Component, ElementRef, OnInit, viewChild } from '@angular/core';
import { TranslateModule } from '@ngx-translate/core';
import { Timewindow } from '@shared/models/time/time.models';
import { TimeService } from '@core/services/time.service';
import { ChartView, ChartKey } from '@shared/models/chart.model';
import { HOME_CHARTS_DURATION, HomePageTitleType } from '@shared/models/home-page.model';
import { CardTitleButtonComponent } from '@shared/components/button/card-title-button.component';
import { FormsModule } from '@angular/forms';
import { ChartComponent } from '@shared/components/chart/chart.component';

@Component({
  selector: 'tb-home-charts',
  templateUrl: './charts.component.html',
  styleUrls: ['./charts.component.scss'],
  imports: [CardTitleButtonComponent, FormsModule, TranslateModule, ChartComponent]
})
export class ChartsComponent implements OnInit {

  readonly homeChartsContainer = viewChild<ElementRef>('homeChartsContainer');

  readonly cardType = HomePageTitleType.MONITORING;
  readonly chartKeys = [
    ChartKey.sessions,
    ChartKey.incomingMsgs,
    ChartKey.outgoingMsgs,
    ChartKey.droppedMsgs,
    ChartKey.inboundPayloadTraffic,
    ChartKey.outboundPayloadTraffic,
    ChartKey.subscriptions,
    ChartKey.retainedMsgs
  ];
  readonly chartView = ChartView.compact;

  chartHeight: number;
  timewindow: Timewindow;

  constructor(private timeService: TimeService) {
  }

  ngOnInit() {
    this.setTimewindow();
    this.onResize()
  }

  private setTimewindow() {
    this.timewindow = this.timeService.defaultTimewindow();
    this.timewindow.realtime.timewindowMs = HOME_CHARTS_DURATION;
  }

  private onResize() {
    const resizeObserver = new ResizeObserver((entries) => {
      const containerWidth = entries[0].contentRect.width;
      const width = (containerWidth / 4) - 64;
      this.chartHeight = Math.round(width * 0.5);
    });
    resizeObserver.observe(this.homeChartsContainer().nativeElement);
  }
}
