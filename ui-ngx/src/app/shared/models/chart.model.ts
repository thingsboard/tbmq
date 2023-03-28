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

const chartColors = ['#65655e', '#c6afb1', '#b0a3d4', '#7d80da', '#79addc'];

export function getColor(index: number): string {
  return chartColors[index];
}

export function defaultChartJsParams() {
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
          top: 0,
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
            padding: -20,
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
