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

import { Component } from '@angular/core';
import { TranslateService } from '@ngx-translate/core';
import { WsClientService } from '@core/http/ws-client.service';
import { Connection } from '@shared/models/ws-client.model';
import { MatDialog } from '@angular/material/dialog';
import { WsSubscriptionsTableConfig } from "@home/pages/ws-client/ws-subscriptions-table-config";

@Component({
  selector: 'tb-ws-client-subscriptions',
  templateUrl: './ws-client-subscriptions.component.html',
  styleUrls: ['./ws-client-subscriptions.component.scss']
})
export class WsClientSubscriptionsComponent {

  connection: Connection;
  wsSubscriptionsTableConfig: WsSubscriptionsTableConfig

  constructor(private dialog: MatDialog,
              private wsClientService: WsClientService,
              private translate: TranslateService) {

    wsClientService.selectedConnection$.subscribe(
      res => {
        if (res) {
          this.connection = res;
          this.wsSubscriptionsTableConfig = new WsSubscriptionsTableConfig(this.wsClientService, this.translate, this.dialog, res.id);
        }
      }
    );
  }
}

