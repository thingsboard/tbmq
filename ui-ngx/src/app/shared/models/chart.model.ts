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

import { Tooltip } from 'chart.js';

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

export const TOTAL_KEY = 'total';

export const ONLY_TOTAL_KEYS = ['sessions', 'subscriptions'];

export const StatsChartTypeTranslationMap = new Map<string, string>(
  [
    [StatsChartType.incomingMsgs, 'overview.incoming-messages'],
    [StatsChartType.outgoingMsgs, 'overview.outgoing-messages'],
    [StatsChartType.droppedMsgs, 'overview.dropped-messages'],
    [StatsChartType.sessions, 'overview.sessions'],
    [StatsChartType.subscriptions, 'overview.subscriptions']
  ]
);

export const ChartTooltipTranslationMap = new Map<string, string>(
  [
    [StatsChartType.incomingMsgs, 'overview.incoming-messages-tooltip'],
    [StatsChartType.outgoingMsgs, 'overview.outgoing-messages-tooltip'],
    [StatsChartType.droppedMsgs, 'overview.dropped-messages-tooltip'],
    [StatsChartType.sessions, 'overview.sessions-tooltip'],
    [StatsChartType.subscriptions, 'overview.subscriptions-tooltip']
  ]
);

export const MonitoringChartColorMap = new Map<string, string[]>(
  [
    [StatsChartType.incomingMsgs, ['#58519E', '#4FA889', '#A356D1', '#AAA081', '#D1A656']],
    [StatsChartType.outgoingMsgs, ['#4A6EA8', '#BE4BD1', '#604BDB', '#4A6EA8', '#B39B7C']],
    [StatsChartType.droppedMsgs, ['#47848F', '#4E73C2', '#1860F5', '#47848F', '#9C8175']],
    [StatsChartType.sessions, ['#4FA889', '#3A4142', '#51C0DB', '#4FA889', '#B38381']],
    [StatsChartType.subscriptions, ['#499E55', '#303836', '#4BD1A9', '#AA799F', '#BE4BD1']]
  ]
);

export function getColor(type: string, index: number): string {
  try {
    return MonitoringChartColorMap.get(type)[index];
  } catch {
    return MonitoringChartColorMap.get(type)[index - 5];
  }
}

export function homeChartJsParams() {
  // @ts-ignore
  Tooltip.positioners.tbPositioner = function(elements, eventPosition) {
    return {
      x: eventPosition.x,
      y: eventPosition.y
    };
  };
  return {
    type: 'line',
    options: {
      responsive: true,
      animation: false,
      elements: {
        point: {
          pointStyle: 'circle',
          radius: 0
        }
      },
      interaction: {
        mode: 'index',
        intersect: false
      },
      layout: {
        padding: {
          right: 0,
          left: 0,
          bottom: 0,
          top: 0
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
          display: true,
          min: 0,
          suggestedMax: 1,
          ticks: {
            maxRotation: 0,
            labelOffset: 0,
            source: 'auto',
            autoSkip: true,
            font: {
              size: 8
            },
            callback(label, index) {
              if (Math.floor(label) === label) {
                return label;
              }
            }
          }
        },
        x: {
          type: 'time',
          display: true,
          time: {
            round: 'second',
            tooltipFormat: 'HH:mm',
            displayFormats: {
              minute: 'HH:mm'
            }
          },
          ticks: {
            autoSkip: true,
            maxRotation: 0,
            font: {
              size: 8
            },
            callback(val, index) {
              return (index === 0) ? val : ''; // Show only first tick label
            },
          },
          grid: {
            display: true,
            drawTicks: false,
            offset: false
          }
        }
      },
      plugins: {
        legend: {
          display: false
        },
        tooltip: {
          enabled: true,
          position: 'tbPositioner'
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
  // @ts-ignore
  Tooltip.positioners.tbPositioner = function(elements, eventPosition) {
    return {
      x: eventPosition.x,
      y: eventPosition.y
    };
  };
  return {
    type: 'line',
    options: {
      animation: false,
      maintainAspectRatio: false,
      responsive: true,
      interaction: {
        mode: 'index',
        intersect: false
      },
      stacked: false,
      scales: {
        y: {
          min: 0,
          suggestedMax: 1,
          title: {
            display: false
          },
          ticks: {
            font: {
              size: 9
            },
            callback(label, index) {
              if (Math.floor(label) === label) {
                return label;
              }
            }
          }
        },
        x: {
          type: 'time',
          time: {
            round: 'second',
            displayFormats: {
              minute: 'HH:mm'
            }
          },
          ticks: {
            maxRotation: 0,
            labelOffset: 0,
            source: 'auto',
            autoSkip: true,
            font: {
              size: 9
            },
            callback(val, index) {
              return index % 1 === 0 ? val : '';
            },
          },
          grid: {
            display: true,
            drawTicks: false,
            offset: false
          }
        }
      },
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
            mode: 'x'
          }
        },
        legend: {
          display: true,
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
        },
        tooltip: {
          enabled: false,
          external: tbTooltipHandler,
          position: 'tbPositioner'
        }
      },
      parsing: {
        xAxisKey: 'ts',
        yAxisKey: 'value'
      },
      layout: {
        padding: {
          right: 0,
          left: 0,
          bottom: 0,
          top: 0
        }
      }
    },
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
    ]
  };
}

export const tbTooltipHandler = (context) => {
  // Tooltip Element
  const {chart, tooltip} = context;
  const tooltipEl = getTbTooltip(chart);

  // Hide if no tooltip
  if (tooltip.opacity === 0 || tooltip.caretY > 240 || tooltip.caretX < 20) {
    tooltipEl.style.opacity = 0;
    return;
  }

  // Set Text
  if (tooltip.body) {
    const titleLines = tooltip.title || [];
    const bodyLines = tooltip.body.map(b => b.lines);

    const tableHead = document.createElement('thead');

    titleLines.forEach(title => {
      const tr = document.createElement('tr');
      tr.style.borderWidth = '0';

      const th = document.createElement('th');
      th.style.borderWidth = '0';
      const text = document.createTextNode(title);

      th.appendChild(text);
      tr.appendChild(th);
      tableHead.appendChild(tr);
    });

    const tableBody = document.createElement('tbody');
    bodyLines.forEach((body, i) => {
      const span = document.createElement('span');
      span.style.background = tooltip.dataPoints[i]?.dataset.backgroundColor;
      span.style.borderColor = tooltip.dataPoints[i]?.dataset.backgroundColor;
      span.style.borderWidth = '2px';
      span.style.marginRight = '10px';
      span.style.height = '10px';
      span.style.width = '10px';
      span.style.display = 'inline-block';

      const tr = document.createElement('tr');
      tr.style.backgroundColor = 'inherit';
      tr.style.borderWidth = '0';

      const td = document.createElement('td');
      td.style.borderWidth = '0';

      const text = document.createTextNode(body);

      td.appendChild(span);
      td.appendChild(text);
      tr.appendChild(td);
      tableBody.appendChild(tr);
    });

    const tableRoot = tooltipEl.querySelector('table');

    // Remove old children
    while (tableRoot.firstChild) {
      tableRoot.firstChild.remove();
    }

    // Add new children
    tableRoot.appendChild(tableHead);
    tableRoot.appendChild(tableBody);
  }

  const {offsetLeft: positionX, offsetTop: positionY} = chart.canvas;

  // Display, position, and set styles for font
  tooltipEl.style.opacity = 1;
  tooltipEl.style.borderRadius = 10;
  if (tooltip.caretX < 200) {
    tooltipEl.style.left = positionX + tooltip.caretX + 100 + 'px';
  } else {
    tooltipEl.style.left = positionX + tooltip.caretX - 100 + 'px';
  }
  tooltipEl.style.top = positionY + tooltip.caretY + 'px';
  tooltipEl.style.width = '200px';
  tooltipEl.style.font = tooltip.options.bodyFont.string;
  tooltipEl.style.padding = tooltip.options.padding + 'px ' + tooltip.options.padding + 'px';
};

const getTbTooltip = (chart) => {
  let tooltipEl = chart.canvas.parentNode.querySelector('div');
  if (!tooltipEl) {
    tooltipEl = document.createElement('div');
    tooltipEl.style.background = 'rgba(0, 0, 0, 0.7)';
    tooltipEl.style.borderRadius = '3px';
    tooltipEl.style.color = '#ececec';
    tooltipEl.style.opacity = 1;
    tooltipEl.style.pointerEvents = 'none';
    tooltipEl.style.position = 'absolute';
    tooltipEl.style.transform = 'translate(-50%, 0)';
    tooltipEl.style.transition = 'all .1s ease';

    const table = document.createElement('table');
    table.style.margin = '0px';
    tooltipEl.appendChild(table);

    chart.canvas.parentNode.appendChild(tooltipEl);
  }
  return tooltipEl;
};
