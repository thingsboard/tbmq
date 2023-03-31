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
    [StatsChartType.DROPPED_MESSAGES, '#009B72'],
    [StatsChartType.INCOMING_MESSAGES, '#008FA4'],
    [StatsChartType.OUTGOING_MESSAGES, '#2F4858'],
    [StatsChartType.SESSIONS, '#00968A'],
    [StatsChartType.SUBSCRIPTIONS, '#0086BB']
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
      },
      plugins: {
        zoom: {
          // Container for pan options
          pan: {
            // Boolean to enable panning
            enabled: true,

            // Panning directions. Remove the appropriate direction to disable
            // Eg. 'y' would only allow panning in the y direction
            // A function that is called as the user is panning and returns the
            // available directions can also be used:
            //   mode: function({ chart }) {
            //     return 'xy';
            //   },
            mode: 'xy',

            rangeMin: {
              // Format of min pan range depends on scale type
              x: null,
              y: null
            },
            rangeMax: {
              // Format of max pan range depends on scale type
              x: null,
              y: null
            },

            // On category scale, factor of pan velocity
            speed: 20,

            // Minimal pan distance required before actually applying pan
            threshold: 10,

            // Function called while the user is panning
            onPan({chart}) { console.log(`I'm panning!!!`); },
            // Function called once panning is completed
            onPanComplete({chart}) { console.log(`I was panned!!!`); }
          },

          // Container for zoom options
          zoom: {
            // Boolean to enable zooming
            enabled: true,

            // Enable drag-to-zoom behavior
            drag: true,

            // Drag-to-zoom effect can be customized
            // drag: {
            // 	 borderColor: 'rgba(225,225,225,0.3)'
            // 	 borderWidth: 5,
            // 	 backgroundColor: 'rgb(225,225,225)',
            // 	 animationDuration: 0
            // },

            // Zooming directions. Remove the appropriate direction to disable
            // Eg. 'y' would only allow zooming in the y direction
            // A function that is called as the user is zooming and returns the
            // available directions can also be used:
            //   mode: function({ chart }) {
            //     return 'xy';
            //   },
            mode: 'xy',

            rangeMin: {
              // Format of min zoom range depends on scale type
              x: null,
              y: null
            },
            rangeMax: {
              // Format of max zoom range depends on scale type
              x: null,
              y: null
            },

            // Speed of zoom via mouse wheel
            // (percentage of zoom on a wheel event)
            speed: 0.1,

            // Minimal zoom distance required before actually applying zoom
            threshold: 2,

            // On category scale, minimal zoom level before actually applying zoom
            sensitivity: 3,

            // Function called while the user is zooming
            onZoom({chart}) { console.log(`I'm zooming!!!`); },
            // Function called once zooming is completed
            onZoomComplete({chart}) { console.log(`I was zoomed!!!`); }
          }
        }
      }
    }
  };
}
