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
import { MatDialog } from '@angular/material/dialog';
import { DialogService } from '@core/services/dialog.service';
import { ConnectionDialogData, ConnectionWizardDialogComponent } from '@home/components/wizard/connection-wizard-dialog.component';
import { isDefinedAndNotNull } from '@core/utils';

@Component({
  selector: 'tb-connection',
  templateUrl: './connection.component.html',
  styleUrls: ['./connection.component.scss']
})
export class ConnectionComponent implements OnInit {

  @Input()
  connection: Connection;

  @Output()
  connectionDeleted = new EventEmitter<Connection>();

  @Output()
  connectionEdited = new EventEmitter<Connection>();

  selectedConnection: Connection;
  showActions = false;
  hiddenActions = this.configureCellHiddenActions();

  constructor(private wsClientService: WsClientService,
              private dialog: MatDialog,
              private dialogService: DialogService,
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

  selectConnection() {
    this.wsClientService.selectConnection(this.connection);
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
    this.dialogService.confirm(
      this.translate.instant('ws-client.connections.delete-connection-title', {connectionName: this.connection.name}),
      this.translate.instant('ws-client.connections.delete-connection-text', {connectionName: this.connection.name}),
      this.translate.instant('action.no'),
      this.translate.instant('action.yes'),
      true
    ).subscribe((result) => {
      if (result) {
        this.wsClientService.deleteConnection(this.connection, this.selectedConnection);
        if (this.selectedConnection.id === this.connection.id) {
          // TODO move implementation to the select-connection-component
          /*this.wsClientService.selectFirstConnection().subscribe(
            () => {
              this.wsClientService.deleteConnection(this.connection.id).subscribe();
            }
          );*/
        }
      }
    });
  }

  private edit($event: Event, entity: Connection) {
    if ($event) {
      $event.stopPropagation();
    }
    this.dialog.open<ConnectionWizardDialogComponent, ConnectionDialogData>(ConnectionWizardDialogComponent, {
      disableClose: true,
      panelClass: ['tb-dialog', 'tb-fullscreen-dialog'],
      data: {
        entity
      }
    }).afterClosed()
      .subscribe((res) => {
        if (isDefinedAndNotNull(res)) {
          /*this.wsClientService.saveConnection(res).subscribe(
            (connection) => {
              this.connectionEdited.emit(connection);
            }
          );*/
        }
      });
  }

}
