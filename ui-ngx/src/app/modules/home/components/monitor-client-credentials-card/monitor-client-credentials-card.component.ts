import { ChangeDetectionStrategy, Component, Input, OnInit } from '@angular/core';
import { Router } from "@angular/router";
import { Observable } from "rxjs";

@Component({
  selector: 'tb-monitor-client-credentials-card',
  templateUrl: './monitor-client-credentials-card.component.html',
  styleUrls: ['./monitor-client-credentials-card.component.scss'],
  changeDetection: ChangeDetectionStrategy.OnPush
})
export class MonitorClientCredentialsCardComponent implements OnInit {

  @Input() isLoading$: Observable<boolean>;
  @Input('data') data: any;

  config = [{
    key: 'devices', label: 'mqtt-client-credentials.type-devices', value: 0
  }, {
    key: 'applications', label: 'mqtt-client-credentials.type-applications', value: 0
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
