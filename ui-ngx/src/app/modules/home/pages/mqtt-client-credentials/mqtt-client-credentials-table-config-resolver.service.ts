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

import { Injectable } from '@angular/core';

import { Resolve } from '@angular/router';
import {
  clientTypeCell, clientTypeWarning,
  credetialsTypeCell,
  DateEntityTableColumn,
  defaultCellStyle,
  EntityTableColumn,
  EntityTableConfig
} from '@home/models/entity/entities-table-config.models';
import { TranslateService } from '@ngx-translate/core';
import { DatePipe } from '@angular/common';
import { EntityType, entityTypeResources, entityTypeTranslations } from '@shared/models/entity-type.models';
import { select, Store } from '@ngrx/store';
import { AppState } from '@core/core.state';
import { DialogService } from '@core/services/dialog.service';
import { Direction } from '@shared/models/page/sort-order';
import { MqttClientCredentialsService } from '@core/http/mqtt-client-credentials.service';
import { MqttClientCredentialsComponent } from '@home/pages/mqtt-client-credentials/mqtt-client-credentials.component';
import { MatDialog } from '@angular/material/dialog';
import { clientTypeTranslationMap } from '@shared/models/client.model';
import {
  credentialsTypeNames,
  credentialsWarningTranslations,
  MqttClientCredentials,
  MqttCredentialsType
} from '@shared/models/client-crenetials.model';
import { ConfigService } from '@core/http/config.service';
import { map } from 'rxjs/operators';
import { selectUserDetails } from '@core/auth/auth.selectors';
import { ConfigParams } from '@shared/models/stats.model';

@Injectable()
export class MqttClientCredentialsTableConfigResolver implements Resolve<EntityTableConfig<MqttClientCredentials>> {

  private readonly config: EntityTableConfig<MqttClientCredentials> = new EntityTableConfig<MqttClientCredentials>();

  constructor(private store: Store<AppState>,
              private dialogService: DialogService,
              private mqttClientCredentialsService: MqttClientCredentialsService,
              private translate: TranslateService,
              private datePipe: DatePipe,
              private configService: ConfigService,
              private dialog: MatDialog) {

    this.config.entityType = EntityType.MQTT_CLIENT_CREDENTIALS;
    this.config.entityComponent = MqttClientCredentialsComponent;
    this.config.entityTranslations = entityTypeTranslations.get(EntityType.MQTT_CLIENT_CREDENTIALS);
    this.config.entityResources = entityTypeResources.get(EntityType.MQTT_CLIENT_CREDENTIALS);
    this.config.defaultSortOrder = { property: 'name', direction: Direction.ASC };
    this.config.tableTitle = this.translate.instant('mqtt-client-credentials.client-credentials');

    this.config.addEnabled = true;
    this.config.entitiesDeleteEnabled = true;
    this.config.deleteEnabled = () => true;
    this.config.entityTitle = (mqttClient) => mqttClient ? mqttClient.credentialsId : '';

    this.config.columns.push(
      new DateEntityTableColumn<MqttClientCredentials>('createdTime', 'common.created-time', this.datePipe, '150px'),
      new EntityTableColumn<MqttClientCredentials>('name', 'mqtt-client-credentials.name', '30%',
        (entity) => defaultCellStyle(entity.name)),
      new EntityTableColumn<MqttClientCredentials>('clientType', 'mqtt-client.client-type', '30%',
        (entity) => clientTypeCell(this.translate.instant(clientTypeTranslationMap.get(entity.clientType)))),
      new EntityTableColumn<MqttClientCredentials>('credentialsType', 'mqtt-client-credentials.type', '30%',
        (entity) => credetialsTypeCell(this.translate.instant(credentialsTypeNames.get(entity.credentialsType))))
    );

    this.config.addActionDescriptors.push(
      {
        name: this.translate.instant('mqtt-client-credentials.add'),
        icon: 'add',
        isEnabled: () => true,
        onAction: ($event) => this.config.table.addEntity($event)
      }
    );

    this.config.deleteEntityTitle = mqttClient => this.translate.instant('mqtt-client-credentials.delete-client-credential-title',
      { clientCredentialsName: mqttClient.name });
    this.config.deleteEntityContent = () => this.translate.instant('mqtt-client-credentials.delete-client-credential-text');
    this.config.deleteEntitiesTitle = count => this.translate.instant('mqtt-client-credentials.delete-client-credentials-title', {count});
    this.config.deleteEntitiesContent = () => this.translate.instant('mqtt-client-credentials.delete-client-credentials-text');

    this.config.loadEntity = id => this.loadEntity(id);
    this.config.saveEntity = mqttClient => this.mqttClientCredentialsService.saveMqttClientCredentials(mqttClient);
    this.config.deleteEntity = id => this.deleteEntity(id);
  }

  resolve(): EntityTableConfig<MqttClientCredentials> {
    this.config.entitiesFetchFunction = pageLink => this.mqttClientCredentialsService.getMqttClientsCredentials(pageLink);
    this.store.pipe(
      select(selectUserDetails),
      map((user) => {
        this.config.componentsData = {};
        this.config.componentsData.config = {
          basicAuthEnabled: user.additionalInfo?.config.find(el => el.key === ConfigParams.BASIC_AUTH).value,
          sslAuthEnabled: user.additionalInfo?.config.find(el => el.key === ConfigParams.X509_CERT_CHAIN_AUTH).value
        };
        if (!this.config.columns.find(el => el.key === 'warning')) {
          this.config.columns.push(
            new EntityTableColumn<MqttClientCredentials>('warning', null, '200px',
              (entity) => {
                if (entity.credentialsType === MqttCredentialsType.MQTT_BASIC) {
                  if (!this.config.componentsData.config?.basicAuthEnabled) {
                    return clientTypeWarning(this.translate.instant(credentialsWarningTranslations.get(entity.credentialsType)));
                  }
                }
                if (entity.credentialsType === MqttCredentialsType.SSL) {
                  if (!this.config.componentsData.config?.sslAuthEnabled) {
                    return clientTypeWarning(this.translate.instant(credentialsWarningTranslations.get(entity.credentialsType)));
                  }
                }
                return '';
              })
          );
        }
        return true;
      })
    ).subscribe();
    return this.config;
  }

  loadEntity(id) {
    return this.mqttClientCredentialsService.getMqttClientCredentials(id);
  }

  deleteEntity(id) {
    return this.mqttClientCredentialsService.deleteMqttClientCredentials(id);
  }
}
