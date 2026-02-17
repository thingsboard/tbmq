///
/// Copyright © 2016-2026 The Thingsboard Authors
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

import { Component, OnInit } from '@angular/core';
import { ConfigService } from '@core/http/config.service';
import { MqttAuthProviderService } from '@core/http/mqtt-auth-provider.service';
import { HomePageTitleType } from '@shared/models/home-page.model';
import { CardTitleButtonComponent } from '@shared/components/button/card-title-button.component';
import { MatSlideToggle } from '@angular/material/slide-toggle';
import { FormsModule } from '@angular/forms';
import { TranslateModule } from '@ngx-translate/core';
import { ToggleHeaderComponent, ToggleOption } from '@shared/components/toggle-header.component';
import { MqttAuthProviderType, ShortMqttAuthProvider } from '@shared/models/mqtt-auth-provider.model';
import { PageLink } from '@shared/models/page/page-link';
import { formatBytes } from '@home/models/entity/entities-table-config.models';
import { ConfigParams } from '@shared/models/config.model';

@Component({
  selector: 'tb-home-advanced-settings',
  templateUrl: './advanced-settings.component.html',
  styleUrls: ['./advanced-settings.component.scss'],
  imports: [CardTitleButtonComponent, MatSlideToggle, FormsModule, TranslateModule, ToggleHeaderComponent, ToggleOption]
})
export class AdvancedSettingsComponent implements OnInit {

  cardType = HomePageTitleType.AUTH_CONFIG;
  viewMode = 'authentication';
  authProviders: ShortMqttAuthProvider[];
  AuthParamsType = MqttAuthProviderType;
  AdvancedParamsType = ConfigParams;

  authParams = {
    [MqttAuthProviderType.MQTT_BASIC]: false,
    [MqttAuthProviderType.X_509]: false,
    [MqttAuthProviderType.SCRAM]: false,
    [MqttAuthProviderType.JWT]: false,
    [MqttAuthProviderType.HTTP]: false
  };

  advancedParams = {
    [ConfigParams.tcpMaxPayloadSize]: '',
    [ConfigParams.tlsMaxPayloadSize]: '',
    [ConfigParams.wsMaxPayloadSize]: '',
    [ConfigParams.wssMaxPayloadSize]: '',
    [ConfigParams.allowKafkaTopicDeletion]: false
  };

  constructor(
    private configService: ConfigService,
    private mqttAuthProviderService: MqttAuthProviderService
  ) {}

  ngOnInit() {
    const config = this.configService.brokerConfig;

    this.authParams = {
      [MqttAuthProviderType.MQTT_BASIC]: config.basicAuthEnabled,
      [MqttAuthProviderType.X_509]: config.x509AuthEnabled,
      [MqttAuthProviderType.SCRAM]: config.scramAuthEnabled,
      [MqttAuthProviderType.JWT]: config.jwtAuthEnabled,
      [MqttAuthProviderType.HTTP]: config.httpAuthEnabled,
    };

    this.advancedParams = {
      [ConfigParams.tcpMaxPayloadSize]: formatBytes(config.tcpMaxPayloadSize),
      [ConfigParams.tlsMaxPayloadSize]: formatBytes(config.tlsMaxPayloadSize),
      [ConfigParams.wsMaxPayloadSize]: formatBytes(config.wsMaxPayloadSize),
      [ConfigParams.wssMaxPayloadSize]: formatBytes(config.wssMaxPayloadSize),
      [ConfigParams.allowKafkaTopicDeletion]: config.allowKafkaTopicDeletion
    };
  }

  switchAuthProvider(type: MqttAuthProviderType) {
    const value = !this.authParams[type];
    if (!this.authProviders) {
      const pageLink = new PageLink(10);
      this.mqttAuthProviderService.getAuthProviders(pageLink).subscribe(
        providersPageData => {
          this.authProviders = providersPageData.data;
          this.updateAuthProvider(type, value);
        }
      );
    } else {
      this.updateAuthProvider(type, value);
    }
  }

  private updateAuthProvider(type: MqttAuthProviderType, value: boolean) {
    const providerId = this.authProviders.find(e => e.type === type)?.id;
    if (providerId) {
      this.mqttAuthProviderService.switchAuthProvider(providerId, value).subscribe(
        () => this.configService.fetchBrokerConfig()
      );
    }
  }
}
