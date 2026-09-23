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

import { ChangeDetectionStrategy, Component, computed, input } from '@angular/core';
import { TranslateModule } from '@ngx-translate/core';
import { TsValue } from '@shared/models/chart.model';

interface SparklineGeometry {
  linePath: string;
  areaPath: string;
  endX: number;
  endY: number;
}

const WIDTH = 266;
const HEIGHT = 58;
const PADDING = 5;

@Component({
    selector: 'tb-cluster-sparkline',
    template: `
      <svg class="tb-sparkline" [attr.viewBox]="'0 0 ' + width + ' ' + height" role="img"
           [attr.aria-label]="'monitoring.cluster.outgoing-messages' | translate">
        @if (geometry(); as g) {
          <defs>
            <linearGradient id="tb-cluster-sparkline-gradient" x1="0" y1="0" x2="0" y2="1">
              <stop class="tb-sparkline-stop-top" offset="0%"/>
              <stop class="tb-sparkline-stop-bottom" offset="100%"/>
            </linearGradient>
          </defs>
          <path class="tb-sparkline-area" [attr.d]="g.areaPath"/>
          <path class="tb-sparkline-line" [attr.d]="g.linePath"/>
          <circle class="tb-sparkline-end" [attr.cx]="g.endX" [attr.cy]="g.endY" r="3"/>
        } @else {
          <text class="tb-sparkline-empty" [attr.x]="width / 2" [attr.y]="height / 2"
                text-anchor="middle" dominant-baseline="central">
            {{ 'monitoring.cluster.collecting-samples' | translate }}
          </text>
        }
      </svg>
    `,
    styles: [`
      :host {
        display: block;
      }

      .tb-sparkline {
        display: block;
        width: 100%;
        height: auto;
      }

      .tb-sparkline-stop-top {
        stop-color: var(--tb-primary-500);
        stop-opacity: 0.3;
      }

      .tb-sparkline-stop-bottom {
        stop-color: var(--tb-primary-500);
        stop-opacity: 0;
      }

      .tb-sparkline-area {
        fill: url(#tb-cluster-sparkline-gradient);
      }

      .tb-sparkline-line {
        fill: none;
        stroke: var(--tb-primary-500);
        stroke-width: 1.8;
        stroke-linejoin: round;
        stroke-linecap: round;
      }

      .tb-sparkline-end {
        fill: var(--tb-primary-500);
      }

      .tb-sparkline-empty {
        font-size: 11px;
        fill: rgba(0, 0, 0, 0.38);
      }
    `],
    imports: [TranslateModule],
    changeDetection: ChangeDetectionStrategy.OnPush
})
export class ClusterSparklineComponent {

  readonly points = input<TsValue[]>([]);

  readonly width = WIDTH;
  readonly height = HEIGHT;

  readonly geometry = computed<SparklineGeometry | null>(() => {
    const values = this.points().map(p => p.value);
    if (values.length < 2) {
      return null;
    }
    const min = Math.min(...values);
    const max = Math.max(...values);
    const span = (max - min) || 1;
    const stepX = (WIDTH - 2 * PADDING) / (values.length - 1);
    const coords = values.map((value, i) => [
      PADDING + i * stepX,
      HEIGHT - PADDING - (value - min) / span * (HEIGHT - 2.6 * PADDING)
    ]);
    const linePath = coords.map(([x, y], i) => `${i ? 'L' : 'M'}${x.toFixed(1)} ${y.toFixed(1)}`).join(' ');
    const [endX, endY] = coords[coords.length - 1];
    return {
      linePath,
      areaPath: `${linePath} L ${endX.toFixed(1)} ${HEIGHT} L ${PADDING} ${HEIGHT} Z`,
      endX,
      endY
    };
  });
}
