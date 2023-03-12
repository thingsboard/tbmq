import { Component, OnInit } from '@angular/core';
import { of } from "rxjs";
import { ConfigParams, ConfigParamsTranslationMap } from "@shared/models/stats.model";

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

  constructor() { }

  ngOnInit(): void {
  }

}
