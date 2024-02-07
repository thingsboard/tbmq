///
/// Copyright © 2016-2024 The Thingsboard Authors
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
import { WsClientService } from '@core/http/ws-client.service';
import { WebSocketConnection } from '@shared/models/ws-client.model';
import { Observable } from 'rxjs';

@Component({
  selector: 'tb-connections',
  templateUrl: './connections.component.html',
  styleUrls: ['./connections.component.scss']
})
export class ConnectionsComponent {

  connection: Observable<WebSocketConnection> = this.wsClientService.selectedConnection$;

  constructor(private wsClientService: WsClientService) {
  }
}
