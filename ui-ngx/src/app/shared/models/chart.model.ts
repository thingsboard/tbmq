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

export const StatsChartTypeTranslationMap = new Map<StatsChartType, string>(
  [
    [StatsChartType.INCOMING_MESSAGES, 'overview.incoming-messages'],
    [StatsChartType.OUTGOING_MESSAGES, 'overview.outgoing-messages'],
    [StatsChartType.DROPPED_MESSAGES, 'overview.dropped-messages'],
    [StatsChartType.SESSIONS, 'overview.sessions'],
    [StatsChartType.SUBSCRIPTIONS, 'overview.subscriptions'],
  ]
);

export const MonitoringChartColorMap = new Map<StatsChartType, string>(
  [
    [StatsChartType.INCOMING_MESSAGES, '#58519E'],
    [StatsChartType.OUTGOING_MESSAGES, '#4A6EA8'],
    [StatsChartType.DROPPED_MESSAGES, '#47848F'],
    [StatsChartType.SESSIONS, '#4FA889'],
    [StatsChartType.SUBSCRIPTIONS, '#499E55']
  ]
);

export function getColor(type: StatsChartType): string {
  return MonitoringChartColorMap.get(type);
}

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
        duration: 500
      },
      layout: {
        padding: 20
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
        y: {
          display: false
        },
        x: {
          display: false,
          type: 'time'
        },
      },
      interaction: {
        mode: 'nearest',
        intersect: true,
        axis: 'x'
      },
      tooltips: {
        mode: 'nearest',
        intersect: true,
        axis: 'x'
      },
      plugins: {
        legend: {
          display: false
        }
      }
    }
  };
}

export function monitoringChartJsParams(index: number, label: string) {
  return {
    type: 'line',
    options: {
      interaction: {
        mode: 'index',
        intersect: false
      },
      stacked: false,
      animation: {
        duration: 500
      },
      scales: {
        y: {
          title: {
            display: false,
            text: label
          }
        },
        x: {
          type: 'time',
          ticks: {
            maxRotation: 0,
            padding: 10,
            labelOffset: 0
          },
        },
      },
      tooltips: {
        mode: 'x'
      },
      plugins: {
        zoom: {
          zoom: {
            drag: {
              enabled: true
            },
            overScaleMode: 'x',
            mode: 'x'
          }
        },
        legend: {
          display: true,
          position: 'bottom',
          align: 'start',
          labels: {
            usePointStyle: true
          }
        }
      }
    }
  };
}
