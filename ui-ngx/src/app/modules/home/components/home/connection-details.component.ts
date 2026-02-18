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
import { MatIconModule } from '@angular/material/icon';
import { MatButtonModule } from '@angular/material/button';

interface HostParam {
  label: string;
  value: string;
  type: 'text' | 'download';
  copyable?: boolean;
}

@Component({
  selector: 'tb-home-connection-details',
  templateUrl: './connection-details.component.html',
  styleUrls: ['./connection-details.component.scss'],
  imports: [CardTitleButtonComponent, CopyButtonComponent, TranslateModule, MatIconModule, MatButtonModule]
})
export class ConnectionDetailsComponent implements OnInit {

  cardType = HomePageTitleType.CONNECTION_DETAILS;
  hostParams: HostParam[] = [];

  constructor(private configService: ConfigService) {}

  ngOnInit() {
    const host = window.location.hostname;

    this.hostParams = [
      { label: 'home.connection-details.broker-host', value: host, type: 'text', copyable: true },
      { label: 'home.connection-details.ca-certificate', value: 'home.connection-details.download', type: 'download', copyable: false }
    ];
  }

  downloadCaCertificate() {
    // TODO: Implement CA certificate download
    console.log('Download CA certificate');
  }
}
