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

import type { EChartsOption, LineSeriesOption, TopLevelFormatterParams } from 'echarts/types/dist/shared';
import moment from 'moment';
import { formatLargeNumber } from '@core/utils';

export interface TimeseriesData {
  [key: string]: Array<TsValue>;
}

export interface TsValue {
  ts: number;
  value: number;
  count?: number;
}

export enum ChartKey {
  incomingMsgs = 'incomingMsgs',
  outgoingMsgs = 'outgoingMsgs',
  droppedMsgs = 'droppedMsgs',
  sessions = 'sessions',
  subscriptions = 'subscriptions',
  retainedMsgs = 'retainedMsgs',
  inboundPayloadTraffic = 'inboundPayloadTraffic',
  outboundPayloadTraffic = 'outboundPayloadTraffic',
}

export const CHARTS_HOME = [
  ChartKey.sessions,
  ChartKey.incomingMsgs,
  ChartKey.outgoingMsgs,
  ChartKey.droppedMsgs,
  ChartKey.inboundPayloadTraffic,
  ChartKey.outboundPayloadTraffic,
  ChartKey.subscriptions,
  ChartKey.retainedMsgs,
];
export const CHARTS_STATE_HEALTH = [
  ChartKey.sessions,
  ChartKey.subscriptions,
  ChartKey.retainedMsgs,
  ChartKey.droppedMsgs,
];
export const CHARTS_TRAFFIC_PERFORMANCE = [
  ChartKey.incomingMsgs,
  ChartKey.outgoingMsgs,
  ChartKey.inboundPayloadTraffic,
  ChartKey.outboundPayloadTraffic,
];
export const CHARTS_TOTAL_ENTITY_ID_ONLY = [
  ChartKey.sessions,
  ChartKey.subscriptions,
  ChartKey.retainedMsgs,
];
export const TOTAL_ENTITY_ID = 'total';
export const MAX_DATAPOINTS_LIMIT = 50000;

export enum ChartView {
  compact = 'compact',
  detailed = 'detailed',
}

export const ChartDataKeyTranslationMap = new Map<ChartKey, string>(
  [
    [ChartKey.incomingMsgs, 'monitoring.chart.incoming-messages'],
    [ChartKey.outgoingMsgs, 'monitoring.chart.outgoing-messages'],
    [ChartKey.droppedMsgs, 'monitoring.chart.dropped-messages'],
    [ChartKey.sessions, 'monitoring.chart.sessions'],
    [ChartKey.subscriptions, 'monitoring.chart.subscriptions'],
    [ChartKey.retainedMsgs, 'monitoring.chart.retained-messages'],
    [ChartKey.inboundPayloadTraffic, 'monitoring.chart.inbound-payload-traffic'],
    [ChartKey.outboundPayloadTraffic, 'monitoring.chart.outbound-payload-traffic'],
  ]
);

export const ChartDataKeyTooltipTranslationMap = new Map<ChartKey, string>(
  [
    [ChartKey.incomingMsgs, 'monitoring.chart.incoming-messages-tooltip'],
    [ChartKey.outgoingMsgs, 'monitoring.chart.outgoing-messages-tooltip'],
    [ChartKey.droppedMsgs, 'monitoring.chart.dropped-messages-tooltip'],
    [ChartKey.sessions, 'monitoring.chart.sessions-tooltip'],
    [ChartKey.subscriptions, 'monitoring.chart.subscriptions-tooltip'],
    [ChartKey.retainedMsgs, 'monitoring.chart.retained-messages-tooltip'],
    [ChartKey.inboundPayloadTraffic, 'monitoring.chart.inbound-payload-traffic-tooltip'],
    [ChartKey.outboundPayloadTraffic, 'monitoring.chart.outbound-payload-traffic-tooltip'],
  ]
);

export const ChartColorMap = new Map<ChartKey, string[]>(
  [
    [ChartKey.incomingMsgs, ['#58519E', '#4FA889', '#A356D1', '#AAA081', '#D1A656']],
    [ChartKey.outgoingMsgs, ['#4A6EA8', '#BE4BD1', '#604BDB', '#4A6EA8', '#B39B7C']],
    [ChartKey.inboundPayloadTraffic, ['#47848F', '#D1A656', '#1860F5', '#499E55', '#BE4BD1']],
    [ChartKey.outboundPayloadTraffic, ['#4FA889', '#3A4142', '#51C0DB', '#4FA889', '#B38381']],
    [ChartKey.droppedMsgs, ['#4FA889', '#D1A656', '#1860F5', '#499E55', '#BE4BD1']],
    [ChartKey.sessions, ['#58519E']],
    [ChartKey.subscriptions, ['#4A6EA8']],
    [ChartKey.retainedMsgs, ['#47848F']],
  ]
);

export function getColor(type: ChartKey, index: number): string {
  const palette = ChartColorMap.get(type);
  const normalizedIndex = ((index % palette.length) + palette.length) % palette.length;
  return palette[normalizedIndex];
}

export interface LegendConfig {
  showMin: boolean;
  showMax: boolean;
  showAvg: boolean;
  showTotal: boolean;
  showLatest: boolean;
}

export interface LegendKey {
  dataKey: DataKey;
  dataIndex: number;
}

export interface DataKey {
  label: string;
  hidden: boolean;
  color: string;
}

export interface ChartSeries {
  name: string;
  color: string;
  data: TsValue[];
  visible: boolean;
}

// Thingsboard chart color scheme (light mode)
const tbColors = {
  axisLine: 'rgba(0, 0, 0, 0.54)',
  axisLabel: 'rgba(0, 0, 0, 0.54)',
  axisSplitLine: 'rgba(0, 0, 0, 0.12)',
  tooltipLabel: 'rgba(0, 0, 0, 0.76)',
  tooltipValue: 'rgba(0, 0, 0, 0.87)',
  tooltipDate: 'rgba(0, 0, 0, 0.76)',
  tooltipBackground: 'rgba(255, 255, 255, 0.76)',
  tooltipBorder: 'rgba(0, 0, 0, 0.08)',
};

export interface XAxisFormat {
  unit: 'minute' | 'hour' | 'day';
  formatPattern: string;
}

export function pickXAxisFormat(startMs: number, endMs: number): XAxisFormat {
  const hours = (endMs - startMs) / (1000 * 60 * 60);
  if (hours <= 24) {
    return { unit: 'minute', formatPattern: 'HH:mm' };
  }
  if (hours <= 24 * 30) {
    return { unit: 'day', formatPattern: 'MMM-DD HH:mm' };
  }
  return { unit: 'day', formatPattern: 'MMM-DD' };
}

export interface TooltipFormatterContext {
  intervalUnit: string;
  totalEntityIdOnly: boolean;
}

export function buildTooltipFormatter(ctx: () => TooltipFormatterContext): (params: TopLevelFormatterParams) => HTMLElement | string {
  return (params: TopLevelFormatterParams) => {
    const list = Array.isArray(params) ? params : [params];
    if (!list.length) {
      return '';
    }
    const { intervalUnit, totalEntityIdOnly } = ctx();
    const ts = (list[0].value as [number, number])[0];
    const dateText = moment(ts).format('YYYY-MM-DD HH:mm');

    const root = document.createElement('div');
    root.style.cssText = 'display:flex;flex-direction:column;gap:8px;padding:4px 2px;min-width:160px;';

    const dateEl = document.createElement('div');
    dateEl.textContent = dateText;
    dateEl.style.cssText = `font-size:11px;font-weight:500;color:${tbColors.tooltipDate};letter-spacing:0.02em;`;
    root.appendChild(dateEl);

    for (const p of list) {
      const value = (p.value as [number, number])[1];
      if (value == null) {
        continue;
      }
      if (value === 0 && p.seriesName !== TOTAL_ENTITY_ID && !totalEntityIdOnly) {
        continue;
      }
      const row = document.createElement('div');
      row.style.cssText = 'display:flex;align-items:center;gap:12px;justify-content:space-between;';

      const label = document.createElement('div');
      label.style.cssText = 'display:flex;align-items:center;gap:8px;';

      const dot = document.createElement('div');
      dot.style.cssText = `width:8px;height:8px;border-radius:50%;background:${p.color};`;
      label.appendChild(dot);

      const name = document.createElement('div');
      name.textContent = p.seriesName ?? '';
      name.style.cssText = `font-size:12px;font-weight:400;color:${tbColors.tooltipLabel};`;
      label.appendChild(name);

      const val = document.createElement('div');
      const formattedValue = Number.isInteger(value) ? value : Number(value).toFixed(2);
      val.textContent = intervalUnit
        ? `${formatLargeNumber(formattedValue as number)} ${intervalUnit}`
        : `${formatLargeNumber(formattedValue as number)}`;
      val.style.cssText = `font-size:13px;font-weight:600;color:${tbColors.tooltipValue};text-align:end;`;

      row.appendChild(label);
      row.appendChild(val);
      root.appendChild(row);
    }
    return root;
  };
}

export interface BuildOptionParams {
  view: ChartView;
  enableZoom: boolean;
  series: LineSeriesOption[];
  xMin: number;
  xMax: number;
  tooltipFormatter: (params: TopLevelFormatterParams) => HTMLElement | string;
}

export function buildEChartsOption(params: BuildOptionParams): EChartsOption {
  const { view, enableZoom, series, xMin, xMax, tooltipFormatter } = params;
  const compact = view === ChartView.compact;
  const xFormat = pickXAxisFormat(xMin, xMax);

  return {
    animation: false,
    grid: {
      left: compact ? 4 : 8,
      right: compact ? 12 : 16,
      top: compact ? 12 : 16,
      bottom: enableZoom ? 56 : (compact ? 4 : 8),
      containLabel: true,
    },
    tooltip: {
      trigger: 'axis',
      confine: true,
      backgroundColor: tbColors.tooltipBackground,
      borderColor: tbColors.tooltipBorder,
      borderWidth: 1,
      padding: [8, 12],
      extraCssText: 'backdrop-filter: blur(4px); box-shadow: 0 2px 8px rgba(0,0,0,0.12); border-radius: 4px;',
      axisPointer: {
        type: 'line',
        lineStyle: {
          color: '#960000',
          width: 1,
        },
      },
      formatter: tooltipFormatter,
    },
    xAxis: {
      type: 'time',
      min: xMin,
      max: xMax,
      boundaryGap: false as any,
      axisLine: { show: false },
      axisTick: { show: false },
      splitLine: { show: false },
      axisLabel: {
        color: tbColors.axisLabel,
        fontSize: compact ? 10 : 11,
        hideOverlap: true,
        formatter: (value: number) => moment(value).format(xFormat.formatPattern),
      },
    },
    yAxis: {
      type: 'value',
      min: 0,
      minInterval: 1,
      axisLine: { show: false },
      axisTick: { show: false },
      splitLine: { show: false },
      axisLabel: {
        color: tbColors.axisLabel,
        fontSize: compact ? 9 : 11,
        formatter: (value: number) => {
          if (Number.isInteger(value)) {
            return new Intl.NumberFormat('en', { notation: 'compact' }).format(value);
          }
          return String(value);
        },
      },
    },
    dataZoom: enableZoom
      ? [
          {
            type: 'slider',
            xAxisIndex: 0,
            show: true,
            height: 32,
            bottom: 8,
            borderColor: 'transparent',
            backgroundColor: 'rgba(0, 0, 0, 0.02)',
            fillerColor: 'rgba(88, 81, 158, 0.12)',
            handleStyle: {
              color: '#ffffff',
              borderColor: 'rgba(0, 0, 0, 0.32)',
              borderWidth: 1,
              shadowBlur: 2,
              shadowColor: 'rgba(0, 0, 0, 0.12)',
              shadowOffsetX: 0,
              shadowOffsetY: 1,
            },
            moveHandleSize: 8,
            moveHandleStyle: {
              color: 'rgba(0, 0, 0, 0.16)',
            },
            dataBackground: {
              lineStyle: { color: 'rgba(0, 0, 0, 0.16)', width: 1 },
              areaStyle: { color: 'rgba(0, 0, 0, 0.04)' },
            },
            selectedDataBackground: {
              lineStyle: { color: 'rgba(88, 81, 158, 0.6)', width: 1 },
              areaStyle: { color: 'rgba(88, 81, 158, 0.08)' },
            },
            textStyle: {
              color: 'rgba(0, 0, 0, 0.54)',
              fontSize: 10,
            },
            labelFormatter: (value: number) => moment(value).format('MMM-DD HH:mm'),
          },
        ]
      : undefined,
    series,
  };
}

export function buildSeries(
  name: string,
  color: string,
  data: TsValue[] | null,
  visible: boolean
): LineSeriesOption {
  return {
    type: 'line',
    name,
    showSymbol: false,
    symbol: 'circle',
    symbolSize: 6,
    sampling: 'lttb',
    smooth: 0.2,
    smoothMonotone: 'x',
    lineStyle: { color, width: 3 },
    itemStyle: { color },
    emphasis: {
      lineStyle: { width: 4 },
    },
    data: (data ?? []).map(d => [d.ts, d.value] as [number, number]),
    z: 2,
    silent: !visible,
  };
}
