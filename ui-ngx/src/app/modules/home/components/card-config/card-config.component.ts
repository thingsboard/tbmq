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

import { AfterViewInit, Component } from '@angular/core';
import { Observable, of } from 'rxjs';
import {
  BrokerConfigTable,
  ConfigParams,
  ConfigParamsTranslationMap,
} from '@shared/models/config.model';
import { ActionNotificationShow } from '@core/notification/notification.actions';
import { Store } from '@ngrx/store';
import { AppState } from '@core/core.state';
import { TranslateService } from '@ngx-translate/core';
import { ConfigService } from '@core/http/config.service';
import { MqttClientCredentialsService } from '@core/http/mqtt-client-credentials.service';
import { PageLink } from '@shared/models/page/page-link';
import { MqttCredentialsType } from '@shared/models/client-crenetials.model';
import { formatBytes, KafkaTableComponent } from '@home/components/entity/kafka-table.component';
import { HomePageTitleType } from '@shared/models/home-page.model';
import { EntityColumn, EntityTableColumn } from '@home/models/entity/entities-table-config.models';
import { DomSanitizer } from '@angular/platform-browser';

@Component({
  selector: 'tb-card-config',
  templateUrl: './card-config.component.html',
  styleUrls: ['./card-config.component.scss']
})
export class CardConfigComponent extends KafkaTableComponent<BrokerConfigTable> implements AfterViewInit {

  cardType = HomePageTitleType.CONFIG;
  configParamsTranslationMap = ConfigParamsTranslationMap;
  configParams = ConfigParams;
  hasBasicCredentials: Observable<boolean>;
  hasX509AuthCredentials: Observable<boolean>;

  fetchEntities$ = () => this.configService.getBrokerConfigPageData();
  tooltipContent = (type) => `${this.translate.instant('config.warning', {type: this.translate.instant(type)})}`;

  constructor(protected store: Store<AppState>,
              private translate: TranslateService,
              private configService: ConfigService,
              protected domSanitizer: DomSanitizer,
              private mqttClientCredentialsService: MqttClientCredentialsService) {
    super(domSanitizer);
  }

  getColumns() {
    const columns: Array<EntityColumn<BrokerConfigTable>> = [];
    columns.push(
      new EntityTableColumn<BrokerConfigTable>('key', 'config.key', '80%',
          entity => this.translate.instant(this.configParamsTranslationMap.get(entity.key))),
      new EntityTableColumn<BrokerConfigTable>('value', 'config.value', '20%',
          entity => entity.value, () => ({color: 'rgba(0,0,0,0.54)'}))
    );
    return columns;
  }

  ngAfterViewInit(): void {
    this.mqttClientCredentialsService.getMqttClientsCredentials(new PageLink(10000)).subscribe(
      data => {
        this.hasBasicCredentials = of(!!data.data.find(el => el.credentialsType === MqttCredentialsType.MQTT_BASIC));
        this.hasX509AuthCredentials = of(!!data.data.find(el => el.credentialsType === MqttCredentialsType.SSL));
      }
    );
  }

  onCopy() {
    const message = this.translate.instant('config.port-copied');
    this.store.dispatch(new ActionNotificationShow({
      message,
      type: 'success',
      duration: 1500,
      verticalPosition: 'top',
      horizontalPosition: 'left'
    }));
  }

  viewDocumentation(page: string){
    window.open(`https://thingsboard.io/docs/mqtt-broker/${page}`, '_blank');
  }

  transformValue(entity: BrokerConfigTable) {
    if (entity.key === ConfigParams.tlsMaxPayloadSize || entity.key === ConfigParams.tcpMaxPayloadSize) {
      return formatBytes(entity.value);
    }
    return entity.value;
  }
}
