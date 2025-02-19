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

import { Injectable } from '@angular/core';
import { defaultHttpOptionsFromConfig, RequestConfig } from './http-utils';
import { Observable } from 'rxjs';
import { HttpClient } from '@angular/common/http';
import { isDefinedAndNotNull } from '@core/utils';
import { AggregationType, Interval } from '@shared/models/time/time.models';
import { Direction } from '@shared/models/page/sort-order';
import { CHART_ALL, TimeseriesData, MAX_DATAPOINTS_LIMIT } from '@shared/models/chart.model';

@Injectable({
  providedIn: 'root'
})
export class StatsService {

  constructor(private http: HttpClient) {
  }

  public getEntityTimeseries(entityId: string, startTs: number, endTs: number, keys: Array<string> = CHART_ALL,
                             limit: number = MAX_DATAPOINTS_LIMIT, agg: AggregationType = AggregationType.NONE, interval?: Interval,
                             orderBy: Direction = Direction.DESC, useStrictDataTypes: boolean = true): Observable<TimeseriesData> {
    let url = `/api/timeseries/${encodeURIComponent(entityId)}/values?keys=${keys.join(',')}&startTs=${startTs}&endTs=${endTs}`;
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
    return this.http.get<TimeseriesData>(url, defaultHttpOptionsFromConfig({
      ignoreLoading: true,
      ignoreErrors: true,
      resendRequest: false
    }));
  }

  public getLatestTimeseries(entityId: string, keys: Array<string> = CHART_ALL,
                             useStrictDataTypes: boolean = true): Observable<TimeseriesData> {
    let url = `/api/timeseries/latest?entityId=${encodeURIComponent(entityId)}&keys=${keys.join(',')}`;
    if (isDefinedAndNotNull(useStrictDataTypes)) {
      url += `&useStrictDataTypes=${useStrictDataTypes}`;
    }
    return this.http.get<TimeseriesData>(url, defaultHttpOptionsFromConfig({
      ignoreLoading: true,
      ignoreErrors: true,
      resendRequest: false
    }));
  }

  public saveTelemetry(entityId: string, data, config?: RequestConfig): Observable<TimeseriesData> {
    const url = `/api/timeseries/${entityId}/save`;
    return this.http.post<TimeseriesData>(url, data, defaultHttpOptionsFromConfig(config));
  }

  public deleteLatestTimeseries(clientId: string, keys: string[], deleteClientSessionCachedStats: boolean, config?: RequestConfig) {
    let url = `/api/timeseries/latest?entityId=${encodeURIComponent(clientId)}`;
    if (keys && keys.length) {
      url += `&keys=${keys.join(',')}`;
    }
    if (isDefinedAndNotNull(deleteClientSessionCachedStats)) {
      url += `&deleteClientSessionCachedStats=${deleteClientSessionCachedStats}`;
    }
    return this.http.delete(url, defaultHttpOptionsFromConfig(config));
  }
}
