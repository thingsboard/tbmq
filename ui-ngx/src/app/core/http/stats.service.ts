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
import { AggregationType, DataSortOrder, TimeseriesData, TsValue } from "@shared/models/stats.model";
import { isDefinedAndNotNull } from "@core/utils";

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

  public getEntityTimeseriesMock(option = true, config?: RequestConfig): Observable<TimeseriesData> {
    if (option) {
      //@ts-ignore
      return of({
        "incomingMessages": [
          {
            "value": 2,
            "ts": 1609459200000
          },
          {
            "value": 3,
            "ts": 1609459201000
          }
        ],
        "outgoingMessages": [
          {
            "value": 12,
            "ts": 1609459200000
          },
          {
            "value": 16,
            "ts": 1609459201000
          }
        ]
      });
    } else {
      //@ts-ignore
      return of({
        "incomingMessages": [
          {
            "value": 100,
            "ts": 1609259200000
          },
          {
            "value": 1,
            "ts": 1609269200000
          },
          {
            "value": 66,
            "ts": 1609279200000
          }
        ],
        "outgoingMessages": [
          {
            "value": 532,
            "ts": 1609459200000
          },
          {
            "value": 767,
            "ts": 1609469200000
          },
          {
            "value": 800,
            "ts": 1609479200000
          }
        ]
      });
    }

  }
}
