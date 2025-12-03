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

import _ from 'lodash';
import { Tooltip } from 'chart.js';
import { DataSizeUnitType, DataSizeUnitTypeTranslationMap } from '@shared/models/ws-client.model';

export interface TimeseriesData {
  [key: string]: Array<TsValue>;
}

export interface TsValue {
  ts: number;
  value: number;
  count?: number;
}

export enum StatsChartType {
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
  StatsChartType.sessions,
  StatsChartType.incomingMsgs,
  StatsChartType.outgoingMsgs,
  StatsChartType.droppedMsgs,
  StatsChartType.inboundPayloadTraffic,
  StatsChartType.outboundPayloadTraffic,
  StatsChartType.subscriptions,
  StatsChartType.retainedMsgs,
];
export const CHARTS_STATE_HEALTH = [
  StatsChartType.sessions,
  StatsChartType.subscriptions,
  StatsChartType.retainedMsgs,
  StatsChartType.droppedMsgs,
];
export const CHARTS_TRAFFIC_PERFORMANCE = [
  StatsChartType.incomingMsgs,
  StatsChartType.outgoingMsgs,
  StatsChartType.inboundPayloadTraffic,
  StatsChartType.outboundPayloadTraffic,
];
export const CHARTS_TOTAL_ONLY = [
  StatsChartType.sessions,
  StatsChartType.subscriptions,
  StatsChartType.retainedMsgs,
];
export const TOTAL_KEY = 'total';
export const MAX_DATAPOINTS_LIMIT = 50000;

export enum ChartPage {
  home = 'home',
  monitoring = 'monitoring'
}

export const StatsChartTypeTranslationMap = new Map<string, string>(
  [
    [StatsChartType.incomingMsgs, 'monitoring.chart.incoming-messages'],
    [StatsChartType.outgoingMsgs, 'monitoring.chart.outgoing-messages'],
    [StatsChartType.droppedMsgs, 'monitoring.chart.dropped-messages'],
    [StatsChartType.sessions, 'monitoring.chart.sessions'],
    [StatsChartType.subscriptions, 'monitoring.chart.subscriptions'],
    [StatsChartType.retainedMsgs, 'monitoring.chart.retained-messages'],
    [StatsChartType.inboundPayloadTraffic, 'monitoring.chart.inbound-payload-traffic'],
    [StatsChartType.outboundPayloadTraffic, 'monitoring.chart.outbound-payload-traffic'],
  ]
);

export const ChartTooltipTranslationMap = new Map<string, string>(
  [
    [StatsChartType.incomingMsgs, 'monitoring.chart.incoming-messages-tooltip'],
    [StatsChartType.outgoingMsgs, 'monitoring.chart.outgoing-messages-tooltip'],
    [StatsChartType.droppedMsgs, 'monitoring.chart.dropped-messages-tooltip'],
    [StatsChartType.sessions, 'monitoring.chart.sessions-tooltip'],
    [StatsChartType.subscriptions, 'monitoring.chart.subscriptions-tooltip'],
    [StatsChartType.retainedMsgs, 'monitoring.chart.retained-messages-tooltip'],
    [StatsChartType.inboundPayloadTraffic, 'monitoring.chart.inbound-payload-traffic-tooltip'],
    [StatsChartType.outboundPayloadTraffic, 'monitoring.chart.outbound-payload-traffic-tooltip'],
  ]
);

export const MonitoringChartColorMap = new Map<string, string[]>(
  [
    [StatsChartType.incomingMsgs, ['#58519E', '#4FA889', '#A356D1', '#AAA081', '#D1A656']],
    [StatsChartType.outgoingMsgs, ['#4A6EA8', '#BE4BD1', '#604BDB', '#4A6EA8', '#B39B7C']],
    [StatsChartType.inboundPayloadTraffic, ['#47848F', '#D1A656', '#1860F5', '#499E55', '#BE4BD1']],
    [StatsChartType.outboundPayloadTraffic, ['#4FA889', '#3A4142', '#51C0DB', '#4FA889', '#B38381']],
    [StatsChartType.droppedMsgs, ['#4FA889', '#D1A656', '#1860F5', '#499E55', '#BE4BD1']],
    [StatsChartType.sessions, ['#58519E']],
    [StatsChartType.subscriptions, ['#4A6EA8']],
    [StatsChartType.retainedMsgs, ['#47848F']],
  ]
);

export function getColor(type: string, index: number): string {
  const palette = MonitoringChartColorMap.get(type);
  const normalizedIndex = ((index % palette.length) + palette.length) % palette.length;
  return palette[normalizedIndex];
}

//@ts-ignore
Tooltip.positioners.tbPositioner = function(elements, eventPosition) {
  return {
    x: eventPosition.x,
    y: eventPosition.y
  };
};

const crosshairPlugin = {
  id: 'corsair',
  afterDatasetsDraw(chart) {
    const { ctx, chartArea, corsair } = chart;
    if (corsair?.x && corsair?.y) {
      const { x, y } = corsair;
      if (
        x >= chartArea.left &&
        x <= chartArea.right &&
        y >= chartArea.top &&
        y <= chartArea.bottom
      ) {
        ctx.save();
        ctx.strokeStyle = '#960000';
        ctx.lineWidth = 1;
        ctx.beginPath();
        ctx.moveTo(x, chartArea.top);
        ctx.lineTo(x, chartArea.bottom);
        ctx.stroke();
        ctx.restore();
      }
    }
  },
  afterEvent(chart, evt) {
    const { chartArea } = chart;
    const { x, y } = evt.event;
    if (
      x >= chartArea.left &&
      x <= chartArea.right &&
      y >= chartArea.top &&
      y <= chartArea.bottom
    ) {
      chart.corsair = { x, y };
    } else {
      chart.corsair = null;
    }
    chart.draw();
  },
};

const baseChartConfig = {
  type: 'line',
  plugins: [
    crosshairPlugin,
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
        type: 'time',
        bounds: 'ticks',
        time: {
          round: 'minute',
          unit: 'minute'
        },
        ticks: {
          align: 'auto',
          maxRotation: 0,
          autoSkipPadding: 9,
          major: {
            enabled: true
          },
          font: (ctx) => {
            if (ctx.tick && ctx.tick.major) {
              return {
                weight: 'bold',
                size: 12
              };
            } else {
              return {
                weight: 'normal',
                size: 11
              };
            }
          },
        },
        grid: {
          display: false,
        },
        border: {
          display: false
        },
      },
      y: {
        offset: false,
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
        },
        border: {
          display: false
        },
        grid: {
          display: false,
        },
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

function pageChartConfig(type: ChartPage): object {
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
            time: {
              displayFormats: {
                minute: 'HH:mm'
              }
            },
            ticks: {
              major: {
                enabled: false
              }
            },
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
                if (chartType === StatsChartType.inboundPayloadTraffic || chartType === StatsChartType.outboundPayloadTraffic) {
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

export const chartJsParams = (type: ChartPage) => {
  const baseConfig = _.cloneDeep(baseChartConfig);
  const pageConfig = pageChartConfig(type);
  return _.merge(baseConfig, pageConfig);
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
