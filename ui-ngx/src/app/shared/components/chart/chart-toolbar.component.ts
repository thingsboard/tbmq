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

import { Component, EventEmitter, input, Output } from '@angular/core';
import { Timewindow } from '@shared/models/time/time.models';
import { TimewindowComponent } from '@shared/components/time/timewindow.component';
import { FormsModule } from '@angular/forms';
import { ToggleHeaderComponent, ToggleOption } from '@shared/components/toggle-header.component';
import { MatIcon } from '@angular/material/icon';
import { MatTooltip } from '@angular/material/tooltip';
import { MatIconButton } from '@angular/material/button';
import { TranslateModule, TranslateService } from '@ngx-translate/core';
import { DataSizeUnitType, DataSizeUnitTypeTranslationMap } from '@shared/models/ws-client.model';
import {
  ChartTooltipTranslationMap,
  StatsChartType,
  StatsChartTypeTranslationMap
} from '@shared/models/chart.model';

@Component({
  selector: 'tb-chart-toolbar',
  templateUrl: './chart-toolbar.component.html',
  imports: [TimewindowComponent, FormsModule, ToggleHeaderComponent, ToggleOption, MatIcon, MatTooltip, MatIconButton, TranslateModule]
})
export class ChartToolbarComponent {

  readonly timewindow = input<Timewindow>();
  readonly chartType = input<string>();
  readonly currentDataSizeUnitType = input<string>();
  readonly showTimewindow = input<boolean>(true);
  readonly showDataSizeUnitToggle = input<boolean>(true);
  readonly showFullscreen = input<boolean>(true);

  public isFullscreen = false;

  readonly dataSizeUnitType = Object.values(DataSizeUnitType);
  readonly dataSizeUnitTypeTranslationMap = DataSizeUnitTypeTranslationMap;
  readonly chartTypeTranslationMap = StatsChartTypeTranslationMap;

  @Output() timewindowChange = new EventEmitter<Timewindow>();
  @Output() unitTypeChange = new EventEmitter<string>();
  @Output() fullscreenToggle = new EventEmitter<boolean>();

  constructor(private translate: TranslateService) {}

  chartTooltip(type: string): string {
    return this.translate.instant(ChartTooltipTranslationMap.get(type));
  }

  isTrafficPayloadChart(): boolean {
    return this.chartType() === StatsChartType.inboundPayloadTraffic || this.chartType() === StatsChartType.outboundPayloadTraffic;
  }

  onFullscreenChange() {
    this.isFullscreen = !this.isFullscreen;
    this.fullscreenToggle.emit(this.isFullscreen);
  }
}
