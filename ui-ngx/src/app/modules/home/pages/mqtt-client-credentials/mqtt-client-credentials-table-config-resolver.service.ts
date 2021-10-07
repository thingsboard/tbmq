///
/// Copyright © 2016-2020 The Thingsboard Authors
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

import { Injectable } from '@angular/core';

import { Resolve } from '@angular/router';
import {
  DateEntityTableColumn,
  EntityTableColumn,
  EntityTableConfig
} from '@home/models/entity/entities-table-config.models';
import { TranslateService } from '@ngx-translate/core';
import { DatePipe } from '@angular/common';
import { EntityType, entityTypeResources, entityTypeTranslations } from '@shared/models/entity-type.models';
import { EntityAction } from '@home/models/entity/entity-component.models';
import { Store } from '@ngrx/store';
import { AppState } from '@core/core.state';
import { getCurrentAuthUser } from '@app/core/auth/auth.selectors';
import { DialogService } from '@core/services/dialog.service';
import { ImportExportService } from '@home/components/import-export/import-export.service';
import { Direction } from '@shared/models/page/sort-order';
import {
  MqttCredentials,
  credentialsTypeNames,
} from '@shared/models/mqtt.models';
import { MqttClientCredentialsService } from '@core/http/mqtt-client-credentials.service';
import { MqttClientCredentialsComponent } from '@home/pages/mqtt-client-credentials/mqtt-client-credentials.component';
import {
  ManageCredentialsDialogData,
  ManageCredentialsDialogComponent
} from '@home/dialogs/manage-credentials-dialog.component';
import { MatDialog } from '@angular/material/dialog';

@Injectable()
export class MqttClientCredentialsTableConfigResolver implements Resolve<EntityTableConfig<MqttCredentials>> {

  private readonly config: EntityTableConfig<MqttCredentials> = new EntityTableConfig<MqttCredentials>();

  constructor(private store: Store<AppState>,
              private dialogService: DialogService,
              private mqttClientCredentialsService: MqttClientCredentialsService,
              private translate: TranslateService,
              private importExport: ImportExportService,
              private datePipe: DatePipe,
              private dialog: MatDialog) {

    this.config.entityType = EntityType.MQTT_CLIENT;
    this.config.entityComponent = MqttClientCredentialsComponent;
    this.config.entityTranslations = entityTypeTranslations.get(EntityType.MQTT_CLIENT);
    this.config.entityResources = entityTypeResources.get(EntityType.MQTT_CLIENT);
    this.config.defaultSortOrder = { property: 'name', direction: Direction.ASC };
    this.config.tableTitle = this.translate.instant('mqtt-client-credentials.client-credentials');

    this.config.addEnabled = true;
    this.config.entitiesDeleteEnabled = true;
    this.config.deleteEnabled = () => true;
    this.config.entityTitle = (mqttClient) => mqttClient ? mqttClient.credentialsId : '';

    this.config.columns.push(
      new DateEntityTableColumn<MqttCredentials>('createdTime', 'common.created-time', this.datePipe, '150px'),
      new EntityTableColumn<MqttCredentials>('name', 'mqtt-client-credentials.name', '50%'),
      new EntityTableColumn<MqttCredentials>('credentialsType', 'mqtt-client-credentials.type', '50%',
        (entity) => credentialsTypeNames.get(entity.credentialsType))
    );

    this.config.addActionDescriptors.push(
      {
        name: this.translate.instant('mqtt-client-credentials.add'),
        icon: 'add',
        isEnabled: () => true,
        onAction: ($event) => this.config.table.addEntity($event)
      }
    );

    this.config.cellActionDescriptors.push(
      {
        name: this.translate.instant('mqtt-client-credentials.manage-credentials'),
        mdiIcon: 'mdi:badge-account-horizontal-outline',
        isEnabled: () => true,
        onAction: ($event, entity) => this.manageCredentials($event, entity)
      }
    );

    this.config.deleteEntityTitle = mqttClient => this.translate.instant('mqtt-client-credentials.delete-client-title',
      { mqttClientTitle: mqttClient.name });
    this.config.deleteEntityContent = () => this.translate.instant('mqtt-client-credentials.delete-client-text');
    this.config.deleteEntitiesTitle = count => this.translate.instant('mqtt-client-credentials.delete-mqtt-clients-title', {count});
    this.config.deleteEntitiesContent = () => this.translate.instant('mqtt-client-credentials.delete-mqtt-clients-text');


    this.config.loadEntity = id => this.loadEntity(id);
    this.config.saveEntity = mqttClient => this.mqttClientCredentialsService.saveMqttClientCredentials(mqttClient);
    this.config.deleteEntity = id => this.deleteEntity(id);
    this.config.onEntityAction = action => this.onMqttClientAction(action);
  }

  resolve(): EntityTableConfig<MqttCredentials> {
    const authUser = getCurrentAuthUser(this.store);
    // this.config.deleteEnabled = (mqttClient) => this.isMqttClientEditable(mqttClient, authUser.authority);
    // this.config.entitySelectionEnabled = (mqttClient) => this.isMqttClientEditable(mqttClient, authUser.authority);
    // this.config.detailsReadonly = (mqttClient) => !this.isMqttClientEditable(mqttClient, authUser.authority);
    this.config.entitiesFetchFunction = pageLink => this.mqttClientCredentialsService.getMqttClientsCredentials(pageLink);
    return this.config;
  }

  loadEntity(id) {
    return this.mqttClientCredentialsService.getMqttClientCredentials(id);
  }

  deleteEntity(id) {
    return this.mqttClientCredentialsService.deleteMqttClientCredentials(id);
  }

  // isMqttClientEditable(mqttClient: Client, authority: Authority): boolean {
  //   return authority === Authority.SYS_ADMIN;
  // }

  onMqttClientAction(action: EntityAction<MqttCredentials>): boolean {
    switch (action.action) {
      case 'manage':
        this.manageCredentials(action.event, action.entity);
        return true;
    }
    return false;
  }

  manageCredentials($event: Event, mqttClientCredentials: MqttCredentials) {
    if ($event) {
      $event.stopPropagation();
    }
    this.dialog.open<ManageCredentialsDialogComponent, ManageCredentialsDialogData,
      MqttCredentials>(ManageCredentialsDialogComponent, {
      disableClose: true,
      panelClass: ['tb-dialog', 'tb-fullscreen-dialog'],
      data: {
        mqttClientCredentials
      }
    })
      .afterClosed()
      .subscribe((res) => {
        if (res) {
          this.config.table.updateData();
        }
      });
  }

  onMqttClientCredentialsAction(action: EntityAction<MqttCredentials>): boolean {
    switch (action.action) {
      case 'open':
        // this.openEditClientCredentialsProfile(action.event, action.entity);
        return true;
    }
    return false;
  }

}
