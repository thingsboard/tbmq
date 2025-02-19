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

import { Component, Inject, OnDestroy, OnInit } from '@angular/core';
import { DialogComponent } from '@shared/components/dialog.component';
import { Store } from '@ngrx/store';
import { AppState } from '@core/core.state';
import { Router } from '@angular/router';
import { MAT_DIALOG_DATA, MatDialogRef, MatDialogContent, MatDialogActions } from '@angular/material/dialog';
import { AuthRulePatternsType, BasicCredentials, ClientCredentials } from '@shared/models/credentials.model';
import { ClientType } from '@shared/models/client.model';
import { randomStringFromRegex, getOS } from '@core/utils';
import { ConnectivitySettings } from '@shared/models/settings.models';
import { SettingsService } from '@core/http/settings.service';
import { MatToolbar } from '@angular/material/toolbar';
import { TranslateModule } from '@ngx-translate/core';
import { MatIconButton, MatButton, MatAnchor } from '@angular/material/button';
import { MatIcon } from '@angular/material/icon';
import { NgTemplateOutlet, AsyncPipe } from '@angular/common';
import { ToggleHeaderComponent, ToggleOption } from '@shared/components/toggle-header.component';
import { MatTabGroup, MatTab, MatTabLabel, MatTabContent } from '@angular/material/tabs';
import { TbMarkdownComponent } from '@shared/components/markdown.component';
import { MatSlideToggle } from '@angular/material/slide-toggle';
import { FormsModule } from '@angular/forms';
import { MatProgressSpinner } from '@angular/material/progress-spinner';

export interface CheckConnectivityDialogData {
  credentials: ClientCredentials;
  afterAdd: boolean;
}

export interface PublishTelemetryCommand {
  mqtt: {
    mqtt?: {
      sub?: string;
      pub?: string;
    };
    mqtts?: string | Array<string>;
    docker?: {
      mqtt?: {
        sub?: string;
        pub?: string;
      };
      mqtts?: string | Array<string>;
    };
    sparkplug?: string;
  };
}

export enum NetworkTransportType {
  MQTT = 'MQTT'
}

export interface MqttCommandConfig {
  clientId?: string;
  userName?: string;
  password?: string;
  hostname?: string;
  mqttPort?: string;
  cleanSession?: boolean;
  network?: string;
  message?: string;
  subTopic?: string;
  pubTopic?: string;
  debugQoS?: string;
}

@Component({
    selector: 'tb-check-connectivity-dialog',
    templateUrl: './check-connectivity-dialog.component.html',
    styleUrls: ['./check-connectivity-dialog.component.scss'],
    imports: [MatToolbar, TranslateModule, MatIconButton, MatIcon, MatDialogContent, ToggleHeaderComponent, ToggleOption, NgTemplateOutlet, MatTabGroup, MatTab, MatTabLabel, MatTabContent, TbMarkdownComponent, MatDialogActions, MatSlideToggle, FormsModule, MatButton, MatProgressSpinner, MatAnchor, AsyncPipe]
})
export class CheckConnectivityDialogComponent extends
  DialogComponent<CheckConnectivityDialogComponent> implements OnInit, OnDestroy {

  loadedCommand = false;
  status: boolean;
  commands: PublishTelemetryCommand;
  selectTransportType = NetworkTransportType.MQTT;
  NetworkTransportType = NetworkTransportType;
  AuthRulePatternsType = AuthRulePatternsType;
  showDontShowAgain: boolean;
  dialogTitle: string;
  notShowAgain = false;
  mqttTabIndex = 0;

  private connectivitySettings = this.settingsService.connectivitySettings;

  constructor(protected store: Store<AppState>,
              protected router: Router,
              @Inject(MAT_DIALOG_DATA) private data: CheckConnectivityDialogData,
              public dialogRef: MatDialogRef<CheckConnectivityDialogComponent>,
              private settingsService: SettingsService) {
    super(store, router, dialogRef);

    if (this.data.afterAdd) {
      this.dialogTitle = 'mqtt-client-credentials.connectivity.credentials-created-check-connectivity';
      this.showDontShowAgain = true;
    } else {
      this.dialogTitle = 'mqtt-client-credentials.connectivity.check-connectivity';
      this.showDontShowAgain = false;
    }
  }

  ngOnInit() {
    this.loadCommands(this.connectivitySettings)
  }

  ngOnDestroy() {
    super.ngOnDestroy();
  }

  close(): void {
    if (this.notShowAgain && this.showDontShowAgain) {
      localStorage.setItem('notDisplayCheckAfterAddCredentials', 'true');
      this.dialogRef.close(null);
    } else {
      this.dialogRef.close(null);
    }
  }

  createMarkDownCommand(commands: string | string[]): string {
    if (Array.isArray(commands)) {
      const formatCommands: Array<string> = [];
      commands.forEach(command => formatCommands.push(this.createMarkDownSingleCommand(command)));
      return formatCommands.join(`\n<br />\n\n`);
    } else {
      return this.createMarkDownSingleCommand(commands);
    }
  }

  checkCredentials(type: AuthRulePatternsType): boolean {
    const credentialsValue = JSON.parse(this.data.credentials.credentialsValue);
    const authRules = credentialsValue.authRules;
    switch (type) {
      case AuthRulePatternsType.PUBLISH:
        return !authRules.pubAuthRulePatterns?.length;
      case AuthRulePatternsType.SUBSCRIBE:
        return !authRules.subAuthRulePatterns?.length;
    }
  }

  private createMarkDownSingleCommand(command: string): string {
    return '```bash\n' +
      command +
      '{:copy-code}\n' +
      '```';
  }

  private loadCommands(data: ConnectivitySettings) {
    const config = this.setConfig(this.data.credentials);
    if (data.mqtt.enabled) {
      config.hostname = data.mqtt.host.toString();
      config.mqttPort = data.mqtt.port.toString();
    }
    const commonCommands = this.setCommonCommands(config);

    const subCommands: string[] = [];
    subCommands.push(
      'mosquitto_sub',
      config.debugQoS,
      `-h ${config.hostname}`,
      `-p ${config.mqttPort}`,
      `-t "${config.subTopic}"`,
      commonCommands,
      '-v'
    );

    const pubCommands: string[] = [];
    pubCommands.push(
      'mosquitto_pub',
      config.debugQoS,
      `-h ${config.hostname}`,
      `-p ${config.mqttPort}`,
      `-t "${config.pubTopic}"`,
      commonCommands,
      config.message
    );

    const dockerSubCommands: string[] = [];
    dockerSubCommands.push('docker run --rm');
    if (config.network) dockerSubCommands.push(config.network);
    dockerSubCommands.push(
      '-it thingsboard/mosquitto-clients mosquitto_sub -d -q 1',
      `-h ${config.hostname}`,
      `-p ${config.mqttPort}`,
      `-t "${config.subTopic}"`,
      commonCommands,
      '-v'
    );

    const dockerPubCommands: string[] = [];
    dockerPubCommands.push('docker run --rm');
    if (config.network) dockerPubCommands.push(config.network);
    dockerPubCommands.push(
      '-it thingsboard/mosquitto-clients mosquitto_pub -d -q 1',
      `-h ${config.hostname}`,
      `-p ${config.mqttPort}`,
      `-t "${config.pubTopic}"`,
      commonCommands,
      config.message
    );

    this.commands = {
      mqtt: {
        mqtt: {
          sub: subCommands.join(' '),
          pub: pubCommands.join(' ')
        },
        docker: {
          mqtt: {
            sub: dockerSubCommands.join(' '),
            pub: dockerPubCommands.join(' ')
          }
        }
      }
    }
    this.selectTabIndexForUserOS();
    this.loadedCommand = true;
  }

  private setConfig(credentials: ClientCredentials): MqttCommandConfig {
    const clientType = credentials.clientType;
    const credentialsValue = JSON.parse(credentials.credentialsValue);
    return {
      clientId: this.setClientId(credentialsValue, clientType),
      userName: credentialsValue.userName,
      password: this.setPassword(credentialsValue),
      subTopic: this.setTopic(credentialsValue.authRules.subAuthRulePatterns, 'tbmq/demo/+'),
      pubTopic: this.setTopic(credentialsValue.authRules.pubAuthRulePatterns, 'tbmq/demo/topic'),
      hostname: this.connectivitySettings.mqtt.host,
      mqttPort: this.connectivitySettings.mqtt.port.toString(),
      cleanSession: clientType === ClientType.APPLICATION,
      network: this.setNetworkCommand(window.location.hostname),
      message: "-m 'Hello World'",
      debugQoS: '-d -q 1'
    }
  }

  private setClientId(credentialsValue: BasicCredentials, clientType: ClientType): string {
    if (credentialsValue.clientId) {
      return credentialsValue.clientId;
    }
    if (clientType === ClientType.APPLICATION) {
      return 'tbmq_' + (Math.random().toString(36).slice(2, 7));
    }
    return null;
  }

  private setPassword(credentialsValue: BasicCredentials): string {
    if (this.data.credentials?.password) {
      return this.data.credentials?.password;
    }
    if (credentialsValue.password) {
      return '$YOUR_PASSWORD';
    }
    return null;
  }

  private setNetworkCommand(hostname: string): string {
    if (hostname === 'localhost' || hostname === '127.0.0.1') {
      return '--network=host'
    }
    return null;
  }

  private setTopic(rules: string[], topic: string): string {
    for (let i= 0; i < rules?.length; i++) {
      if (rules[i] === '.*') {
        return topic;
      }
    }
    if (rules?.length) {
      return randomStringFromRegex(rules[0]);
    }
    return '$YOUR_TOPIC';
  }

  private setCommonCommands(config: MqttCommandConfig): string {
    const clientInfoCommands: string[] = [];
    if (config.clientId) clientInfoCommands.push(`-i "${config.clientId}"`);
    if (config.userName) clientInfoCommands.push(`-u "${config.userName}"`);
    if (config.password) clientInfoCommands.push(`-P "${config.password}"`);
    if (config.cleanSession) clientInfoCommands.push('-c');
    return clientInfoCommands.join(' ');
  }

  private selectTabIndexForUserOS() {
    const currentOS = getOS();
    switch (currentOS) {
      case 'linux':
      case 'android':
        this.mqttTabIndex = 0;
        break;
      case 'macos':
      case 'ios':
        this.mqttTabIndex = 1;
        break;
      case 'windows':
        this.mqttTabIndex = 2;
        break;
      default:
        this.mqttTabIndex = 3;
    }
  }
}
