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
import { clientIdRandom, deepTrim, isDefinedAndNotNull, randomAlphanumeric } from '@core/utils';
import { ClientCredentials, CredentialsType, credentialsTypeTranslationMap } from "@shared/models/credentials.model";
import { ClientCredentialsService } from "@core/http/client-credentials.service";
import { ClientType, clientTypeTranslationMap } from "@shared/models/client.model";
import { AddEntityDialogData } from "@home/models/entity/entity-component.models";
import { BaseData } from "@shared/models/base-data";
import { addressProtocols, ConnectionDetailed, mqttVersions } from '@shared/models/ws-client.model';
import { WsClientService } from "@core/http/ws-client.service";

@Component({
  selector: 'tb-connection-wizard',
  templateUrl: './connection-wizard-dialog.component.html',
  styleUrls: ['./connection-wizard-dialog.component.scss']
})
export class ConnectionWizardDialogComponent extends DialogComponent<ConnectionWizardDialogComponent, ConnectionDetailed> {

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
  mqttVersions = mqttVersions;
  displayPasswordWarning: boolean;
  selectedExistingCredentials: ClientCredentials;
  connectionFormGroup: UntypedFormGroup;
  lastWillFormGroup: UntypedFormGroup;
  userPropertiesFormGroup: UntypedFormGroup;

  constructor(protected store: Store<AppState>,
              protected router: Router,
              public dialogRef: MatDialogRef<ConnectionWizardDialogComponent, ConnectionDetailed>,
              private clientCredentialsService: ClientCredentialsService,
              private wsClientService: WsClientService,
              private breakpointObserver: BreakpointObserver,
              private fb: FormBuilder,
              @Inject(MAT_DIALOG_DATA) public data: AddEntityDialogData<BaseData>) {
    super(store, router, dialogRef);

    this.stepperOrientation = this.breakpointObserver.observe(MediaBreakpoints['gt-sm'])
      .pipe(map(({matches}) => matches ? 'horizontal' : 'vertical'));

    this.stepperLabelPosition = this.breakpointObserver.observe(MediaBreakpoints['gt-sm'])
      .pipe(map(({matches}) => matches ? 'end' : 'bottom'));

    this.connectionFormGroup = this.fb.group({
      name: [randomAlphanumeric(5), [Validators.required]],
      protocol: ['ws://', [Validators.required]],
      host: [window.location.hostname, [Validators.required]],
      port: [8084, [Validators.required]],
      path: ['/mqtt', [Validators.required]],
      clientId: [clientIdRandom, [Validators.required]],
      username: [null, []],
      password: [null, []],
      keepAlive: [0, [Validators.required]],
      reconnectPeriod: [1000, [Validators.required]],
      connectTimeout: [30 * 1000, [Validators.required]],
      clean: [true, []],
      protocolVersion: [5, []],
      properties: this.fb.group({
        sessionExpiryInterval: [null, []],
        maximumPacketSize: [null, []],
        topicAliasMaximum: [null, []]
      }),
      autocomplete: [null, []]
    });

    this.lastWillFormGroup  = this.fb.group({
        lastWill: [null, []]
      }
    );

    this.userPropertiesFormGroup  = this.fb.group({
        userProperties: [null, []]
      }
    );
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
        return 'ws-client.last-will.last-will';
      case 2:
        return 'retained-message.user-properties';
    }
  }

  get maxStepperIndex(): number {
    return this.addConnectionWizardStepper?._steps?.length - 1;
  }

  add(): void {
    if (this.allValid()) {
      this.createClientCredentials().subscribe(
        (entity) => {
          return this.dialogRef.close(entity);
        }
      );
    }
  }

  private createClientCredentials(): Observable<ConnectionDetailed> {
    const connectionFormGroupValue = {
      ...this.connectionFormGroup.getRawValue(),
      ...this.lastWillFormGroup.getRawValue(),
      ...this.userPropertiesFormGroup.getRawValue()
    };
    return this.wsClientService.saveConnection(deepTrim(connectionFormGroupValue)).pipe(
      catchError(e => {
        this.addConnectionWizardStepper.selectedIndex = 0;
        return throwError(e);
      })
    );
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
        clientId: credentialsValue.clientId || clientIdRandom,
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
      this.connectionFormGroup.patchValue({
        clientId: clientIdRandom,
        username: null,
        password: null
      });
      if (this.displayPasswordWarning) this.displayPasswordWarning = false;
      this.connectionFormGroup.get('password').clearValidators();
      this.connectionFormGroup.get('password').updateValueAndValidity();
    }
  }
}
