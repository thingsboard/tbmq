import { ChangeDetectionStrategy, Component, Input, OnInit } from '@angular/core';
import { Router } from "@angular/router";
import { TranslateService } from "@ngx-translate/core";
import { Observable } from "rxjs";

@Component({
  selector: 'tb-monitor-sessions-card',
  templateUrl: './monitor-sessions-card.component.html',
  styleUrls: ['./monitor-sessions-card.component.scss'],
  changeDetection: ChangeDetectionStrategy.OnPush
})
export class MonitorSessionsCardComponent implements OnInit {

  @Input() isLoading$: Observable<boolean>;

  @Input()
  data: any;

  config = [{
    key: 'connected', label: 'mqtt-client-session.connected', value: 0
  }, {
    key: 'disconnected', label: 'mqtt-client-session.disconnected', value: 0
  }, {
    key: 'total', label: 'home.total', value: 0
  }];

  constructor(protected router: Router) {
  }

  ngOnInit(): void {
    this.config.map(el => {
      el.value = this.data ? this.data[el.key] : 0;
    });
  }

  viewDocumentation(type) {
    this.router.navigateByUrl('');
  }

  navigateToPage(type) {
    this.router.navigateByUrl('');
  }

}
