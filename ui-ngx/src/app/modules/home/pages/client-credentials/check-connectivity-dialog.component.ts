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
import { Store } from '@ngrx/store';
import { AppState } from '@core/core.state';
import { Router } from '@angular/router';
import { MAT_DIALOG_DATA, MatDialogRef } from '@angular/material/dialog';
import { ClientCredentials } from '@shared/models/credentials.model';

export interface CheckConnectivityDialogData {
  credentials: ClientCredentials;
  afterAdd: boolean;
}

export interface PublishTelemetryCommand {
  mqtt: {
    mqtt?: string;
    mqtts?: string | Array<string>;
    docker?: {
      mqtt?: string;
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

  // private telemetrySubscriber: TelemetrySubscriber;

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
    this.loadCommands();
    this.subscribeToLatestTelemetry();
  }

  ngOnDestroy() {
    super.ngOnDestroy();
    // this.telemetrySubscriber?.complete();
    // this.telemetrySubscriber?.unsubscribe();
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

  private loadCommands() {

    this.commands = {
      "mqtt": {
        "mqtt": "mosquitto_pub -d -q 1 -h demo.thingsboard.io -p 1883 -t v1/devices/me/telemetry -u zACgOfMKhYmD1I0m38NP -m \"{temperature:25}\"",
        "docker": {
          "mqtt": "docker run --rm -it thingsboard/mosquitto-clients mosquitto_pub -d -q 1 -h demo.thingsboard.io -p 1883 -t v1/devices/me/telemetry -u zACgOfMKhYmD1I0m38NP -m \"{temperature:25}\""
        }
      }
    };
    this.loadedCommand = true;
    /*this.deviceService.getDevicePublishTelemetryCommands(this.data.deviceId.id).subscribe(
      commands => {
        this.commands = commands;
        const commandsProtocols = Object.keys(commands);
        this.transportTypes.forEach(transport => {
          const findCommand = commandsProtocols.find(item => item.toUpperCase().startsWith(transport));
          if (findCommand) {
            this.allowTransportType.add(transport);
          }
        });
        this.selectTransportType = this.allowTransportType.values().next().value;
        this.selectTabIndexForUserOS();
        this.loadedCommand = true;
      }
    );*/
  }

  private subscribeToLatestTelemetry() {
   /* this.store.pipe(select(selectPersistDeviceStateToTelemetry)).pipe(
      take(1)
    ).subscribe(persistToTelemetry => {
      this.telemetrySubscriber = TelemetrySubscriber.createEntityAttributesSubscription(
        this.telemetryWsService, this.data.deviceId, LatestTelemetry.LATEST_TELEMETRY, this.zone);
      if (!persistToTelemetry) {
        const subscriptionCommand = new AttributesSubscriptionCmd();
        subscriptionCommand.entityType = this.data.deviceId.entityType as EntityType;
        subscriptionCommand.entityId = this.data.deviceId.id;
        subscriptionCommand.scope = AttributeScope.SERVER_SCOPE;
        subscriptionCommand.keys = 'active';
        this.telemetrySubscriber.subscriptionCommands.push(subscriptionCommand);
      }

      this.telemetrySubscriber.subscribe();
      this.telemetrySubscriber.attributeData$().subscribe(
        (data) => {
          const telemetry = data.reduce<Array<AttributeData>>((accumulator, item) => {
            if (item.key === 'active') {
              this.status = coerceBooleanProperty(item.value);
            } else if (item.lastUpdateTs > this.currentTime) {
              accumulator.push(item);
            }
            return accumulator;
          }, []);
          this.latestTelemetry = telemetry.sort((a, b) => b.lastUpdateTs - a.lastUpdateTs);
        }
      );
    });*/
  }

}
