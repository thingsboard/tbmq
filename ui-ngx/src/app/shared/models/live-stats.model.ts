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

import { TimeseriesData, TsValue } from '@shared/models/chart.model';
import { HOUR, MINUTE, SECOND } from '@shared/models/time/time.models';
import { isDefinedAndNotNull } from '@core/utils';

export type RateSeries = Record<string, TsValue[]>;

export interface LiveStatsOptions {
  windowMs?: number;
  // Counters are stored per stats-collection interval; per-minute scaling turns them into rates. Gauges (sessions) stay raw.
  perMinute?: boolean;
}

export const LIVE_STATS_REFRESH_MS = 10 * SECOND;
export const LIVE_STATS_WINDOW_MS = HOUR;
export const LIVE_STATS_WINDOWS = [15 * MINUTE, HOUR, 6 * HOUR];
export const LIVE_STATS_MAX_SAMPLES = 720;

const compactFormatter = new Intl.NumberFormat('en', {notation: 'compact', maximumFractionDigits: 1});

export function compactNumber(value: number | null): string {
  return isDefinedAndNotNull(value) ? compactFormatter.format(value) : '—';
}

export function compactBytes(value: number | null): string {
  if (!isDefinedAndNotNull(value)) {
    return '—';
  }
  const units = ['B', 'KB', 'MB', 'GB', 'TB'];
  let scaled = value;
  let unit = 0;
  while (scaled >= 1024 && unit < units.length - 1) {
    scaled /= 1024;
    unit++;
  }
  return `${unit === 0 ? Math.round(scaled) : scaled.toFixed(scaled >= 10 ? 0 : 1)} ${units[unit]}`;
}

export function emptyRateSeries(keys: readonly string[]): RateSeries {
  return Object.fromEntries(keys.map(key => [key, []]));
}

export function scaledValue(count: number | undefined, divisor: number): number {
  return (Number(count) || 0) / (divisor > 0 ? divisor : 1);
}

export function seedRateSeries(keys: readonly string[], history: TimeseriesData, divisor: number, windowMs: number): RateSeries {
  return Object.fromEntries(keys.map(key => [key, trimSamples(
    (history[key] ?? []).map(sample => ({ts: sample.ts, value: scaledValue(sample.value, divisor)})), windowMs)]));
}

export function appendLatestToSeries(series: RateSeries, keys: readonly string[], latest: TimeseriesData | null,
                                     divisor: number, windowMs: number): RateSeries {
  return Object.fromEntries(keys.map(key => {
    const samples = series[key] ?? [];
    const sample = latest?.[key]?.[0];
    const last = samples[samples.length - 1];
    if (!sample || (last && last.ts === sample.ts)) {
      return [key, samples];
    }
    return [key, trimSamples([...samples, {ts: sample.ts, value: scaledValue(sample.value, divisor)}], windowMs)];
  }));
}

function trimSamples(samples: TsValue[], windowMs: number): TsValue[] {
  const byTs = new Map<number, TsValue>();
  for (const sample of samples) {
    byTs.set(sample.ts, sample);
  }
  const sorted = [...byTs.values()].sort((a, b) => a.ts - b.ts);
  const oldest = (sorted[sorted.length - 1]?.ts ?? 0) - windowMs;
  return sorted.filter(sample => sample.ts >= oldest).slice(-LIVE_STATS_MAX_SAMPLES);
}

export interface InlineSparklineOptions {
  width?: number;
  height?: number;
  // Stroke by direction: first vs last sample. Falls back to a neutral grey when flat.
  risingColor?: string;
  fallingColor?: string;
}

// Markup-string sparkline for table cells rendered through [innerHTML]; components use tb-rate-chart instead.
export function inlineSparkline(values: number[], options: InlineSparklineOptions = {}): string {
  if (values.length < 2) {
    return '<span style="color: rgba(0,0,0,.38)">—</span>';
  }
  const width = options.width ?? 96;
  const height = options.height ?? 20;
  const max = Math.max(1, ...values);
  const stepX = width / (values.length - 1);
  const points = values.map((value, i) => `${(i * stepX).toFixed(1)},${(height - 2 - value / max * (height - 4)).toFixed(1)}`).join(' ');
  const first = values[0];
  const last = values[values.length - 1];
  const color = last > first ? (options.risingColor ?? '#d12730') : last < first ? (options.fallingColor ?? '#198038') : 'rgba(0,0,0,.54)';
  return `<svg width="${width}" height="${height}" viewBox="0 0 ${width} ${height}" aria-hidden="true" style="display:block;overflow:visible">` +
    `<polyline fill="none" stroke="${color}" stroke-width="1.6" stroke-linejoin="round" stroke-linecap="round" points="${points}"/>` +
    `</svg>`;
}
