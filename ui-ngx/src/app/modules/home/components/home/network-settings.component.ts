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

import { Component, OnInit } from '@angular/core';
import { ConfigService } from '@core/http/config.service';
import { HomePageTitleType } from '@shared/models/home-page.model';
import { CardTitleButtonComponent } from '@shared/components/button/card-title-button.component';
import { CopyButtonComponent } from '@shared/components/button/copy-button.component';
import { TranslateModule } from '@ngx-translate/core';

interface NetworkParam {
  label: string;
  port: string | number;
  status: string;
  statusKey: string;
  copyable?: boolean;
}

@Component({
  selector: 'tb-home-network-settings',
  templateUrl: './network-settings.component.html',
  styleUrls: ['./network-settings.component.scss'],
  imports: [CardTitleButtonComponent, CopyButtonComponent, TranslateModule]
})
export class NetworkSettingsComponent implements OnInit {

  cardType = HomePageTitleType.NETWORK_CONFIG;
  networkParams: NetworkParam[] = [];

  constructor(private configService: ConfigService) {}

  ngOnInit() {
    const config = this.configService.brokerConfig;

    this.networkParams = [
      { label: 'home.network-settings.tcp', port: config.tcpPort, status: config.tcpListenerEnabled ? 'home.network-settings.enabled' : 'home.network-settings.disabled', statusKey: config.tcpListenerEnabled ? 'enabled' : 'disabled', copyable: config.tcpListenerEnabled },
      { label: 'home.network-settings.tls', port: config.tlsPort, status: config.tlsListenerEnabled ? 'home.network-settings.enabled' : 'home.network-settings.disabled', statusKey: config.tlsListenerEnabled ? 'enabled' : 'disabled', copyable: config.tlsListenerEnabled },
      { label: 'home.network-settings.ws', port: config.wsPort, status: config.wsListenerEnabled ? 'home.network-settings.enabled' : 'home.network-settings.disabled', statusKey: config.wsListenerEnabled ? 'enabled' : 'disabled', copyable: config.wsListenerEnabled },
      { label: 'home.network-settings.wss', port: config.wssPort, status: config.wssListenerEnabled ? 'home.network-settings.enabled' : 'home.network-settings.disabled', statusKey: config.wssListenerEnabled ? 'enabled' : 'disabled', copyable: config.wssListenerEnabled }
    ];
  }
}
