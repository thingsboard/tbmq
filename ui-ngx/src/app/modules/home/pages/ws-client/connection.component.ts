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

import { Component, EventEmitter, Input, OnInit, Output } from '@angular/core';
import { Connection } from '@shared/models/ws-client.model';
import { CellActionDescriptor } from '@home/models/entity/entities-table-config.models';
import { TranslateService } from '@ngx-translate/core';
import { WsClientService } from '@core/http/ws-client.service';

@Component({
  selector: 'tb-connection',
  templateUrl: './connection.component.html',
  styleUrls: ['./connection.component.scss']
})
export class ConnectionComponent implements OnInit {

  @Input()
  connection: Connection;

  selectedConnection: Connection;
  showActions = false;
  hiddenActions = this.configureCellHiddenActions();

  constructor(private wsClientService: WsClientService,
              private translate: TranslateService) {

  }

  ngOnInit() {
    this.wsClientService.selectedConnection$.subscribe(
      res => {
        this.selectedConnection = res;
      }
    );
  }

  onCommentMouseEnter(): void {
    this.showActions = true;
  }

  onCommentMouseLeave(): void {
    this.showActions = false;
  }

  private configureCellHiddenActions(): Array<CellActionDescriptor<Connection>> {
    const actions: Array<CellActionDescriptor<Connection>> = [];
    actions.push(
      {
        name: this.translate.instant('action.edit'),
        icon: 'edit',
        isEnabled: () => true,
        onAction: ($event, entity) => this.edit($event, entity)
      },
      {
        name: this.translate.instant('action.delete'),
        icon: 'delete',
        isEnabled: () => true,
        onAction: ($event, entity) => this.delete($event, entity)
      }
    );
    return actions;
  }

  private delete($event: Event, entity: Connection) {
    if ($event) {
      $event.stopPropagation();
    }
  }

  private edit($event: Event, entity: Connection) {
    if ($event) {
      $event.stopPropagation();
    }
  }

}
