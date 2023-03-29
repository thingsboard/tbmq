///
/// Copyright © 2016-2023 The Thingsboard Authors
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

import { FixedWindow } from '@shared/models/time/time.models';

export interface TimeseriesData {
  [key: string]: Array<TsValue>;
}

export interface TsValue {
  ts: number;
  value: string;
  count?: number;
}

export enum StatsChartType {
  INCOMING_MESSAGES = 'INCOMING_MESSAGES',
  OUTGOING_MESSAGES = 'OUTGOING_MESSAGES',
  DROPPED_MESSAGES = 'DROPPED_MESSAGES',
  SESSIONS = 'SESSIONS',
  SUBSCRIPTIONS = 'SUBSCRIPTIONS'
}

export enum MonitoringChartType {
  OUTGOING_MESSAGES = 'OUTGOING_MESSAGES',
  SESSIONS = 'SESSIONS',
  SUBSCRIPTIONS = 'SUBSCRIPTIONS'
}

export const StatsChartTypeTranslationMap = new Map<StatsChartType, string>(
  [
    [StatsChartType.INCOMING_MESSAGES, 'overview.incoming-messages'],
    [StatsChartType.OUTGOING_MESSAGES, 'overview.outgoing-messages'],
    [StatsChartType.DROPPED_MESSAGES, 'overview.dropped-messages'],
    [StatsChartType.SESSIONS, 'overview.sessions'],
    [StatsChartType.SUBSCRIPTIONS, 'overview.subscriptions'],
  ]
);
export const MonitoringChartTypeTranslationMap = new Map<MonitoringChartType, string>(
  [
    [MonitoringChartType.OUTGOING_MESSAGES, 'monitoring.messages'],
    [MonitoringChartType.SESSIONS, 'monitoring.session'],
    [MonitoringChartType.SUBSCRIPTIONS, 'monitoring.subscription']
  ]
);

export function getColor(type: StatsChartType): string {
  return MonitoringChartColorMap.get(type);
}

export const MonitoringChartColorMap = new Map<StatsChartType, string>(
  [
    [StatsChartType.DROPPED_MESSAGES, '#009B72'],
    [StatsChartType.INCOMING_MESSAGES, '#008FA4'],
    [StatsChartType.OUTGOING_MESSAGES, '#2F4858'],
    [StatsChartType.SESSIONS, '#00968A'],
    [StatsChartType.SUBSCRIPTIONS, '#0086BB']
  ]
);

export function homeChartJsParams() {
  return {
    type: 'line',
    options: {
      elements: {
        point: {
          pointStyle: 'circle',
          radius: 0
        }
      },
      animation: {
        duration: 1000
      },
      layout: {
        padding: {
          left: 20,
          right: 20,
          top: 20,
          bottom: 0
        }
      },
      legend: {
        display: false
      },
      title: {
        display: false,
        text: null,
        lineHeight: 0,
        padding: 0,
        fontStyle: 'normal',
        fontColor: '#000000',
        fontSize: 12
      },
      scales: {
        yAxes: [{
          display: false,
          type: 'linear',
          gridLines: {
            display: false
          },
          ticks: {
            min: 0
          }
        }],
        xAxes: [{
          type: 'time',
          gridLines: {
            display: false
          },
          ticks: {
            display: false,
            fontSize: 8,
            fontColor: '#000000',
            fontFamily: 'sans serif',
            autoSkip: true,
            autoSkipPadding: (60 * 60 * 1000),
            maxRotation: 0,
            padding: 0,
            labelOffset: 0
          },
          distribution: 'series',
          bounds: 'ticks',
          time: {
            round: 'second',
            unitStepSize: 5 * 60 * 1000,
            unit: 'millisecond',
            displayFormats: {
              millisecond: 'hh:mm'
            }
          }
        }]
      },
      tooltips: {
        mode: 'x-axis',
        intersect: true,
        axis: 'x'
      }
    }
  };
}

export function monitoringChartJsParams(index: number, label: string, fixedWindowTimeMs: FixedWindow) {
  const rangeMs = fixedWindowTimeMs?.endTimeMs - fixedWindowTimeMs?.startTimeMs || 60 * 1000;
  return {
    type: 'line',
    options: {
      elements: {
        point: {
          pointStyle: 'circle',
          radius: 1
        }
      },
      animation: {
        duration: 1000
      },
      legend: {
        display: true,
        position: 'bottom',
        align: 'start',
        labels: {
          fontSize: 14,
          boxWidth: 14,
          usePointStyle: true
        }
      },
      title: {
        display: false,
        position: 'bottom',
        fontColor: 'rgba(0,0,0,0.87)',
        fontSize: 20,
        text: label
      },
      scales: {
        yAxes: [{
          display: true
        }],
        xAxes: [{
          type: 'time',
          ticks: {
            display: true,
            // fontColor: getColor(index),
            // fontFamily: 'sans serif',
            // autoSkip: true,
            // autoSkipPadding: (60 * 60 * 1000),
            maxRotation: 0,
            // padding: 20,
            // labelOffset: 0
          },
          time: {
            round: 'second',
            unitStepSize: rangeMs,
            unit: 'millisecond',
            displayFormats: {
              millisecond: 'hh:mm'
            }
          }
        }]
      },
      tooltips: {
        mode: 'x-axis'
      }
    }
  };
}
