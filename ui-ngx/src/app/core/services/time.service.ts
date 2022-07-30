///
/// Copyright © 2016-2022 The Thingsboard Authors
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
import { HttpClient } from '@angular/common/http';
import { Observable } from 'rxjs';
import { defaultHttpOptions } from '@core/http/http-utils';
import { map } from 'rxjs/operators';

const MIN_LIMIT = 7;

const MAX_DATAPOINTS_LIMIT = 500;

@Injectable({
  providedIn: 'root'
})
export class TimeService {

  private maxDatapointsLimit = MAX_DATAPOINTS_LIMIT;

  constructor(
    private http: HttpClient
  ) {}

  public loadMaxDatapointsLimit(): Observable<number> {
    return this.http.get<number>('/api/dashboard/maxDatapointsLimit',
      defaultHttpOptions(true)).pipe(
      map((limit) => {
        this.maxDatapointsLimit = limit;
        if (!this.maxDatapointsLimit || this.maxDatapointsLimit <= MIN_LIMIT) {
          this.maxDatapointsLimit = MIN_LIMIT + 1;
        }
        return this.maxDatapointsLimit;
      })
    );
  }
}
