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

import { Component } from '@angular/core';
import {
  BrokerConfigTableParam,
  BrokerConfig,
  ConfigParams,
  ConfigParamAuthProviderTypeMap,
  ConfigParamTranslationMap,
  ConfigParamAuthProviderTranslationMap,
  customValueKeys,
  allowedFlagKeys,
} from '@shared/models/config.model';
import { Store } from '@ngrx/store';
import { AppState } from '@core/core.state';
import { TranslateService, TranslateModule } from '@ngx-translate/core';
import { ConfigService } from '@core/http/config.service';
import { HomePageTitleType } from '@shared/models/home-page.model';
import { EntityColumn, EntityTableColumn, formatBytes } from '@home/models/entity/entities-table-config.models';
import { DomSanitizer } from '@angular/platform-browser';
import { mergeMap } from 'rxjs/operators';
import { EntitiesTableHomeNoPagination } from '../entity/entities-table-no-pagination.component';
import { CardTitleButtonComponent } from '@shared/components/button/card-title-button.component';
import { MatTable, MatColumnDef, MatHeaderCellDef, MatHeaderCell, MatCellDef, MatCell, MatHeaderRowDef, MatHeaderRow, MatRowDef, MatRow } from '@angular/material/table';
import { CopyButtonComponent } from '@shared/components/button/copy-button.component';
import { MatIcon } from '@angular/material/icon';
import { MatTooltip } from '@angular/material/tooltip';
import { MatSlideToggle } from '@angular/material/slide-toggle';
import { FormsModule } from '@angular/forms';
import { MqttAuthProviderService } from '@core/http/mqtt-auth-provider.service';
import { MqttAuthProviderType, ShortMqttAuthProvider } from '@shared/models/mqtt-auth-provider.model';
import { PageLink } from '@shared/models/page/page-link';
import { of } from 'rxjs';
import { HelpLinks } from '@shared/models/constants';

@Component({
    selector: 'tb-card-config',
    templateUrl: './card-config.component.html',
    styleUrls: ['./card-config.component.scss'],
    imports: [CardTitleButtonComponent, MatTable, MatColumnDef, MatHeaderCellDef, MatHeaderCell, MatCellDef, MatCell, TranslateModule, CopyButtonComponent, MatIcon, MatTooltip, MatHeaderRowDef, MatHeaderRow, MatRowDef, MatRow, MatSlideToggle, FormsModule]
})
export class CardConfigComponent extends EntitiesTableHomeNoPagination<BrokerConfigTableParam> {

  cardType = HomePageTitleType.CONFIG;
  configParamTranslationMap = ConfigParamTranslationMap;
  configParamAuthProviderTranslationMap = ConfigParamAuthProviderTranslationMap;
  configParamAuthProviderTypeMap = ConfigParamAuthProviderTypeMap;
  authProviders: ShortMqttAuthProvider[];

  fetchEntities$ = () => this.configService.fetchBrokerConfig().pipe(
    mergeMap(() => {
      return this.configService.getBrokerConfigPageData().pipe(
        mergeMap(data => {
          data.data = data.data.filter(el =>
            el.key !== ConfigParams.existsBasicCredentials &&
            el.key !== ConfigParams.existsX509Credentials &&
            el.key !== ConfigParams.existsScramCredentials);
          return of(data);
        })
      );
    })
  );

  tooltipContent = (key: ConfigParams): string => {
    const type = this.translate.instant(this.configParamAuthProviderTranslationMap.get(key));
    return `${this.translate.instant('config.warning', {type})}`;
  }

  constructor(protected store: Store<AppState>,
              private translate: TranslateService,
              private configService: ConfigService,
              private mqttAuthProviderService: MqttAuthProviderService,
              protected domSanitizer: DomSanitizer) {
    super(domSanitizer);
  }

  getColumns() {
    const columns: Array<EntityColumn<BrokerConfigTableParam>> = [];
    columns.push(
      new EntityTableColumn<BrokerConfigTableParam>('key', 'config.key', '70%'),
      new EntityTableColumn<BrokerConfigTableParam>('value', 'config.value', '30%',
          entity => entity.value, () => ({color: 'rgba(0,0,0,0.54)'}))
    );
    return columns;
  }

  gotoSecurityDocs() {
    const link = HelpLinks.linksMap.securitySettings + '/#authentication'
    window.open(link, '_blank');
  }

  transformValue(entity: BrokerConfigTableParam): string {
    const key = entity.key;
    const value = entity.value;
    if (key === ConfigParams.tlsMaxPayloadSize ||
        key === ConfigParams.tcpMaxPayloadSize ||
        key === ConfigParams.wsMaxPayloadSize ||
        key === ConfigParams.wssMaxPayloadSize) {
      return formatBytes(value);
    }
    if (customValueKeys.includes(key)) {
      if (allowedFlagKeys.includes(key)) {
        return value ? this.translate.instant('common.allowed') : this.translate.instant('common.not-allowed');
      }
    }
    if (typeof value === 'boolean') {
      return value ? this.translate.instant('common.enabled') : this.translate.instant('common.disabled');
    }
    return value;
  }

  showCopyButton(entity: BrokerConfigTableParam): boolean {
    return entity.key === ConfigParams.tlsPort || entity.key === ConfigParams.tcpPort ||
           entity.key === ConfigParams.wsPort || entity.key === ConfigParams.wssPort;
  }

  showWarningIcon(entity: BrokerConfigTableParam): boolean {
    const brokerConfig: BrokerConfig = this.configService.brokerConfig;
    return entity.value === false &&
           ((entity.key === ConfigParams.x509AuthEnabled && brokerConfig.existsX509Credentials) ||
            (entity.key === ConfigParams.basicAuthEnabled && brokerConfig.existsBasicCredentials) ||
            (entity.key === ConfigParams.scramAuthEnabled && brokerConfig.existsScramCredentials));
  }

  isAuthProviderParam(entity: BrokerConfigTableParam) {
    return this.configParamAuthProviderTypeMap.has(entity.key);
  }

  switchParam(entity: BrokerConfigTableParam) {
    const providerType = this.configParamAuthProviderTypeMap.get(entity.key);
    const initValue = !entity.value;
    if (!this.authProviders) {
      const pageLink = new PageLink(10);
      this.mqttAuthProviderService.getAuthProviders(pageLink).subscribe(
        providersPageData => {
          this.authProviders = providersPageData.data;
          this.switchAuthProvider(providerType, initValue);
        }
      );
    } else {
      this.switchAuthProvider(providerType, initValue);
    }
  }

  private switchAuthProvider(providerType: MqttAuthProviderType, value: boolean) {
    const providerId = this.authProviders.find(e => e.type === providerType).id;
    this.mqttAuthProviderService.switchAuthProvider(providerId, value).subscribe(() => this.configService.fetchBrokerConfig());
  }
}
