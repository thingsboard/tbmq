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

import { Component, OnInit } from '@angular/core';
import { Store } from '@ngrx/store';
import { AppState } from '@core/core.state';
import { UntypedFormBuilder } from '@angular/forms';
import { WsClientService } from '@core/http/ws-client.service';
import { Connection } from '@shared/models/ws-client.model';
import { tap } from 'rxjs/operators';

@Component({
  selector: 'tb-ws-client-connections',
  templateUrl: './connections.component.html',
  styleUrls: ['./connections.component.scss']
})
export class ConnectionsComponent implements OnInit {

  connection: Connection;

  constructor(protected store: Store<AppState>,
              private wsClientService: WsClientService,
              public fb: UntypedFormBuilder) {
  }

  ngOnInit() {
    this.wsClientService.getConnections().pipe(
      tap(res => {
        if (res.data?.length) {
          const targetConnection = res.data[0];
          this.wsClientService.getConnection(targetConnection.id).subscribe(
            connection => {
              this.wsClientService.selectConnection(connection);
            }
          )
        }
      })
    ).subscribe();

    this.wsClientService.selectedConnection$.subscribe(
      res => {
        this.connection = res;
      }
    )
  }
}
