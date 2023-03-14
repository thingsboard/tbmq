import { Component, OnInit } from '@angular/core';
import { of } from "rxjs";
import { ConfigParams, ConfigParamsTranslationMap } from "@shared/models/stats.model";
import { ActionNotificationShow } from "@core/notification/notification.actions";
import { Store } from "@ngrx/store";
import { AppState } from "@core/core.state";
import { TranslateService } from "@ngx-translate/core";

@Component({
  selector: 'tb-card-config',
  templateUrl: './card-config.component.html',
  styleUrls: ['./card-config.component.scss']
})
export class CardConfigComponent implements OnInit {

  configParamsTranslationMap = ConfigParamsTranslationMap;
  configParams = ConfigParams;

  overviewConfig: any = of([
    {
      key: 'PORT_MQTT',
      value: 1883
    },
    {
      key: 'TLS_TCP_PORT',
      value: 8883
    },
    {
      key: 'TCP_LISTENER',
      value: true
    },
    {
      key: 'TCP_LISTENER_MAX_PAYLOAD_SIZE',
      value: '65536 bytes'
    },
    {
      key: 'TLS_LISTENER',
      value: true
    },
    {
      key: 'TLS_LISTENER_MAX_PAYLOAD_SIZE',
      value: '65536 bytes'
    },
    {
      key: 'BASIC_AUTH',
      value: true
    },
    {
      key: 'X509_CERT_CHAIN_AUTH',
      value: true
    }
  ]);

  constructor(protected store: Store<AppState>,
              private translate: TranslateService) { }

  ngOnInit(): void {
  }

  onCopy() {
    const message = this.translate.instant('config.port-copied');
    this.store.dispatch(new ActionNotificationShow(
      {
        message,
        type: 'success',
        duration: 1500,
        verticalPosition: 'top',
        horizontalPosition: 'left'
      }));
  }

}
