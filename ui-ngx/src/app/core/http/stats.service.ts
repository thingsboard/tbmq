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

import { Injectable } from '@angular/core';
import { defaultHttpOptionsFromConfig, RequestConfig } from './http-utils';
import { Observable, of } from 'rxjs';
import { HttpClient } from '@angular/common/http';
import {
  AggregationType,
  DataSortOrder,
  StatChartData,
  StatChartType,
  TimeseriesData,
  TsValue
} from "@shared/models/stats.model";
import { isDefinedAndNotNull } from "@core/utils";
import { StatsType } from "@home/components/chart/chart.component";

@Injectable({
  providedIn: 'root'
})
export class StatsService {

  constructor(private http: HttpClient) {
  }

  public getEntityTimeseries(keys: Array<string>, startTs: number, endTs: number,
                             limit: number = 100, agg: AggregationType = AggregationType.NONE, interval?: number,
                             orderBy: DataSortOrder = DataSortOrder.DESC, useStrictDataTypes: boolean = false,
                             config?: RequestConfig): Observable<TimeseriesData> {
    let url = `/api/plugins/telemetry/values/timeseries?keys=${keys.join(',')}&startTs=${startTs}&endTs=${endTs}`;
    if (isDefinedAndNotNull(limit)) {
      url += `&limit=${limit}`;
    }
    if (isDefinedAndNotNull(agg)) {
      url += `&agg=${agg}`;
    }
    if (isDefinedAndNotNull(interval)) {
      url += `&interval=${interval}`;
    }
    if (isDefinedAndNotNull(orderBy)) {
      url += `&orderBy=${orderBy}`;
    }
    if (isDefinedAndNotNull(useStrictDataTypes)) {
      url += `&useStrictDataTypes=${useStrictDataTypes}`;
    }

    return this.http.get<TimeseriesData>(url, defaultHttpOptionsFromConfig(config));
  }

  public getEntityTimeseriesMock(config?: RequestConfig): Observable<StatChartData> {
    const dataset: StatChartData = {};
    for (let chart in StatChartType) {
      const randomValuesNumber = Math.floor(Math.random() * 10) + 5;
      const data = [];
      let ts = 1609459200000;
      for (let i = 0; i < randomValuesNumber; i++) {
        ts += 100000;
        let value = Math.floor(Math.random() * 10) + 5;
        data.push({ value, ts });
      }
      dataset[chart] = data;
    }
    return of(dataset);
  }
}
