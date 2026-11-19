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

import { inject, Injectable } from '@angular/core';
import { defer, Observable, of } from 'rxjs';
import { catchError, finalize, map, scan, shareReplay, startWith, switchMap } from 'rxjs/operators';
import { TimeseriesService } from '@core/http/timeseries.service';
import { ConfigService } from '@core/http/config.service';
import { TimeService } from '@core/services/time.service';
import { AggregationType, MINUTE } from '@shared/models/time/time.models';
import { Direction } from '@shared/models/page/sort-order';
import {
  appendLatestToSeries,
  emptyRateSeries,
  LIVE_STATS_MAX_SAMPLES,
  LIVE_STATS_WINDOW_MS,
  LiveStatsOptions,
  RateSeries,
  seedRateSeries,
  statsIntervalMinutes
} from '@shared/models/live-stats.model';

/**
 * One history-seeded series per (entity, keys, window). Subscribers share the stream; the latest values are fetched
 * once per stats interval, right after the brokers persist them. The poll stops and the stream is dropped when the last
 * subscriber leaves; the next subscription starts over with fresh history.
 */
@Injectable({
  providedIn: 'root'
})
export class LiveStatsService {

  private readonly timeseriesService = inject(TimeseriesService);
  private readonly configService = inject(ConfigService);
  private readonly timeService = inject(TimeService);
  private readonly streams = new Map<string, Observable<RateSeries>>();

  watch(entityId: string, keys: readonly string[], options: LiveStatsOptions = {}): Observable<RateSeries> {
    const windowMs = options.windowMs ?? LIVE_STATS_WINDOW_MS;
    const cacheKey = `${entityId}|${keys.join(',')}|${windowMs}`;
    let stream = this.streams.get(cacheKey);
    if (!stream) {
      stream = this.buildStream(entityId, keys, windowMs).pipe(
        finalize(() => this.streams.delete(cacheKey)),
        shareReplay({bufferSize: 1, refCount: true})
      );
      this.streams.set(cacheKey, stream);
    }
    return stream;
  }

  private buildStream(entityId: string, keys: readonly string[], windowMs: number): Observable<RateSeries> {
    const intervalMinutes = statsIntervalMinutes(this.configService.brokerConfig);
    return defer(() => {
      const endTs = Date.now();
      return this.timeseriesService.getEntityTimeseries(entityId, endTs - windowMs, endTs, [...keys], LIVE_STATS_MAX_SAMPLES,
        AggregationType.NONE, undefined, Direction.ASC).pipe(
        map(history => seedRateSeries(keys, history, intervalMinutes, windowMs)),
        catchError(() => of(emptyRateSeries(keys)))
      );
    }).pipe(
      switchMap(seed => this.timeService.getSyncTimer(intervalMinutes * MINUTE).pipe(
        switchMap(() => this.timeseriesService.getLatestTimeseries(entityId, [...keys]).pipe(catchError(() => of(null)))),
        scan((series, latest) => appendLatestToSeries(series, keys, latest, intervalMinutes, windowMs), seed),
        startWith(seed)
      ))
    );
  }
}
