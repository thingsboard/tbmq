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

import { ActivatedRoute, Router } from '@angular/router';
import {
  clientTypeCell,
  clientTypeWarning,
  DateEntityTableColumn,
  EntityTableColumn,
  EntityTableConfig
} from '@home/models/entity/entities-table-config.models';
import { TranslateService } from '@ngx-translate/core';
import { DatePipe } from '@angular/common';
import { EntityType, entityTypeResources, entityTypeTranslations } from '@shared/models/entity-type.models';
import { select, Store } from '@ngrx/store';
import { AppState } from '@core/core.state';
import { Direction } from '@shared/models/page/sort-order';
import { ClientCredentialsService } from '@core/http/client-credentials.service';
import {
  clientTypeColor,
  clientTypeIcon,
  clientTypeTranslationMap,
  initialClientCredentialsFilterConfig
} from '@shared/models/client.model';
import {
  ClientCredentialsFilterConfig, ClientCredentialsQuery,
  credentialsTypeTranslationMap,
  credentialsWarningTranslations,
  ClientCredentials,
  CredentialsType
} from '@shared/models/credentials.model';
import { map } from 'rxjs/operators';
import { selectUserDetails } from '@core/auth/auth.selectors';
import { ConfigParams } from '@shared/models/config.model';
import { TimePageLink } from '@shared/models/page/page-link';
import { Observable } from 'rxjs';
import { PageData } from '@shared/models/page/page-data';
import { deepClone } from '@core/utils';
import { ClientCredentialsTableHeaderComponent } from '@home/pages/client-credentials/client-credentials-table-header.component';
import { ClientCredentialsComponent } from '@home/pages/client-credentials/client-credentials.component';

export class ClientCredentialsTableConfig extends EntityTableConfig<ClientCredentials, TimePageLink> {

  clientCredentialsFilterConfig: ClientCredentialsFilterConfig = initialClientCredentialsFilterConfig;

  constructor(private store: Store<AppState>,
              private clientCredentialsService: ClientCredentialsService,
              private translate: TranslateService,
              private datePipe: DatePipe,
              public entityId: string = null,
              private route: ActivatedRoute,
              private router: Router) {
    super();
    this.loadDataOnInit = true;
    this.entityType = EntityType.MQTT_CLIENT_CREDENTIALS;
    this.entityTranslations = entityTypeTranslations.get(EntityType.MQTT_CLIENT_CREDENTIALS);
    this.entityResources = entityTypeResources.get(EntityType.MQTT_CLIENT_CREDENTIALS);
    this.defaultSortOrder = { property: 'name', direction: Direction.ASC };
    this.tableTitle = this.translate.instant('mqtt-client-credentials.client-credentials');
    this.entityTitle = (mqttClient) => mqttClient ? mqttClient.name : '';

    this.entityComponent = ClientCredentialsComponent;
    this.headerComponent = ClientCredentialsTableHeaderComponent;

    this.columns.push(
      new DateEntityTableColumn<ClientCredentials>('createdTime', 'common.created-time', this.datePipe, '150px'),
      new EntityTableColumn<ClientCredentials>('name', 'mqtt-client-credentials.name', '30%',
        (entity) => entity.name),
      new EntityTableColumn<ClientCredentials>('credentialsType', 'mqtt-client-credentials.type', '30%',
        (entity) => this.translate.instant(credentialsTypeTranslationMap.get(entity.credentialsType))),
      new EntityTableColumn<ClientCredentials>('clientType', 'mqtt-client.client-type', '30%',
        (entity) => {
        const clientType = entity.clientType;
        const clientTypeTranslation = this.translate.instant(clientTypeTranslationMap.get(clientType));
        const icon = clientTypeIcon.get(clientType);
        const color = clientTypeColor.get(clientType);
        return clientTypeCell(clientTypeTranslation, icon, color);
      })
    );

    this.addActionDescriptors.push(
      {
        name: this.translate.instant('mqtt-client-credentials.add-client-credentials'),
        icon: 'add',
        isEnabled: () => true,
        onAction: ($event) => this.getTable().addEntity($event)
      }
    );

    this.deleteEntityTitle = mqttClient => this.translate.instant('mqtt-client-credentials.delete-client-credential-title',
      { clientCredentialsName: mqttClient.name });
    this.deleteEntityContent = () => this.translate.instant('mqtt-client-credentials.delete-client-credential-text');
    this.deleteEntitiesTitle = count => this.translate.instant('mqtt-client-credentials.delete-client-credentials-title', {count});
    this.deleteEntitiesContent = () => this.translate.instant('mqtt-client-credentials.delete-client-credentials-text');

    this.loadEntity = id => this.clientCredentialsService.getMqttClientCredentials(id);
    this.saveEntity = mqttClient => this.clientCredentialsService.saveMqttClientCredentials(mqttClient);
    this.deleteEntity = id => this.clientCredentialsService.deleteMqttClientCredentials(id);
    this.entitiesFetchFunction = pageLink => this.fetchClientCredentials(pageLink);

    this.store.pipe(
      select(selectUserDetails),
      map((user) => {
        this.componentsData = {};
        this.componentsData.config = {
          basicAuthEnabled: user.additionalInfo?.config?.[ConfigParams.basicAuthEnabled],
          sslAuthEnabled: user.additionalInfo?.config?.[ConfigParams.x509AuthEnabled]
        };
        if (!this.columns.find(el => el.key === 'warning')) {
          this.columns.push(
            new EntityTableColumn<ClientCredentials>('warning', null, '300px',
              (entity) => {
                if ((entity.credentialsType === CredentialsType.MQTT_BASIC && !this.componentsData.config?.basicAuthEnabled) ||
                  (entity.credentialsType === CredentialsType.SSL && !this.componentsData.config?.sslAuthEnabled)) {
                  return clientTypeWarning(this.translate.instant(credentialsWarningTranslations.get(entity.credentialsType)));
                }
                return '';
              }, () => null, false
            )
          );
        }
        return true;
      })
    ).subscribe();
  }

  private fetchClientCredentials(pageLink: TimePageLink): Observable<PageData<ClientCredentials>> {
    const routerQueryParams: ClientCredentialsFilterConfig = this.route.snapshot.queryParams;
    if (routerQueryParams) {
      const queryParams = deepClone(routerQueryParams);
      let replaceUrl = false;
      if (routerQueryParams?.clientTypeList) {
        this.clientCredentialsFilterConfig.clientTypeList = routerQueryParams?.clientTypeList;
        delete queryParams.clientTypeList;
        replaceUrl = true;
      }
      if (replaceUrl) {
        this.router.navigate([], {
          relativeTo: this.route,
          queryParams,
          queryParamsHandling: '',
          replaceUrl: true
        });
      }
    }
    const clientCredentialsFilter = this.resolveClientSessionFilter(this.clientCredentialsFilterConfig);
    const query = new ClientCredentialsQuery(pageLink, clientCredentialsFilter);
    // return this.clientCredentialsService.getMqttClientsCredentialsV2(query);

    return this.clientCredentialsService.getMqttClientsCredentials(pageLink);
  }

  private resolveClientSessionFilter(clientCredentialsFilterConfig?: ClientCredentialsFilterConfig): ClientCredentialsFilterConfig {
    const filter: ClientCredentialsFilterConfig = {};
    if (clientCredentialsFilterConfig) {
      filter.name = clientCredentialsFilterConfig.name;
      filter.clientTypeList = clientCredentialsFilterConfig.clientTypeList;
      filter.credentialsTypeList = clientCredentialsFilterConfig.credentialsTypeList;
    }
    return filter;
  }
}
