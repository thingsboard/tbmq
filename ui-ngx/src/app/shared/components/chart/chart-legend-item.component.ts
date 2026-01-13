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

import { Component, EventEmitter, input, Output } from '@angular/core';
import { SafePipe } from '@shared/pipe/safe.pipe';
import { LegendKey } from '@shared/models/chart.model';

@Component({
  selector: 'tb-chart-legend-item',
  standalone: true,
  imports: [SafePipe],
  templateUrl: './chart-legend-item.component.html'
})
export class ChartLegendItemComponent {
  readonly legendKey = input<LegendKey>();
  readonly left = input<boolean>(false);

  @Output() legendKeyEnter = new EventEmitter<LegendKey>();
  @Output() legendKeyLeave = new EventEmitter<LegendKey>();
  @Output() toggleLegendKey = new EventEmitter<LegendKey>();
}
