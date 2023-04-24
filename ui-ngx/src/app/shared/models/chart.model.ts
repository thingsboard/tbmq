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

import moment from 'moment/moment';

export interface TimeseriesData {
  [key: string]: Array<TsValue>;
}

export interface TsValue {
  ts: number;
  value: string;
  count?: number;
}

export enum StatsChartType {
  incomingMsgs = 'incomingMsgs',
  outgoingMsgs = 'outgoingMsgs',
  droppedMsgs = 'droppedMsgs',
  sessions = 'sessions',
  subscriptions = 'subscriptions'
}

export const StatsChartTypeTranslationMap = new Map<string, string>(
  [
    [StatsChartType.incomingMsgs, 'overview.incoming-messages'],
    [StatsChartType.outgoingMsgs, 'overview.outgoing-messages'],
    [StatsChartType.droppedMsgs, 'overview.dropped-messages'],
    [StatsChartType.sessions, 'overview.sessions'],
    [StatsChartType.subscriptions, 'overview.subscriptions'],
  ]
);

export const MonitoringChartColorMap = new Map<string, string>(
  [
    [StatsChartType.incomingMsgs, '#58519E'],
    [StatsChartType.outgoingMsgs, '#4A6EA8'],
    [StatsChartType.droppedMsgs, '#47848F'],
    [StatsChartType.sessions, '#4FA889'],
    [StatsChartType.subscriptions, '#499E55']
  ]
);

export function getColor(type: string): string {
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
        duration: 0
      },
      layout: {
        padding: {
          right: 20,
          left: 20,
          bottom: 10,
          top: 10
        }
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
          type: 'time',
          display: false
        }
      },
      interaction: {
        mode: 'nearest',
        intersect: true,
        axis: 'x'
      },
      plugins: {
        legend: {
          display: false
        },
        tooltip: {
          enabled: false
        }
      },
      parsing: {
        xAxisKey: 'ts',
        yAxisKey: 'value'
      }
    }
  };
}

export function monitoringChartJsParams() {
  return {
    type: 'line',
    options: {
      interaction: {
        mode: 'nearest',
        intersect: false,
        axis: 'x'
      },
      layout: {
        padding: {
          right: 20,
          left: 20
        }
      },
      stacked: false,
      animation: false,
      scales: {
        y: {
          min: 0,
          suggestedMax: 1,
          title: {
            display: false
          }
        },
        x: {
          type: 'time',
          time: {
            unit: 'hour'
          },
          ticks: {
            maxRotation: 0,
            padding: 0,
            labelOffset: 0
          }
        }
      },
      plugins: {
        zoom: {
          zoom: {
            drag: {
              enabled: true
            },
            wheel: {
              enabled: false,
            },
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
      },
      parsing: {
        xAxisKey: 'ts',
        yAxisKey: 'value'
      }
    }
  };
}
