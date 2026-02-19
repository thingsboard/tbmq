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

import { Component, effect, input, signal } from '@angular/core';
import { ChartKey } from '@shared/models/chart.model';

@Component({
  selector: 'tb-chart-canvas',
  templateUrl: './chart-canvas.component.html',
  host: {
    // '[style.height]': 'containerHeight()'
  }
})
export class ChartCanvasComponent {

  readonly chartKey = input<ChartKey>();
  readonly chartHeight = input<number>();
  readonly entityIds = input<string[]>();
  readonly isFullscreen = input<boolean>();
  readonly totalEntityIdOnly = input<boolean>();

  containerHeight = signal<string>(null);

  constructor() {
    effect(() => {
      this.onFullScreen(this.isFullscreen());
    });
  }

  private onFullScreen(isFullscreen: boolean) {
    if (isFullscreen) {
      const legendRows = this.totalEntityIdOnly() ? 1 : this.entityIds().length;
      const legendHeight = 100 + (legendRows * 24);
      this.containerHeight.set(`calc(100vh - ${legendHeight}px)`);
    } else {
      this.containerHeight.set(this.chartHeight() + 'px');
    }
  }
}
