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

import { Component, Inject, ViewChild } from '@angular/core';
import { MAT_DIALOG_DATA, MatDialogRef } from '@angular/material/dialog';
import { Store } from '@ngrx/store';
import { AppState } from '@core/core.state';
import { FormBuilder, UntypedFormGroup, Validators } from '@angular/forms';
import { DialogComponent } from '@shared/components/dialog.component';
import { Router } from '@angular/router';
import { MatStepper, StepperOrientation } from '@angular/material/stepper';
import { Observable, throwError } from 'rxjs';
import { catchError, map } from 'rxjs/operators';
import { StepperSelectionEvent } from '@angular/cdk/stepper';
import { BreakpointObserver } from '@angular/cdk/layout';
import { MediaBreakpoints } from '@shared/models/constants';
import {
  clientCredentialsNameRandom,
  clientIdRandom,
  clientUserNameRandom,
  convertDataSizeUnits,
  convertTimeUnits,
  deepTrim,
  isDefinedAndNotNull
} from '@core/utils';
import { ClientCredentials, CredentialsType, credentialsTypeTranslationMap } from '@shared/models/credentials.model';
import { ClientCredentialsService } from '@core/http/client-credentials.service';
import { ClientType, clientTypeTranslationMap } from '@shared/models/client.model';
import {
  addressProtocols,
  Connection,
  DataSizeUnitType,
  dataSizeUnitTypeTranslationMap,
  mqttVersions,
  TimeUnitType,
  timeUnitTypeTranslationMap,
  WsAddressProtocolType,
  wsAddressProtocolTypeValueMap,
  WsCredentialsGeneratortTypeTranslationMap,
  WsCredentialsGeneratorType
} from '@shared/models/ws-client.model';
import { WsClientService } from '@core/http/ws-client.service';

export interface ConnectionDialogData {
  entity?: Connection;
  connectionsTotal?: number;
}

@Component({
  selector: 'tb-connection-wizard',
  templateUrl: './connection-wizard-dialog.component.html',
  styleUrls: ['./connection-wizard-dialog.component.scss']
})
export class ConnectionWizardDialogComponent extends DialogComponent<ConnectionWizardDialogComponent, Connection> {

  @ViewChild('addConnectionWizardStepper', {static: true}) addConnectionWizardStepper: MatStepper;

  stepperOrientation: Observable<StepperOrientation>;

  stepperLabelPosition: Observable<'bottom' | 'end'>;

  selectedIndex = 0;

  credentialsOptionalStep = true;

  showNext = true;
  useExistingCredentials = false;

  CredentialsType = CredentialsType;

  ClientType = ClientType;

  CredentialsTypes = Object.values(CredentialsType);

  credentialsTypeTranslationMap = credentialsTypeTranslationMap;

  clientTypeTranslationMap = clientTypeTranslationMap;

  clientTypes = Object.values(ClientType);

  addressProtocols = addressProtocols;
  WsAddressProtocolType = WsAddressProtocolType;
  wsAddressProtocolTypeValueMap = wsAddressProtocolTypeValueMap;

  WsCredentialsGeneratorType = WsCredentialsGeneratorType;
  WsCredentialsGeneratortTypeTranslationMap = WsCredentialsGeneratortTypeTranslationMap;

  timeUnitTypes = Object.keys(TimeUnitType);
  timeUnitTypeTranslationMap = timeUnitTypeTranslationMap;

  dataSizeUnitTypes = Object.keys(DataSizeUnitType);
  dataSizeUnitTypeTranslationMap = dataSizeUnitTypeTranslationMap;

  mqttVersions = mqttVersions;
  displayPasswordWarning: boolean;
  connectionFormGroup: UntypedFormGroup;
  connectionAdvancedFormGroup: UntypedFormGroup;
  lastWillFormGroup: UntypedFormGroup;
  userPropertiesFormGroup: UntypedFormGroup;

  title = 'ws-client.connections.add-connection';
  actionButtonLabel = 'action.add';
  connection: Connection;

  credentialsGeneratorType = WsCredentialsGeneratorType.AUTO;

  constructor(protected store: Store<AppState>,
              protected router: Router,
              public dialogRef: MatDialogRef<ConnectionWizardDialogComponent, Connection>,
              private clientCredentialsService: ClientCredentialsService,
              private wsClientService: WsClientService,
              private breakpointObserver: BreakpointObserver,
              private fb: FormBuilder,
              @Inject(MAT_DIALOG_DATA) public data: ConnectionDialogData) {
    super(store, router, dialogRef);

    this.stepperOrientation = this.breakpointObserver.observe(MediaBreakpoints['gt-sm'])
      .pipe(map(({matches}) => matches ? 'horizontal' : 'vertical'));

    this.stepperLabelPosition = this.breakpointObserver.observe(MediaBreakpoints['gt-sm'])
      .pipe(map(({matches}) => matches ? 'end' : 'bottom'));

    this.connection = this.data.entity;

    this.setConnectionFormGroup(this.connection);
    this.setConnectionAdvancedFormGroup(this.connection);
    this.setLastWillFormGroup();
    this.setUserPropertiesFormGroup();

    if (this.connection) {
      this.title = 'ws-client.connections.edit';
      this.actionButtonLabel = 'action.save';
      if (isDefinedAndNotNull(this.connection.clientCredentialsId)) {
        this.credentialsGeneratorType = WsCredentialsGeneratorType.EXISTING;
      } else {
        this.credentialsGeneratorType = WsCredentialsGeneratorType.CUSTOM;
        this.onCredentialsGeneratorChange(WsCredentialsGeneratorType.CUSTOM);
      }
    }
  }

  private setConnectionFormGroup(entity: Connection) {
    const url = 'ws://' + window.location.hostname + ':' + 8084 + '/mqtt';
    this.connectionFormGroup = this.fb.group({
      name: [entity ? entity.name : this.generateWebSocketClientName(), [Validators.required]],
      url: [entity ? entity.url : url, [Validators.required]],
      credentialsName: [{value: this.generateWebSocketClientName(), disabled: true}, [Validators.required]],
      clientId: [{value: entity ? entity.clientId : clientIdRandom(), disabled: true}, [Validators.required]],
      username: [{value: entity ? entity.username : clientUserNameRandom(), disabled: true}, []],
      password: [null, []],
      clientCredentials: [null, []],
      clientCredentialsId: [null, []]
    });
  }

  private setConnectionAdvancedFormGroup(entity: Connection) {
    this.connectionAdvancedFormGroup = this.fb.group({
      clean: [entity ? entity.clean : false, []],
      keepalive: [entity ? convertTimeUnits(entity.keepalive, TimeUnitType.SECONDS, entity.keepaliveUnit) : 60, [Validators.required]],
      keepaliveUnit: [entity ? entity.keepaliveUnit : TimeUnitType.SECONDS, []],
      connectTimeout: [entity ? convertTimeUnits(entity.connectTimeout, TimeUnitType.MILLISECONDS, entity.connectTimeoutUnit) : 30 * 1000, [Validators.required]],
      connectTimeoutUnit: [entity ? entity.connectTimeoutUnit : TimeUnitType.MILLISECONDS, []],
      reconnectPeriod: [entity ? convertTimeUnits(entity.reconnectPeriod, TimeUnitType.MILLISECONDS, entity.reconnectPeriodUnit) : 1000, [Validators.required]],
      reconnectPeriodUnit: [entity ? entity.reconnectPeriodUnit : TimeUnitType.MILLISECONDS, []],
      protocolVersion: [entity ? entity.protocolVersion : 5, []],
      properties: this.fb.group({
        sessionExpiryInterval: [entity ? convertTimeUnits(entity.properties.sessionExpiryInterval, TimeUnitType.SECONDS, entity.properties.sessionExpiryIntervalUnit) : null, []],
        sessionExpiryIntervalUnit: [entity ? entity.properties.sessionExpiryIntervalUnit : TimeUnitType.SECONDS, []],
        maximumPacketSize: [entity ? convertDataSizeUnits(entity.properties.maximumPacketSize, DataSizeUnitType.B, entity.properties.maximumPacketSizeUnit) : null, []],
        maximumPacketSizeUnit: [entity ? entity.properties.maximumPacketSizeUnit : DataSizeUnitType.KB, []],
        topicAliasMaximum: [entity ? entity.properties.topicAliasMaximum : null, []],
        receiveMaximum: [entity ? entity.properties.receiveMaximum : null, []],
        requestResponseInfo: [entity ? entity.properties.requestResponseInformation : null, []],
      })
    });
  }

  private setLastWillFormGroup() {
    this.lastWillFormGroup  = this.fb.group({
        will: [null, []]
      }
    );
  }

  private setUserPropertiesFormGroup() {
    this.userPropertiesFormGroup  = this.fb.group({
        userProperties: [null, []]
      }
    );
  }

  onAddressProtocolChange(value: WsAddressProtocolType) {
    const url = this.connectionFormGroup.get('url').value;
    const divider = '://';
    const protocol = url.split(divider);
    const host = protocol[1] ? protocol[1] : (window.location.hostname + ':' + 8084 + '/mqtt');
    const newUrl = value.toLowerCase() + divider + host;
    this.connectionFormGroup.get('url').patchValue(newUrl);
    /*const newUrl = this.connectionFormGroup.get('url').value === WsAddressProtocolType.WSS
      ? url.replace('ws://', 'wss://')
      : url.replace('wss://', 'ws://');
    this.connectionFormGroup.get('url').patchValue('1234');*/
  }

  onCredentialsGeneratorChange(value: WsCredentialsGeneratorType) {
    if (value === WsCredentialsGeneratorType.AUTO) {
      this.connectionFormGroup.get('credentialsName').disable();
      this.connectionFormGroup.get('clientId').patchValue(clientIdRandom());
      this.connectionFormGroup.get('clientId').disable();
      this.connectionFormGroup.get('username').patchValue(clientUserNameRandom());
      this.connectionFormGroup.get('username').disable();
      this.connectionFormGroup.get('password').patchValue(null);
      this.connectionFormGroup.get('clientCredentials').patchValue(null);
      this.connectionFormGroup.get('clientCredentialsId').patchValue(null);
      this.connectionFormGroup.get('clientCredentials').clearValidators();
    }
    if (value === WsCredentialsGeneratorType.CUSTOM) {
      this.connectionFormGroup.get('clientId').enable();
      this.connectionFormGroup.get('clientId').patchValue(this.connection?.clientId ? this.connection.clientId : clientIdRandom());
      this.connectionFormGroup.get('username').enable();
      this.connectionFormGroup.get('username').patchValue(this.connection?.username ? this.connection.username : null);
      this.connectionFormGroup.get('password').patchValue(null);
      this.connectionFormGroup.get('clientCredentialsId').patchValue(null);
      this.connectionFormGroup.get('clientCredentials').patchValue(null);
      this.connectionFormGroup.get('clientCredentials').clearValidators();
    }
    if (value === WsCredentialsGeneratorType.EXISTING) {
      this.connectionFormGroup.get('credentialsName').enable();
      this.connectionFormGroup.get('clientId').patchValue(null);
      this.connectionFormGroup.get('clientId').disable();
      this.connectionFormGroup.get('username').patchValue(null);
      this.connectionFormGroup.get('username').disable();
      this.connectionFormGroup.get('password').patchValue(null);
      this.connectionFormGroup.get('clientCredentials').setValidators(Validators.required);
    }
    this.connectionFormGroup.updateValueAndValidity();
  }

  cancel(): void {
    this.dialogRef.close(null);
  }

  previousStep(): void {
    this.addConnectionWizardStepper.previous();
  }

  nextStep(): void {
    this.addConnectionWizardStepper.next();
  }

  getFormLabel(index: number): string {
    switch (index) {
      case 0:
        return;
      case 1:
        return 'ws-client.connections.advanced-settings';
      case 2:
        return 'ws-client.last-will.last-will';
      case 3:
        return 'retained-message.user-properties';
    }
  }

  get maxStepperIndex(): number {
    return this.addConnectionWizardStepper?._steps?.length - 1;
  }

  save(): void {
    if (this.allValid()) {
      if (this.credentialsGeneratorType === WsCredentialsGeneratorType.AUTO) {
        const name = this.connectionFormGroup.get('credentialsName').value;
        const clientId = this.connectionFormGroup.get('clientId').value;
        const username = this.connectionFormGroup.get('username').value;
        const newCredentials = new BasicClientCredentials(name, clientId, username);
        this.clientCredentialsService.saveClientCredentials(newCredentials).subscribe(
          credentials => {
            this.connectionFormGroup.get('clientCredentials').patchValue(credentials);
            this.createClientCredentials().subscribe(
              (entity) => {
                return this.dialogRef.close(entity);
              }
            );
          }
        )
      } else {
        this.createClientCredentials().subscribe(
          (entity) => {
            return this.dialogRef.close(entity);
          }
        );
      }
    }
  }

  private createClientCredentials(): Observable<Connection> {
    const connectionFormGroupValue = {
      ...this.connectionFormGroup.getRawValue(),
      ...this.connectionAdvancedFormGroup.getRawValue(),
      ...this.lastWillFormGroup.getRawValue()
    };
    const result: Connection = this.transformValues(deepTrim(connectionFormGroupValue));
    const userProperties = this.userPropertiesFormGroup.getRawValue().userProperties;
    if (isDefinedAndNotNull(userProperties)) {
      result.properties.userProperties = userProperties;
    }
    return this.wsClientService.saveConnection(result).pipe(
      catchError(e => {
        this.addConnectionWizardStepper.selectedIndex = 0;
        return throwError(e);
      })
    );
  }

  private transformValues(entity: Connection): Connection {
    entity.clientCredentialsId = isDefinedAndNotNull(entity.clientCredentials) ? entity.clientCredentials.id : null;
    entity.keepalive = convertTimeUnits(entity.keepalive, entity.keepaliveUnit, TimeUnitType.SECONDS);
    entity.connectTimeout = convertTimeUnits(entity.connectTimeout, entity.connectTimeoutUnit, TimeUnitType.MILLISECONDS);
    entity.reconnectPeriod = convertTimeUnits(entity.reconnectPeriod, entity.reconnectPeriodUnit, TimeUnitType.MILLISECONDS);
    entity.properties.sessionExpiryInterval = convertTimeUnits(entity.properties.sessionExpiryInterval, entity.properties.sessionExpiryIntervalUnit, TimeUnitType.SECONDS);
    entity.properties.maximumPacketSize = convertDataSizeUnits(entity.properties.maximumPacketSize, entity.properties.maximumPacketSizeUnit, DataSizeUnitType.B);
    if (isDefinedAndNotNull(entity.will?.properties)) {
      entity.will.properties.messageExpiryInterval = convertTimeUnits(entity.will.properties.messageExpiryInterval, entity.will.properties.messageExpiryIntervalUnit, TimeUnitType.SECONDS);
      entity.will.properties.willDelayInterval = convertTimeUnits(entity.will.properties.willDelayInterval, entity.will.properties.willDelayIntervalUnit, TimeUnitType.SECONDS);
    }
    delete entity.clientCredentials;
    return entity;
  }

  allValid(): boolean {
    return !this.addConnectionWizardStepper.steps.find((item, index) => {
      if (item.stepControl.invalid) {
        item.interacted = true;
        this.addConnectionWizardStepper.selectedIndex = index;
        return true;
      } else {
        return false;
      }
    });
  }

  changeStep($event: StepperSelectionEvent): void {
    this.selectedIndex = $event.selectedIndex;
    this.showNext = this.selectedIndex !== this.maxStepperIndex;
  }

  clientCredentialsChanged(credentials: ClientCredentials) {
    if (credentials?.credentialsValue) {
      const credentialsValue = JSON.parse(credentials.credentialsValue);
      this.connectionFormGroup.patchValue({
        clientId: credentialsValue.clientId || clientIdRandom(),
        username: credentialsValue.userName,
        password: null
      });
      if (isDefinedAndNotNull(credentialsValue.password)) {
        this.displayPasswordWarning = true;
        this.connectionFormGroup.get('password').setValidators([Validators.required]);
      } else {
        this.displayPasswordWarning = false;
        this.connectionFormGroup.get('password').clearValidators();
      }
      this.connectionFormGroup.get('password').updateValueAndValidity();
    } else {
      this.displayPasswordWarning = false;
      this.connectionFormGroup.get('password').clearValidators();
      this.connectionFormGroup.get('password').updateValueAndValidity();
    }
  }

  regenerate(type: string) {
    switch (type) {
      case 'name':
        this.connectionFormGroup.patchValue({
          credentialsName: clientCredentialsNameRandom()
        });
        break;
      case 'clientId':
        this.connectionFormGroup.patchValue({
          clientId: clientIdRandom()
        });
        break;
      case 'username':
        this.connectionFormGroup.patchValue({
          username: clientUserNameRandom()
        });
        break;
    }
  }

  private generateWebSocketClientName(): string {
    return 'WebSocket Connection ' + (this.data.connectionsTotal + 1)
  }
}


export class BasicClientCredentials {
  credentialsType = CredentialsType.MQTT_BASIC;
  clientType = ClientType.APPLICATION;
  name: string;
  credentialsValue: any = {};

  constructor (name: string,
               clientId: string,
               username: string) {
    this.name = name;
    this.credentialsValue = JSON.stringify({
      clientId,
      userName: username,
      password: null,
      authRules: {
        pubAuthRulePatterns: [".*"],
        subAuthRulePatterns: [".*"]
      }
    });
  }
}
