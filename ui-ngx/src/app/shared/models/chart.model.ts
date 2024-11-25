///
/// Copyright © 2016-2024 The Thingsboard Authors
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

import _ from 'lodash';
import { Tooltip } from 'chart.js';
import { DataSizeUnitType, DataSizeUnitTypeTranslationMap } from '@shared/models/ws-client.model';

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
  subscriptions = 'subscriptions',
  processedBytes = 'processedBytes',
}

export const CHART_ALL = Object.values(StatsChartType);
export const CHART_TOTAL_ONLY = [StatsChartType.sessions, StatsChartType.subscriptions];
export const TOTAL_KEY = 'total';
export const timeseriesDataLimit = 50000;

export enum ChartPage {
  home = 'home',
  monitoring = 'monitoring'
}

export const StatsChartTypeTranslationMap = new Map<string, string>(
  [
    [StatsChartType.incomingMsgs, 'overview.incoming-messages'],
    [StatsChartType.outgoingMsgs, 'overview.outgoing-messages'],
    [StatsChartType.droppedMsgs, 'overview.dropped-messages'],
    [StatsChartType.sessions, 'overview.sessions'],
    [StatsChartType.subscriptions, 'overview.subscriptions'],
    [StatsChartType.processedBytes, 'overview.processed-bytes'],
  ]
);

export const ChartTooltipTranslationMap = new Map<string, string>(
  [
    [StatsChartType.incomingMsgs, 'overview.incoming-messages-tooltip'],
    [StatsChartType.outgoingMsgs, 'overview.outgoing-messages-tooltip'],
    [StatsChartType.droppedMsgs, 'overview.dropped-messages-tooltip'],
    [StatsChartType.sessions, 'overview.sessions-tooltip'],
    [StatsChartType.subscriptions, 'overview.subscriptions-tooltip'],
    [StatsChartType.processedBytes, 'overview.processed-bytes-tooltip'],
  ]
);

export const MonitoringChartColorMap = new Map<string, string[]>(
  [
    [StatsChartType.incomingMsgs, ['#58519E', '#4FA889', '#A356D1', '#AAA081', '#D1A656']],
    [StatsChartType.outgoingMsgs, ['#4A6EA8', '#BE4BD1', '#604BDB', '#4A6EA8', '#B39B7C']],
    [StatsChartType.droppedMsgs, ['#47848F', '#4E73C2', '#1860F5', '#47848F', '#9C8175']],
    [StatsChartType.sessions, ['#4FA889', '#3A4142', '#51C0DB', '#4FA889', '#B38381']],
    [StatsChartType.subscriptions, ['#499E55', '#303836', '#4BD1A9', '#AA799F', '#BE4BD1']],
    [StatsChartType.processedBytes, ['#58519E', '#4FA889', '#A356D1', '#AAA081', '#D1A656']],
  ]
);

export function getColor(type: string, index: number): string {
  try {
    return MonitoringChartColorMap.get(type)[index];
  } catch {
    return MonitoringChartColorMap.get(type)[index - 5];
  }
}

//@ts-ignore
Tooltip.positioners.tbPositioner = function(elements, eventPosition) {
  return {
    x: eventPosition.x,
    y: eventPosition.y
  };
};

const lineChartParams = {
  type: 'line',
  plugins: [
    {
      id: 'corsair',
      afterInit: (chart) => { chart.corsair = {x: 0, y: 0}; },
      afterEvent: (chart, evt) => {
        const {chartArea: {top, bottom}} = chart;
        const {event: {x, y}} = evt;
        if (y < top || y > bottom || x < 20) {
          chart.corsair = {x, y, draw: false};
          chart.draw();
          return;
        }
        chart.corsair = {x, y, draw: true};
        chart.draw();
      },
      afterDatasetsDraw: (chart, _, opts) => {
        const {ctx, chartArea: {top, bottom}} = chart;
        const {x, draw} = chart.corsair;
        if (!draw) return;
        const {datasets} = chart.data;
        let hasData = false;
        for (let i = 0; i < datasets.length; i += 1) {
          const dataset = datasets[i];
          hasData = dataset.data.length > 0;
        }
        if (hasData) {
          ctx.lineWidth = opts.width || 0;
          ctx.setLineDash(opts.dash || []);
          ctx.strokeStyle = '#960000';

          ctx.save();
          ctx.beginPath();
          ctx.moveTo(x, bottom);
          ctx.lineTo(x, top);
          ctx.stroke();
          ctx.restore();
        }
      }
    }
  ],
  options: {
    animation: false,
    responsive: true,
    maintainAspectRatio: false,
    interaction: {
      mode: 'index',
      intersect: false
    },
    scales: {
      x: {
        type: 'timeseries',
        bounds: 'ticks',
        time: {
          round: 'minute',
          unit: 'minute'
        },
        ticks: {
          align: 'start',
          maxRotation: 0,
          autoSkip: true,
          autoSkipPadding: 6
        },
        grid: {
          display: true,
          drawTicks: false,
          offset: false
        }
      },
      y: {
        offset: true,
        min: 0,
        suggestedMax: 1,
        ticks: {
          maxRotation: 0,
          labelOffset: 0,
          autoSkip: true,
          callback(label, index) {
            if (Math.floor(label) === label) {
              const formatter = Intl.NumberFormat('en', { notation: 'compact'});
              return formatter.format(label);
            }
          }
        }
      }
    },
    plugins: {
      tooltip: {
        position: 'tbPositioner',
        multiKeyBackground: 'transparent'
      }
    },
    layout: {
      padding: {
        right: 0,
        left: 0,
        bottom: 0,
        top: 0
      }
    },
    parsing: {
      xAxisKey: 'ts',
      yAxisKey: 'value'
    }
  }
}

function getParams(type) {
  if (type === ChartPage.monitoring) {
    return {
      options: {
        plugins: {
          zoom: {
            zoom: {
              drag: {
                enabled: true,
                threshold: 5
              },
              wheel: {
                enabled: false,
              },
              mode: 'x',
              onZoomStart: ({ chart, event, point }) => {
                chart.corsair = {x: 0, y: 0, draw: false};
                chart.tooltip.setActiveElements([], { x: 0, y: 0 });
              },
              onZoomComplete: ({ chart }) => {
                chart.update();
              }
            }
          },
          legend: {
            display: false,
            position: 'bottom',
            align: 'start',
            fullSize: true,
            labels: {
              usePointStyle: true,
              pointStyle: 'line',
              padding: 20,
              font: {
                weight: 500,
                size: 12
              },
              generateLabels(chart) {
                const datasets = chart.data.datasets;
                return chart._getSortedDatasetMetas().map((meta, index) => {
                  return {
                    datasetIndex: index,
                    text: datasets[meta.index].label,
                    hidden: !meta.visible,
                    lineWidth: 5,
                    strokeStyle: datasets[meta.index].backgroundColor,
                    pointStyle: 'line'
                  };
                }, this);
              }
            },
            onClick(e, legendItem, legend) {
              const index = legendItem.datasetIndex;
              const ci = legend.chart;
              if (ci.isDatasetVisible(index)) {
                ci.hide(index);
                legendItem.hidden = true;
              } else {
                ci.show(index);
                legendItem.hidden = false;
              }
            },
            onHover: function(e) {
              e.native.target.style.cursor = 'pointer';
            },
            onLeave: function(e) {
              e.native.target.style.cursor = 'default';
            }
          }
        }
      },
    };
  }
  if (type === ChartPage.home) {
    return {
      options: {
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
          x: {
            ticks: {
              font: {
                size: 9
              }
            }
          },
          y: {
            ticks: {
              font: {
                size: 9
              }
            }
          }
        },
        plugins: {
          legend: {
            display: false
          },
          tooltip: {
            callbacks: {
              label: function(context) {
                const chartType = context.dataset.chartType;
                const value = context.parsed.y || 0;
                let label = `${context.dataset.label}: ${value}`;
                if (chartType === StatsChartType.processedBytes) {
                  label += ' ' + DataSizeUnitTypeTranslationMap.get(DataSizeUnitType.BYTE);
                }
                return label;
              }
            }
          }
        }
      }
    }
  }
  return {};
}

export const chartJsParams = (type: string) => {
  const options = getParams(type);
  return _.merge(options, lineChartParams);
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
