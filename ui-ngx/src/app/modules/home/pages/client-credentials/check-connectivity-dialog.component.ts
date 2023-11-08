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

import { Component, Inject, OnDestroy, OnInit } from '@angular/core';
import { DialogComponent } from '@shared/components/dialog.component';
import { select, Store } from '@ngrx/store';
import { AppState } from '@core/core.state';
import { Router } from '@angular/router';
import { MAT_DIALOG_DATA, MatDialogRef } from '@angular/material/dialog';
import { ClientCredentials } from '@shared/models/credentials.model';
import { ClientType } from '@shared/models/client.model';
import { selectUserDetails } from '@core/auth/auth.selectors';
import { map } from 'rxjs/operators';
import { ConfigParams } from '@shared/models/config.model';

export interface CheckConnectivityDialogData {
  credentials: ClientCredentials;
  afterAdd: boolean;
}

export interface PublishTelemetryCommand {
  mqtt: {
    mqtt?: {
      sub?: string,
      pub?: string
    };
    mqtts?: string | Array<string>;
    docker?: {
      mqtt?: {
        sub?: string,
        pub?: string
      },
      mqtts?: string | Array<string>;
    };
    sparkplug?: string;
  };
}

export enum NetworkTransportType {
  MQTT = 'MQTT'
}

@Component({
  selector: 'tb-check-connectivity-dialog',
  templateUrl: './check-connectivity-dialog.component.html',
  styleUrls: ['./check-connectivity-dialog.component.scss']
})
export class CheckConnectivityDialogComponent extends
  DialogComponent<CheckConnectivityDialogComponent> implements OnInit, OnDestroy {

  loadedCommand = false;
  status: boolean;
  commands: PublishTelemetryCommand;
  selectTransportType = NetworkTransportType.MQTT;
  NetworkTransportType = NetworkTransportType;
  showDontShowAgain: boolean;
  dialogTitle: string;
  notShowAgain = false;
  mqttTabIndex = 0;

  private mqttPort: string;

  constructor(protected store: Store<AppState>,
              protected router: Router,
              @Inject(MAT_DIALOG_DATA) private data: CheckConnectivityDialogData,
              public dialogRef: MatDialogRef<CheckConnectivityDialogComponent>) {
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
    this.loadConfig();
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

  private createMarkDownSingleCommand(command: string): string {
    return '```bash\n' +
      command +
      '{:copy-code}\n' +
      '```';
  }

  private loadConfig() {
    this.store.pipe(
      select(selectUserDetails),
      map((user) => user?.additionalInfo?.config))
      .pipe(
        map((data) => {
          this.mqttPort = data ? data[ConfigParams.tcpPort] : '1883';
          this.loadCommands();
          return true;
          }
        ))
      .subscribe();
  }

  private loadCommands() {
    const credentials = this.data.credentials;
    const clientType = credentials.clientType;
    const credentialsValue = JSON.parse(credentials.credentialsValue);
    const clientId = credentialsValue.clientId;
    const userName = credentialsValue.userName;
    const password = this.data.credentials?.password;
    const subTopic = this.getTopic(credentialsValue.authRules.subAuthRulePatterns, 'tbmq/demo/+');
    const pubTopic = this.getTopic(credentialsValue.authRules.pubAuthRulePatterns, 'tbmq/demo/topic');
    const hostname = window.location.hostname;
    const mqttPort = this.mqttPort;
    const subCommands: string[] = [];
    const pubCommands: string[] = [];
    const dockerSubCommands: string[] = [];
    const dockerPubCommands: string[] = [];
    const clientInfoCommands: string[] = [];
    const defaultOptions: string = '-d -q 1';

    if (clientId) clientInfoCommands.push(`-i ${clientId}`);
    if (userName) clientInfoCommands.push(`-u ${userName}`);
    if (password) clientInfoCommands.push(`-P ${password}`);
    const clientInfo = clientInfoCommands.join(' ');
    const message = "-m 'Hello World'";
    const network = (hostname === 'localhost' || hostname === '127.0.0.1') ? '--network=host' : '';
    const cleanSession = clientType === ClientType.APPLICATION ? '-c' : '';
    subCommands.push(
      "mosquitto_sub",
      defaultOptions,
      `-h ${hostname}`,
      `-p ${mqttPort}`,
      `-t ${subTopic}`,
      clientInfo,
      cleanSession,
      '-v'
    );
    pubCommands.push(
      "mosquitto_pub",
      defaultOptions,
      `-h ${hostname}`,
      `-p ${mqttPort}`,
      `-t ${pubTopic}`,
      clientInfo,
      message,
      cleanSession
    );
    dockerSubCommands.push(
      "docker run --rm",
      network,
      "-it thingsboard/mosquitto-clients mosquitto_sub -d -q 1",
      `-h ${hostname}`,
      `-p ${mqttPort}`,
      `-t ${subTopic}`,
      clientInfo,
      cleanSession,
      '-v'
    );
    dockerPubCommands.push(
      "docker run --rm",
      network,
      "-it thingsboard/mosquitto-clients mosquitto_pub -d -q 1",
      `-h ${hostname}`,
      `-p ${mqttPort}`,
      `-t ${pubTopic}`,
      clientInfo,
      message,
      cleanSession
    );
    const sub = subCommands.join(' ');
    const pub = pubCommands.join(' ');
    const dockerSub = dockerSubCommands.join(' ');
    const dockerPub = dockerPubCommands.join(' ');
    this.commands = {
      mqtt: {
        mqtt: {
          sub,
          pub
        },
        docker: {
          mqtt: {
            sub: dockerSub,
            pub: dockerPub
          }
        }
      }
    }
    this.loadedCommand = true;
  }

  private getTopic(rules: string[], topic: string): string {
    for (let i= 0; i < rules?.length; i++) {
      if (rules[i] === ".*") return topic;
    }
    return '$YOUR_TOPIC';
  }
}
