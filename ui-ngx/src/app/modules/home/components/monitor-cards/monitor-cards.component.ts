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
