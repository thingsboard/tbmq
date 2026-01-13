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

import _ from 'lodash';
import { Tooltip } from 'chart.js';
import { DataSizeUnit, DataSizeUnitTranslationMap } from '@shared/models/ws-client.model';

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
        right: 5,
        left: 0,
        bottom: 0,
        top: 5
      }
    },
    parsing: {
      xAxisKey: 'ts',
      yAxisKey: 'value'
    }
  }
}

function pageChartConfig(type: ChartView): object {
  if (type === ChartView.detailed) {
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
  if (type === ChartView.compact) {
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
                if (chartType === ChartKey.inboundPayloadTraffic || chartType === ChartKey.outboundPayloadTraffic) {
                  label += ' ' + DataSizeUnitTranslationMap.get(DataSizeUnit.BYTE);
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

export const chartJsParams = (type: ChartView) => {
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
