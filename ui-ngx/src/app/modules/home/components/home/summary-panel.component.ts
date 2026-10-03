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

import { AfterViewInit, Component, computed, inject, input, OnDestroy, signal } from '@angular/core';
import { toObservable, toSignal } from '@angular/core/rxjs-interop';
import { Observable, of, retry, Subject } from 'rxjs';
import { HomeCardFilter, HomePageTitleType } from '@shared/models/home-page.model';
import { Router } from '@angular/router';
import { CardTitleButtonComponent } from '@shared/components/button/card-title-button.component';
import { TranslateModule } from '@ngx-translate/core';
import { shareReplay, switchMap, take, takeUntil } from 'rxjs/operators';
import { TimeService } from '@core/services/time.service';
import { LiveStatsService } from '@core/services/live-stats.service';
import { RateChartComponent, RateChartSeries } from '@shared/components/chart/rate-chart.component';
import { compactNumber } from '@shared/models/live-stats.model';
import { ChartKey } from '@shared/models/chart.model';

export interface HomeCardTrend {
  entityId: string;
  keys: ChartKey[];
  labels: Record<string, string>;
  perMinute: boolean;
}


@Component({
    selector: 'tb-home-summary-panel',
    templateUrl: './summary-panel.component.html',
    styleUrls: ['summary-panel.component.scss'],
    imports: [CardTitleButtonComponent, TranslateModule, RateChartComponent]
})
export class SummaryPanelComponent implements AfterViewInit, OnDestroy {

  readonly cardType = input<HomePageTitleType>();
  readonly config = input<HomeCardFilter[]>();
  readonly entities$ = input<Observable<any>>();
  readonly trend = input<HomeCardTrend | null>(null);

  private readonly liveStats = inject(LiveStatsService);
  private readonly trendSeries = toSignal(toObservable(this.trend).pipe(
    switchMap(trend => trend ? this.liveStats.watch(trend.entityId, trend.keys, {perMinute: trend.perMinute}) : of(null))
  ), {initialValue: null});
  readonly trendChart = computed<RateChartSeries[] | null>(() => {
    const trend = this.trend();
    const series = this.trendSeries();
    if (!trend || !series) {
      return null;
    }
    return trend.keys.map(key => {
      const points = series[key] ?? [];
      return {key, label: trend.labels[key], points, current: points[points.length - 1]?.value ?? 0, format: compactNumber};
    });
  });

  readonly latestValues = signal({initialValue: null});

  private destroy$ = new Subject<void>();

  constructor(
    private router: Router,
    private timeService: TimeService,
  ) {
  }

  ngAfterViewInit(): void {
    this.entities$().pipe(take(1)).subscribe(data => this.latestValues.set(data));
    this.startPolling();
  }

  ngOnDestroy(): void {
    this.destroy$.next();
  }

  private startPolling() {
    this.timeService.getSyncTimer()
      .pipe(
        switchMap(() => this.entities$()),
        retry(),
        takeUntil(this.destroy$),
        shareReplay()
      )
      .subscribe(data => this.latestValues.set(data));
  }

  navigateApplyFilter(item: HomeCardFilter) {
    this.router.navigate([item.path], {queryParams: item.filter});
  }
}
