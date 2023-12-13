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

import { CellActionDescriptor, EntityTableColumn, EntityTableConfig } from '@home/models/entity/entities-table-config.models';
import { TimePageLink } from '@shared/models/page/page-link';
import { EntityType, entityTypeResources, entityTypeTranslations } from '@shared/models/entity-type.models';
import { TranslateService } from '@ngx-translate/core';
import { WsClientService } from '@core/http/ws-client.service';
import { Connection } from '@shared/models/ws-client.model';
import {
  AddWsClientConnectionDialogData,
  WsClientConnectionDialogComponent
} from '@home/pages/ws-client/ws-client-connection-dialog.component';
import { isDefinedAndNotNull } from '@core/utils';
import { MatDialog } from '@angular/material/dialog';
import { of } from 'rxjs';

export class WsConnectionsTableConfig extends EntityTableConfig<Connection, TimePageLink> {

  constructor(private wsClientService: WsClientService,
              private translate: TranslateService,
              private dialog: MatDialog,
              public entityId: string = null) {
    super();
    this.entityType = EntityType.WS_CONNECTION;
    this.entityTranslations = entityTypeTranslations.get(EntityType.WS_CONNECTION);
    this.entityResources = entityTypeResources.get(EntityType.WS_CONNECTION);
    this.tableTitle = this.translate.instant('ws-client.connections.connections');
    this.entityComponent = null;
    this.detailsPanelEnabled = false;
    this.selectionEnabled = false;
    this.addEnabled = true;
    this.entitiesDeleteEnabled = false;
    this.displayPagination = false;
    this.columns.push(new EntityTableColumn<Connection>('name', 'kafka.name'));
    this.entitiesFetchFunction = pageLink => this.wsClientService.getConnections(pageLink);
    this.cellActionDescriptors = this.configureCellActions();

    this.cellActionDescriptors = this.configureCellActions();
    this.addEntity = () => {
      this.addConnection(null);
      return of(null);
    };
    this.handleRowClick = ($event, entity) => this.selectConnection($event, entity);
  }

  selectConnection($event, entity) {
    if ($event) {
      $event.stopPropagation();
    }
    this.wsClientService.selectConnection(entity);
    return true;
  }

  addConnection($event: Event) {
    if ($event) {
      $event.stopPropagation();
    }
    const data = {
      connection: null
    };
    this.dialog.open<WsClientConnectionDialogComponent, AddWsClientConnectionDialogData>(WsClientConnectionDialogComponent, {
      disableClose: true,
      panelClass: ['tb-dialog', 'tb-fullscreen-dialog'],
      data
    }).afterClosed()
      .subscribe((res) => {
        if (isDefinedAndNotNull(res)) {
          this.wsClientService.addConnection(res);
          this.updateData();
        }
      });
  }

  configureCellActions(): Array<CellActionDescriptor<Connection>> {
    const actions: Array<CellActionDescriptor<Connection>> = [];
    actions.push(
      {
        name: this.translate.instant('ws-client.connections.disconnect'),
        icon: 'play_arrow',
        style: {
          color: 'rgba(25,128,56,1)',
          background: 'rgba(31, 139, 77, 0.07)',
          borderRadius: '2px'
        },
        isEnabled: (entity) => !!entity.connected,
        onAction: ($event, entity) => this.connect($event, entity)
      },
      {
        name: this.translate.instant('ws-client.connections.connect'),
        icon: 'pause_arrow',
        isEnabled: (entity) => !entity.connected,
        onAction: ($event, entity) => this.disconnect($event, entity)
      },
      {
        name: this.translate.instant('mqtt-client-session.remove-session'),
        icon: 'edit',
        isEnabled: (entity) => true,
        onAction: ($event, entity) => this.edit($event, entity)
      },
      {
        name: this.translate.instant('mqtt-client-session.remove-session'),
        icon: 'delete',
        isEnabled: (entity) => true,
        onAction: ($event, entity) => this.remove($event, entity)
      }
    );
    return actions;
  }

  connect($event, entity) {
    if ($event) {
      $event.stopPropagation();
    }
    this.wsClientService.selectConnection(entity);
  }

  disconnect($event, entity) {
    if ($event) {
      $event.stopPropagation();
    }

  }

  remove($event, entity) {
    if ($event) {
      $event.stopPropagation();
    }

  }

  edit($event, entity) {
    if ($event) {
      $event.stopPropagation();
    }

  }
}
