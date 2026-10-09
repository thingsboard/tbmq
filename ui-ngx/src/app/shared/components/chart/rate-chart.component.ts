///
/// ThingsBoard, Inc. ("COMPANY") CONFIDENTIAL
///
/// Copyright © 2016-2026 ThingsBoard, Inc. All Rights Reserved.
///
/// NOTICE: All information contained herein is, and remains
/// the property of ThingsBoard, Inc. and its suppliers,
/// if any.  The intellectual and technical concepts contained
/// herein are proprietary to ThingsBoard, Inc.
/// and its suppliers and may be covered by U.S. and Foreign Patents,
/// patents in process, and are protected by trade secret or copyright law.
///
/// Dissemination of this information or reproduction of this material is strictly forbidden
/// unless prior written permission is obtained from COMPANY.
///
/// Access to the source code contained herein is hereby forbidden to anyone except current COMPANY employees,
/// managers or contractors who have executed Confidentiality and Non-disclosure agreements
/// explicitly covering such access.
///
/// The copyright notice above does not evidence any actual or intended publication
/// or disclosure  of  this source code, which includes
/// information that is confidential and/or proprietary, and is a trade secret, of  COMPANY.
/// ANY REPRODUCTION, MODIFICATION, DISTRIBUTION, PUBLIC  PERFORMANCE,
/// OR PUBLIC DISPLAY OF OR THROUGH USE  OF THIS  SOURCE CODE  WITHOUT
/// THE EXPRESS WRITTEN CONSENT OF COMPANY IS STRICTLY PROHIBITED,
/// AND IN VIOLATION OF APPLICABLE LAWS AND INTERNATIONAL TREATIES.
/// THE RECEIPT OR POSSESSION OF THIS SOURCE CODE AND/OR RELATED INFORMATION
/// DOES NOT CONVEY OR IMPLY ANY RIGHTS TO REPRODUCE, DISCLOSE OR DISTRIBUTE ITS CONTENTS,
/// OR TO MANUFACTURE, USE, OR SELL ANYTHING THAT IT  MAY DESCRIBE, IN WHOLE OR IN PART.
///

import {
  afterNextRender,
  ChangeDetectionStrategy,
  Component,
  computed,
  DestroyRef,
  ElementRef,
  inject,
  input,
  signal,
  viewChild
} from '@angular/core';
import { DatePipe } from '@angular/common';
import { TranslateModule, TranslateService } from '@ngx-translate/core';
import { currentLangSignal } from '@core/translate/current-lang-signal';
import { TsValue } from '@shared/models/chart.model';

export interface RateChartSeries {
  key: string;
  // Translation key.
  label: string;
  points: TsValue[];
  current: number;
  format: (value: number) => string;
  // Any CSS color; series without one take the palette color of their position.
  color?: string;
}

interface SeriesPath {
  key: string;
  color: string;
  linePath: string;
  areaPath: string;
  endX: number;
  endY: number;
}

interface ChartGeometry {
  paths: SeriesPath[];
  timeline: number[];
  startTs: number;
  endTs: number;
  toX: (ts: number) => number;
  toY: (value: number) => number;
}

interface HoverRow {
  key: string;
  color: string;
  label: string;
  value: string;
  y: number;
}

interface HoverVm {
  x: number;
  ts: number;
  rows: HoverRow[];
}

const WIDTH = 266;
const HEIGHT = 84;
const PAD_X = 5;
const PAD_TOP = 8;
const AXIS_BAND = 16;
// Validated for deutan/protan/tritan separation and contrast against the white card.
const SERIES_PALETTE = ['#58519e', '#00897b', '#d1a656'];

let nextChartId = 0;

@Component({
    selector: 'tb-rate-chart',
    template: `
      <div #chartBox class="tb-chart">
        <svg class="tb-chart-svg" [attr.viewBox]="'0 0 ' + width() + ' ' + height()" role="img" tabindex="0"
             [attr.aria-label]="ariaLabel()"
             (pointermove)="onPointerMove($event)" (pointerleave)="hoverIndex.set(null)"
             (keydown)="onKeydown($event)" (blur)="hoverIndex.set(null)">
          @if (geometry(); as g) {
            <defs>
              @for (path of g.paths; track path.key) {
                <linearGradient [attr.id]="fillIdPrefix + path.key" x1="0" y1="0" x2="0" y2="1">
                  <stop class="tb-chart-fill-stop" [style.--tb-series]="path.color" offset="0%" stop-opacity="0.16"/>
                  <stop class="tb-chart-fill-stop" [style.--tb-series]="path.color" offset="100%" stop-opacity="0"/>
                </linearGradient>
              }
            </defs>
            <line class="tb-chart-baseline" [attr.x1]="padX" [attr.x2]="width() - padX" [attr.y1]="plotBottom()" [attr.y2]="plotBottom()"/>
            @for (path of g.paths; track path.key) {
              <path class="tb-chart-area" [attr.d]="path.areaPath" [attr.fill]="'url(#' + fillIdPrefix + path.key + ')'"/>
              <path class="tb-chart-line" [style.--tb-series]="path.color" [attr.d]="path.linePath"/>
              <circle class="tb-chart-marker" [style.--tb-series]="path.color" [attr.cx]="path.endX" [attr.cy]="path.endY" r="3.5"/>
            }
            <text class="tb-chart-tick" [attr.x]="padX" [attr.y]="height() - 3">{{ g.startTs | date: 'HH:mm' }}</text>
            <text class="tb-chart-tick" [attr.x]="width() - padX" [attr.y]="height() - 3" text-anchor="end">{{ g.endTs | date: 'HH:mm' }}</text>
            @if (hover(); as h) {
              <line class="tb-chart-cursor" [attr.x1]="h.x" [attr.x2]="h.x" [attr.y1]="padTop" [attr.y2]="plotBottom()"/>
              @for (row of h.rows; track row.key) {
                <circle class="tb-chart-marker" [style.--tb-series]="row.color" [attr.cx]="h.x" [attr.cy]="row.y" r="3.5"/>
              }
            }
          } @else {
            <text class="tb-chart-empty" [attr.x]="width() / 2" [attr.y]="plotBottom() / 2" text-anchor="middle" dominant-baseline="central">
              {{ 'rate-chart.collecting-samples' | translate }}
            </text>
          }
        </svg>
        @if (hover(); as h) {
          <div class="tb-chart-tooltip" [class.tb-chart-tooltip-left]="h.x > width() / 2" [style.left.%]="h.x / width() * 100">
            <div class="tb-chart-tooltip-time">{{ h.ts | date: 'HH:mm' }}</div>
            @for (row of h.rows; track row.key) {
              <div class="tb-chart-tooltip-row">
                <span class="tb-legend-key" [style.--tb-series]="row.color"></span>
                <span class="tb-chart-tooltip-value">{{ row.value }}</span>
                <span class="tb-chart-tooltip-label">{{ row.label | translate }}</span>
              </div>
            }
          </div>
        }
      </div>
      <div class="tb-legend">
        @for (item of series(); track item.key; let i = $index) {
          <button type="button" class="tb-legend-item" [class.tb-legend-item-hidden]="isHidden(item.key)"
                  [attr.aria-pressed]="!isHidden(item.key)" (click)="toggle(item.key)">
            <span class="tb-legend-key" [style.--tb-series]="seriesColor(item, i)"></span>
            <span class="tb-legend-label">{{ item.label | translate }}</span>
            <span class="tb-legend-value">{{ item.format(item.current) }}</span>
          </button>
        }
      </div>
    `,
    styleUrls: ['./rate-chart.component.scss'],
    imports: [DatePipe, TranslateModule],
    changeDetection: ChangeDetectionStrategy.OnPush
})
export class RateChartComponent {

  readonly series = input<RateChartSeries[]>([]);
  // The period the chart covers, already translated (e.g. "1 hour"); without it the label only names the series.
  readonly windowLabel = input<string | null>(null);

  readonly hoverIndex = signal<number | null>(null);

  readonly padX = PAD_X;
  readonly padTop = PAD_TOP;
  // Gradient ids are document-global, and two charts may colour the same key differently.
  readonly fillIdPrefix = `tb-rate-chart-${nextChartId++}-fill-`;

  private readonly chartBox = viewChild.required<ElementRef<HTMLElement>>('chartBox');
  private readonly size = signal({width: WIDTH, height: HEIGHT});
  readonly width = computed(() => this.size().width);
  readonly height = computed(() => this.size().height);
  readonly plotBottom = computed(() => this.height() - AXIS_BAND);

  private readonly hidden = signal<ReadonlySet<string>>(new Set());
  private readonly translate = inject(TranslateService);
  private readonly lang = currentLangSignal(this.translate);

  readonly ariaLabel = computed(() => {
    const series = this.visibleSeries().map(item => this.instant(item.label)).join(', ');
    const window = this.windowLabel()?.trim();
    return window
      ? this.instant('rate-chart.aria-label', {series, window})
      : this.instant('rate-chart.aria-label-no-window', {series});
  });

  constructor() {
    const destroyRef = inject(DestroyRef);
    afterNextRender(() => {
      const observer = new ResizeObserver(([entry]) => {
        const {width, height} = entry.contentRect;
        if (width > 0 && height > 0) {
          this.size.set({width: Math.round(width), height: Math.round(height)});
        }
      });
      observer.observe(this.chartBox().nativeElement);
      destroyRef.onDestroy(() => observer.disconnect());
    });
  }

  readonly visibleSeries = computed(() => this.series().filter(item => !this.hidden().has(item.key)));

  readonly geometry = computed<ChartGeometry | null>(() => {
    const visible = this.visibleSeries();
    const timeline = [...new Set(visible.flatMap(item => item.points.map(point => point.ts)))].sort((a, b) => a - b);
    if (timeline.length < 2) {
      return null;
    }
    const startTs = timeline[0];
    const endTs = timeline[timeline.length - 1];
    const max = Math.max(1, ...visible.flatMap(item => item.points.map(point => point.value)));
    const width = this.width();
    const plotBottom = this.plotBottom();
    const toX = (ts: number) => PAD_X + (ts - startTs) / (endTs - startTs) * (width - 2 * PAD_X);
    const toY = (value: number) => plotBottom - value / max * (plotBottom - PAD_TOP);
    const paths = visible
      .filter(item => item.points.length)
      .map(item => {
        const color = this.seriesColor(item, this.series().indexOf(item));
        const coords = item.points.map(point => [toX(point.ts), toY(point.value)]);
        const linePath = coords.map(([x, y], i) => `${i ? 'L' : 'M'}${x.toFixed(1)} ${y.toFixed(1)}`).join(' ');
        const [firstX] = coords[0];
        const [endX, endY] = coords[coords.length - 1];
        return {
          key: item.key,
          color,
          linePath,
          areaPath: `${linePath} L ${endX.toFixed(1)} ${plotBottom} L ${firstX.toFixed(1)} ${plotBottom} Z`,
          endX,
          endY
        };
      });
    return {paths, timeline, startTs, endTs, toX, toY};
  });

  readonly hover = computed<HoverVm | null>(() => {
    const geometry = this.geometry();
    const index = this.hoverIndex();
    if (!geometry || index === null || index < 0 || index >= geometry.timeline.length) {
      return null;
    }
    const ts = geometry.timeline[index];
    const rows = this.visibleSeries().flatMap(item => {
      const point = item.points.find(p => p.ts === ts);
      return point ? [{key: item.key, color: this.seriesColor(item, this.series().indexOf(item)), label: item.label,
        value: item.format(point.value), y: geometry.toY(point.value)}] : [];
    });
    return {x: geometry.toX(ts), ts, rows};
  });

  seriesColor(item: RateChartSeries, index: number): string {
    return item.color ?? SERIES_PALETTE[index % SERIES_PALETTE.length];
  }

  isHidden(key: string): boolean {
    return this.hidden().has(key);
  }

  toggle(key: string) {
    this.hidden.update(hidden => {
      const next = new Set(hidden);
      if (!next.delete(key)) {
        next.add(key);
      }
      return next;
    });
  }

  onPointerMove(event: PointerEvent) {
    const geometry = this.geometry();
    if (!geometry) {
      return;
    }
    const rect = (event.currentTarget as SVGSVGElement).getBoundingClientRect();
    const x = (event.clientX - rect.left) / rect.width * this.width();
    let nearest = 0;
    for (let i = 1; i < geometry.timeline.length; i++) {
      if (Math.abs(geometry.toX(geometry.timeline[i]) - x) < Math.abs(geometry.toX(geometry.timeline[nearest]) - x)) {
        nearest = i;
      }
    }
    this.hoverIndex.set(nearest);
  }

  onKeydown(event: KeyboardEvent) {
    const geometry = this.geometry();
    if (!geometry) {
      return;
    }
    const last = geometry.timeline.length - 1;
    switch (event.key) {
      case 'ArrowLeft':
        this.hoverIndex.update(index => Math.max(0, (index ?? last) - 1));
        break;
      case 'ArrowRight':
        this.hoverIndex.update(index => Math.min(last, (index ?? last - 1) + 1));
        break;
      case 'Escape':
        if (this.hoverIndex() === null) {
          return;
        }
        // Only dismisses the readout; a page-level Escape (such as clearing a selection) must not fire as well.
        this.hoverIndex.set(null);
        event.stopPropagation();
        break;
      default:
        return;
    }
    event.preventDefault();
  }

  // Reading the language signal makes computed() translations follow a runtime language switch.
  private instant(key: string, params?: object): string {
    this.lang();
    return this.translate.instant(key, params);
  }
}
