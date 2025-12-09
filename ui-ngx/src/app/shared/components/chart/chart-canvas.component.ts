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

import { Component, effect, input, signal } from '@angular/core';
import { ChartDataKey, LegendKey } from '@shared/models/chart.model';

@Component({
  selector: 'tb-chart-canvas',
  templateUrl: './chart-canvas.component.html',
  host: {
    '[style.height]': 'containerHeight()'
  }
})
export class ChartCanvasComponent {

  readonly chartDataKey = input.required<ChartDataKey>();
  readonly chartHeight = input.required<number>();
  readonly legendItems = input<LegendKey[]>();
  readonly isFullscreen = input<boolean>();

  containerHeight = signal<string>(null);

  constructor() {
    effect(() => {
      this.onFullScreen(this.isFullscreen());
    });
  }

  private onFullScreen(isFullscreen: boolean) {
    if (isFullscreen) {
      const height = 120 + ((this.legendItems().length - 1) * 24);
      this.containerHeight.set(`calc(100vh - ${height}px)`);
    } else {
      this.containerHeight.set(this.chartHeight() + 'px');
    }
  }
}
