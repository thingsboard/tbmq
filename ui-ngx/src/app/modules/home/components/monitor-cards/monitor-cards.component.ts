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

import { AfterViewInit, Component, Input, OnInit } from '@angular/core';
import { Router } from "@angular/router";
import { forkJoin, Observable, Subject, timer } from "rxjs";
import { retry, switchMap, takeUntil } from "rxjs/operators";
import { StatsService } from "@core/http/stats.service";

@Component({
  selector: 'tb-monitor-cards',
  templateUrl: './monitor-cards.component.html',
  styleUrls: ['./monitor-cards.component.scss']
})
export class MonitorCardsComponent implements OnInit, AfterViewInit {

  @Input()
  isLoading$: Observable<boolean>

  pollData$: Observable<any>;
  private stopPolling = new Subject();

  sessionsValue: any;
  clientCredentialsValue: any;

  constructor(private statsService: StatsService,
              private router: Router) { }

  ngOnInit(): void {
    this.pollData$ = timer(0, 5000).pipe(
      switchMap(() => forkJoin(
        this.statsService.getSessionsInfoMock(),
        this.statsService.getClientCredentialsInfoMock()
      )),
      retry(),
      takeUntil(this.stopPolling)
    );
  }

  viewDocumentation(type) {
    this.router.navigateByUrl('');
  }

  navigateToPage(type) {
    this.router.navigateByUrl('');
  }

  ngAfterViewInit(): void {
    this.startPolling();
  }

  startPolling() {
    this.pollData$.subscribe(data => {
      this.sessionsValue = data[0];
      this.clientCredentialsValue = data[1];
    });
  }
}
