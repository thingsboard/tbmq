///
/// Copyright © 2016-2025 The Thingsboard Authors
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

import { ActivatedRoute, ActivatedRouteSnapshot, Router, RouterStateSnapshot } from '@angular/router';
import {
  cellWithIcon,
  DateEntityTableColumn,
  EntityTableColumn,
  EntityTableConfig
} from '@home/models/entity/entities-table-config.models';
import { TranslateService } from '@ngx-translate/core';
import { DatePipe } from '@angular/common';
import { EntityType, entityTypeResources, entityTypeTranslations } from '@shared/models/entity-type.models';
import { ClientCredentialsService } from '@core/http/client-credentials.service';
import { clientTypeColor, clientTypeIcon, clientTypeTranslationMap, clientTypeValueColor } from '@shared/models/client.model';
import {
  ClientCredentials,
  ClientCredentialsFilterConfig,
  ClientCredentialsQuery,
  CredentialsType,
  credentialsTypeTranslationMap,
  credentialsWarningTranslations,
  wsSystemCredentialsName
} from '@shared/models/credentials.model';
import { TimePageLink } from '@shared/models/page/page-link';
import { mergeMap, Observable, of } from 'rxjs';
import { PageData } from '@shared/models/page/page-data';
import { deepClone } from '@core/utils';
import { ClientCredentialsTableHeaderComponent } from '@home/pages/client-credentials/client-credentials-table-header.component';
import { ClientCredentialsComponent } from '@home/pages/client-credentials/client-credentials.component';
import { MatDialog } from '@angular/material/dialog';
import { ClientCredentialsWizardDialogComponent } from '@home/components/wizard/client-credentials-wizard-dialog.component';
import { AddEntityDialogData, EntityAction } from '@home/models/entity/entity-component.models';
import { BaseData } from '@shared/models/base-data';
import {
  CheckConnectivityDialogComponent,
  CheckConnectivityDialogData
} from '@home/pages/client-credentials/check-connectivity-dialog.component';
import {
  ChangeBasicPasswordDialogComponent,
  ChangeBasicPasswordDialogData
} from '@home/pages/client-credentials/change-basic-password-dialog.component';
import { Injectable } from '@angular/core';
import { ConfigService } from '@core/http/config.service';

@Injectable()
export class ClientCredentialsTableConfigResolver {

  clientCredentialsFilterConfig: ClientCredentialsFilterConfig = {};
  private readonly config: EntityTableConfig<ClientCredentials> = new EntityTableConfig<ClientCredentials>();

  constructor(private clientCredentialsService: ClientCredentialsService,
              private configService: ConfigService,
              private translate: TranslateService,
              private datePipe: DatePipe,
              private route: ActivatedRoute,
              private router: Router,
              private dialog: MatDialog) {

    this.config.entityType = EntityType.MQTT_CLIENT_CREDENTIALS;
    this.config.entityTranslations = entityTypeTranslations.get(EntityType.MQTT_CLIENT_CREDENTIALS);
    this.config.entityResources = entityTypeResources.get(EntityType.MQTT_CLIENT_CREDENTIALS);
    this.config.tableTitle = this.translate.instant('mqtt-client-credentials.client-credentials');
    this.config.entityTitle = (entity) => entity ? entity.name : '';
    this.config.addDialogStyle = {width: 'fit-content'};
    this.config.addEntity = () => {this.clientCredentialsWizard(null); return of(null); }
    this.config.onEntityAction = action => this.onAction(action, this.config);
    this.config.deleteEnabled = (entity) => entity?.name !== wsSystemCredentialsName;
    this.config.entitySelectionEnabled = (entity) => entity?.name !== wsSystemCredentialsName;

    this.config.entityComponent = ClientCredentialsComponent;
    this.config.headerComponent = ClientCredentialsTableHeaderComponent;

    this.config.columns.push(
      new DateEntityTableColumn<ClientCredentials>('createdTime', 'common.created-time', this.datePipe, '150px'),
      new EntityTableColumn<ClientCredentials>('name', 'mqtt-client-credentials.name', '40%',
        (entity) => entity.name),
      new EntityTableColumn<ClientCredentials>('credentialsType', 'mqtt-client-credentials.type', '150px',
        (entity) => this.translate.instant(credentialsTypeTranslationMap.get(entity.credentialsType))),
      new EntityTableColumn<ClientCredentials>('clientType', 'mqtt-client.client-type', '150px',
        (entity) => {
        const clientType = entity.clientType;
        const clientTypeTranslation = this.translate.instant(clientTypeTranslationMap.get(clientType));
        const icon = clientTypeIcon.get(clientType);
        const color = clientTypeColor.get(clientType);
        const iconColor = clientTypeValueColor.get(clientType);
        return cellWithIcon(clientTypeTranslation, icon, color, iconColor, iconColor);
      })
    );

    this.config.addActionDescriptors.push(
      {
        name: this.translate.instant('mqtt-client-credentials.add-client-credentials'),
        icon: 'add',
        isEnabled: () => true,
        onAction: ($event) => this.config.getTable().addEntity($event)
      }
    );
    this.config.headerActionDescriptors.push(
      {
        name: this.translate.instant('mqtt-client-credentials.connectivity-settings'),
        icon: 'settings',
        isEnabled: () => true,
        onAction: ($event) => this.router.navigate(['/settings/general'])
      }
    )

    this.config.deleteEntityTitle = mqttClient => this.translate.instant('mqtt-client-credentials.delete-client-credential-title',
      { clientCredentialsName: mqttClient.name });
    this.config.deleteEntityContent = () => this.translate.instant('mqtt-client-credentials.delete-client-credential-text');
    this.config.deleteEntitiesTitle = count => this.translate.instant('mqtt-client-credentials.delete-client-credentials-title', {count});
    this.config.deleteEntitiesContent = () => this.translate.instant('mqtt-client-credentials.delete-client-credentials-text');

    this.config.loadEntity = id => this.clientCredentialsService.getClientCredentials(id);
    this.config.saveEntity = mqttClient => this.clientCredentialsService.saveClientCredentials(mqttClient);
    this.config.deleteEntity = id => this.clientCredentialsService.deleteClientCredentials(id);
    this.config.entitiesFetchFunction = pageLink => this.fetchClientCredentials(pageLink as TimePageLink);

    if (!this.config.columns.find(el => el.key === 'warning')) {
      this.config.columns.push(
        new EntityTableColumn<ClientCredentials>('warning', null, '300px',
          (entity) => {
            const credentialsType = entity.credentialsType;
            if (this.showCredentialsWarning(credentialsType)) {
              const value = this.translate.instant(credentialsWarningTranslations.get(credentialsType));
              const icon = 'warning';
              const backgroundColor = 'rgba(255,236,128,0)';
              const iconColor = '#ff9a00';
              const valueColor = 'inherit';
              return cellWithIcon(value,  icon, backgroundColor, iconColor, valueColor);
            }
            return '';
          }, () => null, false
        )
      );
    }
  }

  resolve(route: ActivatedRouteSnapshot, state: RouterStateSnapshot): Observable<EntityTableConfig<ClientCredentials>> {
    this.config.componentsData = {};
    this.config.componentsData.clientCredentialsFilterConfig = {};
    for (const key of Object.keys(route.queryParams)) {
      if (route.queryParams[key] && route.queryParams[key].length) {
        this.config.componentsData.clientCredentialsFilterConfig[key] = route.queryParams[key];
      }
    }
    return this.configService.fetchBrokerConfig({ignoreLoading: true}).pipe(mergeMap(() => of(this.config)));
  }

  private fetchClientCredentials(pageLink: TimePageLink): Observable<PageData<ClientCredentials>> {
    this.clientCredentialsFilterConfig = deepClone(this.config.componentsData.clientCredentialsFilterConfig);
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
    return this.clientCredentialsService.getClientCredentialsV2(query);
  }

  private resolveClientSessionFilter(clientCredentialsFilterConfig?: ClientCredentialsFilterConfig): ClientCredentialsFilterConfig {
    const filter: ClientCredentialsFilterConfig = {};
    if (clientCredentialsFilterConfig) {
      filter.name = clientCredentialsFilterConfig.name;
      filter.clientTypeList = clientCredentialsFilterConfig.clientTypeList;
      filter.credentialsTypeList = clientCredentialsFilterConfig.credentialsTypeList;
      filter.clientId = clientCredentialsFilterConfig.clientId;
      filter.username = clientCredentialsFilterConfig.username;
      filter.certificateCn = clientCredentialsFilterConfig.certificateCn;
    }
    return filter;
  }

  private clientCredentialsWizard($event: Event) {
    if ($event) {
      $event.stopPropagation();
    }
    this.dialog.open<ClientCredentialsWizardDialogComponent, AddEntityDialogData<BaseData>,
      ClientCredentials>(ClientCredentialsWizardDialogComponent, {
      disableClose: true,
      panelClass: ['tb-dialog', 'tb-fullscreen-dialog'],
      autoFocus: false
    }).afterClosed().subscribe(
      (res) => {
        if (res) {
          this.config.updateData();
          if (!localStorage.getItem('notDisplayCheckAfterAddCredentials') && res.credentialsType === CredentialsType.MQTT_BASIC) {
            this.checkConnectivity(null, res, true);
          }
        }
      }
    );
  }

  private onAction(action: EntityAction<ClientCredentials>, config: EntityTableConfig<ClientCredentials>): boolean {
    switch (action.action) {
      case 'open':
        this.openCredentials(action.event, action.entity, config);
        return true;
      case 'checkConnectivity':
        this.checkConnectivity(action.event, action.entity);
        return true;
      case 'changePassword':
        this.changePassword(action.event, action.entity);
        return true;
    }
    return false;
  }

  private checkConnectivity($event: Event, credentials: ClientCredentials, afterAdd = false) {
    if ($event) {
      $event.stopPropagation();
    }
    if (afterAdd) {
      this.dialog.open<CheckConnectivityDialogComponent, CheckConnectivityDialogData>
      (CheckConnectivityDialogComponent, {
        disableClose: true,
        panelClass: ['tb-dialog', 'tb-fullscreen-dialog'],
        data: {
          credentials,
          afterAdd
        }
      })
        .afterClosed()
        .subscribe();
    } else {
      this.clientCredentialsService.getClientCredentials(credentials.id).subscribe(
        res => {
          this.dialog.open<CheckConnectivityDialogComponent, CheckConnectivityDialogData>
          (CheckConnectivityDialogComponent, {
            disableClose: true,
            panelClass: ['tb-dialog', 'tb-fullscreen-dialog'],
            data: {
              credentials: res,
              afterAdd
            }
          })
            .afterClosed()
            .subscribe();
        }
      );
    }
  }

  private openCredentials($event: Event, user: ClientCredentials, config: EntityTableConfig<ClientCredentials>) {
    if ($event) {
      $event.stopPropagation();
    }
    const url = this.router.createUrlTree([user.id], {relativeTo: config.getActivatedRoute()});
    this.router.navigateByUrl(url);
  }

  private changePassword($event: Event, credentials: ClientCredentials) {
    if ($event) {
      $event.stopPropagation();
    }
    this.dialog.open<ChangeBasicPasswordDialogComponent, ChangeBasicPasswordDialogData,
      ClientCredentials>(ChangeBasicPasswordDialogComponent, {
      disableClose: true,
      panelClass: ['tb-dialog', 'tb-fullscreen-dialog'],
      data: {
        credentials
      }
    }).afterClosed().subscribe((res: ClientCredentials) => {
      if (res) {
        this.config.updateData(false);
      }
    });
  }

  private showCredentialsWarning(credentialsType: CredentialsType): boolean {
    const brokerConfig = this.configService.brokerConfig;
    return (credentialsType === CredentialsType.MQTT_BASIC && !brokerConfig.basicAuthEnabled) ||
           (credentialsType === CredentialsType.X_509 && !brokerConfig.x509AuthEnabled) ||
           (credentialsType === CredentialsType.SCRAM && !brokerConfig.scramAuthEnabled);
  }
}
