///
/// ThingsBoard, Inc. ("COMPANY") CONFIDENTIAL
///
/// Copyright © 2016-2026 ThingsBoard, Inc. All Rights Reserved.
///
/// NOTICE: All information contained herein is, and remains
/// the property of ThingsBoard, Inc. and its suppliers,
/// if any.  The intellectual and technical concepts contained
/// herein are proprietary to ThingsBoard, Inc.
/// and its suppliers and may be covered by U.S. and Foreign Patents,
/// patents in process, and are protected by trade secret or copyright law.
///
/// Dissemination of this information or reproduction of this material is strictly forbidden
/// unless prior written permission is obtained from COMPANY.
///
/// Access to the source code contained herein is hereby forbidden to anyone except current COMPANY employees,
/// managers or contractors who have executed Confidentiality and Non-disclosure agreements
/// explicitly covering such access.
///
/// The copyright notice above does not evidence any actual or intended publication
/// or disclosure  of  this source code, which includes
/// information that is confidential and/or proprietary, and is a trade secret, of  COMPANY.
/// ANY REPRODUCTION, MODIFICATION, DISTRIBUTION, PUBLIC  PERFORMANCE,
/// OR PUBLIC DISPLAY OF OR THROUGH USE  OF THIS  SOURCE CODE  WITHOUT
/// THE EXPRESS WRITTEN CONSENT OF COMPANY IS STRICTLY PROHIBITED,
/// AND IN VIOLATION OF APPLICABLE LAWS AND INTERNATIONAL TREATIES.
/// THE RECEIPT OR POSSESSION OF THIS SOURCE CODE AND/OR RELATED INFORMATION
/// DOES NOT CONVEY OR IMPLY ANY RIGHTS TO REPRODUCE, DISCLOSE OR DISTRIBUTE ITS CONTENTS,
/// OR TO MANUFACTURE, USE, OR SELL ANYTHING THAT IT  MAY DESCRIBE, IN WHOLE OR IN PART.
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
  KeyDivisor,
  LIVE_STATS_MAX_SAMPLES,
  LIVE_STATS_WINDOW_MS,
  LiveStatsOptions,
  RateSeries,
  seedRateSeries
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
    const gaugeKeys = options.gaugeKeys ?? [];
    const cacheKey = `${entityId}|${keys.join(',')}|${windowMs}|${gaugeKeys.join(',')}`;
    let stream = this.streams.get(cacheKey);
    if (!stream) {
      stream = this.buildStream(entityId, keys, windowMs, new Set(gaugeKeys)).pipe(
        finalize(() => this.streams.delete(cacheKey)),
        shareReplay({bufferSize: 1, refCount: true})
      );
      this.streams.set(cacheKey, stream);
    }
    return stream;
  }

  private buildStream(entityId: string, keys: readonly string[], windowMs: number, gaugeKeys: Set<string>): Observable<RateSeries> {
    const divisor: KeyDivisor = key => gaugeKeys.has(key) ? 1 : this.statsIntervalMinutes();
    return defer(() => {
      const endTs = Date.now();
      return this.timeseriesService.getEntityTimeseries(entityId, endTs - windowMs, endTs, [...keys], LIVE_STATS_MAX_SAMPLES,
        AggregationType.NONE, undefined, Direction.ASC).pipe(
        map(history => seedRateSeries(keys, history, divisor, windowMs)),
        catchError(() => of(emptyRateSeries(keys)))
      );
    }).pipe(
      switchMap(seed => this.timeService.getSyncTimer(this.statsIntervalMinutes() * MINUTE).pipe(
        switchMap(() => this.timeseriesService.getLatestTimeseries(entityId, [...keys]).pipe(catchError(() => of(null)))),
        scan((series, latest) => appendLatestToSeries(series, keys, latest, divisor, windowMs), seed),
        startWith(seed)
      ))
    );
  }

  private statsIntervalMinutes(): number {
    return this.configService.brokerConfig?.statsCollectionInterval || 1;
  }
}
