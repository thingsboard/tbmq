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

import { Router } from '@angular/router';
import {
  CellActionDescriptor,
  EntityTableColumn,
  EntityTableConfig
} from '@home/models/entity/entities-table-config.models';
import { TranslateService } from '@ngx-translate/core';
import { EntityType, entityTypeResources, entityTypeTranslations } from '@shared/models/entity-type.models';
import { Observable, of } from 'rxjs';
import { EntityAction } from '@home/models/entity/entity-component.models';
import { Injectable } from '@angular/core';
import {
  MqttAuthProvider,
  mqttAuthProviderTypeTranslationMap,
  ShortMqttAuthProvider
} from '@shared/models/mqtt-auth-provider.model';
import { MqttAuthProviderComponent } from '@home/pages/authentication/mqtt-auth-provider.component';
import { MqttAuthProviderService } from '@core/http/mqtt-auth-provider.service';
import { ConfigService } from '@core/http/config.service';

@Injectable()
export class MqttAuthProviderTableConfigResolver {

  private readonly config: EntityTableConfig<ShortMqttAuthProvider> = new EntityTableConfig<ShortMqttAuthProvider>();

  constructor(private mqttAuthProviderService: MqttAuthProviderService,
              private condfigService: ConfigService,
              private translate: TranslateService,
              private router: Router) {

    this.config.entityType = EntityType.MQTT_AUTH_PROVIDER;
    this.config.entityTranslations = entityTypeTranslations.get(EntityType.MQTT_AUTH_PROVIDER);
    this.config.entityResources = entityTypeResources.get(EntityType.MQTT_AUTH_PROVIDER);
    this.config.entityComponent = MqttAuthProviderComponent;

    this.config.tableTitle = this.translate.instant('authentication.authentication-providers');
    this.config.entityTitle = (entity) => entity ? this.translate.instant(mqttAuthProviderTypeTranslationMap.get(entity.type)) : '';
    this.config.onEntityAction = action => this.onAction(action, this.config);
    this.config.addEnabled = false;
    this.config.selectionEnabled = false;
    this.config.searchEnabled = false;
    this.config.entitiesDeleteEnabled = false;
    this.config.displayPagination = false;

    this.config.cellActionDescriptors = this.configureCellActions();
    this.config.columns.push(
      new EntityTableColumn<ShortMqttAuthProvider>('type', 'authentication.type', '150px',
        (entity) => this.translate.instant(mqttAuthProviderTypeTranslationMap.get(entity.type)), undefined, false),
      new EntityTableColumn<ShortMqttAuthProvider>('description', 'authentication.description', '200px', undefined, undefined, false),
      new EntityTableColumn<ShortMqttAuthProvider>('status', 'authentication.status', '100px',
        entity => this.providerStatus(entity),
        entity => this.providerStatusStyle(entity),
        false
      ),
    );
    this.config.headerActionDescriptors.push(
      {
        name: this.translate.instant('authentication.settings'),
        icon: 'settings',
        isEnabled: () => true,
        onAction: ($event) => this.router.navigate(['/settings/security'])
      }
    );
    this.config.loadEntity = id => this.mqttAuthProviderService.getAuthProviderById(id);
    this.config.saveEntity = entity => this.mqttAuthProviderService.saveAuthProvider(entity as MqttAuthProvider);
    this.config.entitiesFetchFunction = pageLink => this.mqttAuthProviderService.getAuthProviders(pageLink);
  }

  resolve(): Observable<EntityTableConfig<ShortMqttAuthProvider>> {
    return of(this.config);
  }

  private configureCellActions(): Array<CellActionDescriptor<MqttAuthProvider>> {
    const actions: Array<CellActionDescriptor<MqttAuthProvider>> = [];
    actions.push(
      {
        name: this.translate.instant('authentication.enable'),
        nameFunction: (entity) => entity.enabled ? this.translate.instant('authentication.disable') : this.translate.instant('authentication.enable'),
        iconFunction: (entity) => entity.enabled ? 'mdi:toggle-switch' : 'mdi:toggle-switch-off-outline',
        isEnabled: () => true,
        onAction: ($event, entity) => this.switchStatus($event, entity)
      },
    );
    return actions;
  }

  private onAction(action: EntityAction<ShortMqttAuthProvider>, config: EntityTableConfig<ShortMqttAuthProvider>): boolean {
    switch (action.action) {
      case 'open':
        this.openMqttAuthProvider(action.event, action.entity, config);
        return true;
    }
    return false;
  }

  private openMqttAuthProvider($event: Event, user: ShortMqttAuthProvider, config: EntityTableConfig<ShortMqttAuthProvider>) {
    if ($event) {
      $event.stopPropagation();
    }
    const url = this.router.createUrlTree([user.id], {relativeTo: config.getActivatedRoute()});
    this.router.navigateByUrl(url);
  }

  switchStatus($event: Event, provider: MqttAuthProvider) {
    if ($event) {
      $event.stopPropagation();
    }
    this.mqttAuthProviderService.switchAuthProvider(provider.id, provider.enabled)
      .subscribe(() => {
        this.condfigService.fetchBrokerConfig().subscribe();
        this.config.getTable().updateData();
      });
  }

  private providerStatus(provider: ShortMqttAuthProvider): string {
    let translateKey = 'authentication.status-active';
    let backgroundColor = 'rgba(25, 128, 56, 0.08)';
    if (!provider.enabled) {
      translateKey = 'authentication.status-disabled';
      backgroundColor = 'rgba(0, 0, 0, 0.08)';
    }
    return `<div class="status" style="border-radius: 16px; height: 32px; line-height: 32px; padding: 0 12px; width: fit-content; background-color: ${backgroundColor}">
                ${this.translate.instant(translateKey)}
            </div>`;
  }

  private providerStatusStyle(provider: ShortMqttAuthProvider): object {
    const styleObj = {
      fontSize: '14px',
      color: '#198038',
      cursor: 'pointer'
    };
    if (!provider.enabled) {
      styleObj.color = 'rgba(0, 0, 0, 0.54)';
    }
    return styleObj;
  }
}
