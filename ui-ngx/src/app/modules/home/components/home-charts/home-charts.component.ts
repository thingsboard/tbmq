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

import { Component, ElementRef, OnInit, viewChild } from '@angular/core';
import { TranslateModule } from '@ngx-translate/core';
import { Timewindow } from '@shared/models/time/time.models';
import { TimeService } from '@core/services/time.service';
import { ChartView, CHARTS_HOME } from '@shared/models/chart.model';
import { HOME_CHARTS_DURATION, HomePageTitleType } from '@shared/models/home-page.model';
import { ResizeObserver } from '@juggle/resize-observer';
import { CardTitleButtonComponent } from '@shared/components/button/card-title-button.component';
import { MatIconButton } from '@angular/material/button';
import { MatIcon } from '@angular/material/icon';
import { NgxHmCarouselComponent, NgxHmCarouselItemDirective } from 'ngx-hm-carousel';
import { FormsModule } from '@angular/forms';
import { ChartComponent } from '@shared/components/chart/chart.component';

@Component({
  selector: 'tb-home-charts',
  templateUrl: './home-charts.component.html',
  styleUrls: ['./home-charts.component.scss'],
  imports: [CardTitleButtonComponent, MatIconButton, MatIcon, NgxHmCarouselComponent, FormsModule, NgxHmCarouselItemDirective, TranslateModule, ChartComponent]
})
export class HomeChartsComponent implements OnInit {

  readonly homeChartsContainer = viewChild<ElementRef>('homeChartsContainer');

  readonly cardType = HomePageTitleType.MONITORING;
  readonly chartKeys = CHARTS_HOME;
  readonly chartView = ChartView.compact;

  carouselIndex = 0;
  visibleItems = 5;
  chartHeight: number;
  timewindow: Timewindow;

  constructor(private timeService: TimeService) {
  }

  ngOnInit() {
    this.setTimewindow();
    this.onResize();
  }

  private setTimewindow() {
    this.timewindow = this.timeService.defaultTimewindow();
    this.timewindow.realtime.timewindowMs = HOME_CHARTS_DURATION;
  }

  private onResize() {
    const resizeObserver = new ResizeObserver((entries) => {
      const containerWidth = entries[0].contentRect.width;
      let width = (containerWidth / 5) - 16;
      this.visibleItems = 5;
      if (containerWidth < 500 || window.innerHeight > window.innerWidth) { // mobile or portrait
        width = containerWidth / 2;
        this.visibleItems = 2;
      }
      this.chartHeight = Math.round(width * 0.5);
    });
    resizeObserver.observe(this.homeChartsContainer().nativeElement);
  }
}
