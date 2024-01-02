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

import {
  CellActionDescriptor,
  EntityTableColumn,
  EntityTableConfig
} from '@home/models/entity/entities-table-config.models';
import { TimePageLink } from '@shared/models/page/page-link';
import { EntityType, entityTypeResources, entityTypeTranslations } from '@shared/models/entity-type.models';
import { TranslateService } from '@ngx-translate/core';
import { WsClientService } from '@core/http/ws-client.service';
import { Connection } from '@shared/models/ws-client.model';
import { isDefinedAndNotNull } from '@core/utils';
import { MatDialog } from '@angular/material/dialog';
import { of } from 'rxjs';
import { tap } from "rxjs/operators";
import { ConnectionWizardDialogComponent } from "@home/components/wizard/connection-wizard-dialog.component";

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
    this.selectionEnabled = true;
    this.addEnabled = true;
    this.entitiesDeleteEnabled = false;
    this.displayPagination = false;
    this.columns.push(new EntityTableColumn<Connection>('name', 'kafka.name'));
    this.entitiesFetchFunction = pageLink => this.fetchConnections(pageLink);

    this.cellActionDescriptors = this.configureCellActions();
    this.cellMoreActionDescriptors = this.configureCellMoreActions();
    this.addEntity = () => {
      this.addConnection(null);
      return of(null);
    };
    this.handleRowClick = ($event, entity) => this.selectConnection($event, entity);
  }

  fetchConnections(pageLink: TimePageLink) {
    return this.wsClientService.getConnections(pageLink).pipe(
      tap(res => {
        if (res.data?.length) {
          const targetConnection = res.data[0];
          this.wsClientService.getConnection(targetConnection.id).subscribe(
            connection => {
              this.selectConnection(null, connection);
            }
          )
        }
      })
    )
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
    this.dialog.open<ConnectionWizardDialogComponent, any>(ConnectionWizardDialogComponent, {
      disableClose: true,
      panelClass: ['tb-dialog', 'tb-fullscreen-dialog']
    }).afterClosed()
      .subscribe((res) => {
        if (isDefinedAndNotNull(res)) {
          this.wsClientService.saveConnection(res).subscribe(
            () => {
              this.updateData()
            }
          );
        }
      });
  }

  configureCellActions(): Array<CellActionDescriptor<Connection>> {
    const actions: Array<CellActionDescriptor<Connection>> = [];
    actions.push(
      {
        name: this.translate.instant('ws-client.connections.disconnect'),
        icon: 'mdi:toggle-switch',
        style: {
          color: 'rgba(25,128,56,1)'
        },
        isEnabled: (entity) => !!entity.connected,
        onAction: ($event, entity) => this.disconnect($event, entity)
      },
      {
        name: this.translate.instant('ws-client.connections.connect'),
        icon: 'mdi:toggle-switch-off',
        style: {
          color: 'rgba(0,0,0,0.54)'
        },
        isEnabled: (entity) => !entity.connected,
        onAction: ($event, entity) => this.connect($event, entity)
      }
    );
    return actions;
  }

  configureCellMoreActions(): Array<CellActionDescriptor<Connection>> {
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
        onAction: ($event, entity) => this.remove($event, entity)
      }
    );
    return actions;
  }

  connect($event, entity) {
    if ($event) {
      $event.stopPropagation();
    }
    this.wsClientService.connectClient(entity);
  }

  disconnect($event, entity) {
    if ($event) {
      $event.stopPropagation();
    }
    this.wsClientService.disconnectClient(entity);
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
