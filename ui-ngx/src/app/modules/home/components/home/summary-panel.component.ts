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

import { AfterViewInit, Component, input, OnDestroy, signal } from '@angular/core';
import { Observable, retry, Subject } from 'rxjs';
import { HomeCardFilter, HomePageTitleType } from '@shared/models/home-page.model';
import { Router } from '@angular/router';
import { CardTitleButtonComponent } from '@shared/components/button/card-title-button.component';
import { TranslateModule } from '@ngx-translate/core';
import { shareReplay, switchMap, take, takeUntil } from 'rxjs/operators';
import { TimeService } from '@core/services/time.service';

@Component({
    selector: 'tb-home-summary-panel',
    templateUrl: './summary-panel.component.html',
    styleUrls: ['summary-panel.component.scss'],
    imports: [CardTitleButtonComponent, TranslateModule]
})
export class SummaryPanelComponent implements AfterViewInit, OnDestroy {

  readonly cardType = input<HomePageTitleType>();
  readonly config = input<HomeCardFilter[]>();
  readonly entities$ = input<Observable<any>>();

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
