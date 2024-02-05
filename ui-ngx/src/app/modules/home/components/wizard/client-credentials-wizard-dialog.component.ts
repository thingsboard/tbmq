///
/// Copyright © 2016-2024 The Thingsboard Authors
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

import {Component, Inject, ViewChild} from '@angular/core';
import {MAT_DIALOG_DATA, MatDialogRef} from '@angular/material/dialog';
import {Store} from '@ngrx/store';
import {AppState} from '@core/core.state';
import {FormBuilder, UntypedFormGroup, Validators} from '@angular/forms';
import {DialogComponent} from '@shared/components/dialog.component';
import {Router} from '@angular/router';
import {MatStepper, StepperOrientation} from '@angular/material/stepper';
import {Observable, throwError} from 'rxjs';
import {catchError, map} from 'rxjs/operators';
import {StepperSelectionEvent} from '@angular/cdk/stepper';
import {BreakpointObserver} from '@angular/cdk/layout';
import {MediaBreakpoints} from '@shared/models/constants';
import {deepTrim, isDefinedAndNotNull} from '@core/utils';
import {ClientCredentials, CredentialsType, credentialsTypeTranslationMap} from "@shared/models/credentials.model";
import {ClientCredentialsService} from "@core/http/client-credentials.service";
import {ClientType, clientTypeTranslationMap} from "@shared/models/client.model";
import {AddEntityDialogData} from "@home/models/entity/entity-component.models";
import {BaseData} from "@shared/models/base-data";

@Component({
  selector: 'tb-client-credentials-wizard',
  templateUrl: './client-credentials-wizard-dialog.component.html',
  styleUrls: ['./client-credentials-wizard-dialog.component.scss']
})
export class ClientCredentialsWizardDialogComponent extends DialogComponent<ClientCredentialsWizardDialogComponent, ClientCredentials> {

  @ViewChild('addClientCredentialsWizardStepper', {static: true}) addClientCredentialsWizardStepper: MatStepper;

  stepperOrientation: Observable<StepperOrientation>;

  stepperLabelPosition: Observable<'bottom' | 'end'>;

  selectedIndex = 0;

  credentialsOptionalStep = true;

  showNext = true;

  CredentialsType = CredentialsType;

  ClientType = ClientType;

  CredentialsTypes = Object.values(CredentialsType);

  credentialsTypeTranslationMap = credentialsTypeTranslationMap;

  clientTypeTranslationMap = clientTypeTranslationMap;

  clientTypes = Object.values(ClientType);

  clientCredentialsWizardFormGroup: UntypedFormGroup;

  authenticationFormGroup: UntypedFormGroup;

  constructor(protected store: Store<AppState>,
              protected router: Router,
              public dialogRef: MatDialogRef<ClientCredentialsWizardDialogComponent, ClientCredentials>,
              private clientCredentialsService: ClientCredentialsService,
              private breakpointObserver: BreakpointObserver,
              private fb: FormBuilder,
              @Inject(MAT_DIALOG_DATA) public data: AddEntityDialogData<BaseData>,
  ) {
    super(store, router, dialogRef);

    this.stepperOrientation = this.breakpointObserver.observe(MediaBreakpoints['gt-sm'])
      .pipe(map(({matches}) => matches ? 'horizontal' : 'vertical'));

    this.stepperLabelPosition = this.breakpointObserver.observe(MediaBreakpoints['gt-sm'])
      .pipe(map(({matches}) => matches ? 'end' : 'bottom'));

    this.clientCredentialsWizardFormGroup = this.fb.group({
        name: [null, [Validators.required]],
        clientType: [ClientType.DEVICE, [Validators.required]],
        credentialsType: [CredentialsType.MQTT_BASIC, [Validators.required]],
      }
    );

    this.authenticationFormGroup  = this.fb.group({
        credentialsValue: [null, []]
      }
    );

    if (isDefinedAndNotNull(this.data?.entitiesTableConfig?.demoData)) {
      for (const [key, value] of Object.entries(this.data.entitiesTableConfig.demoData)) {
        let form: UntypedFormGroup;
        if (key === 'name' || key === 'clientType' || key === 'credentialsType') {
          form = this.clientCredentialsWizardFormGroup;
        }
        if (key === 'credentialsValue') {
          form = this.authenticationFormGroup;
        }
        form.patchValue({
          [key]: value
        });
      }
    }

    this.clientCredentialsWizardFormGroup.get('credentialsType').valueChanges.subscribe(
      (type) => {
        this.authenticationFormGroup.patchValue({credentialsValue: null}, {emitEvent:false});
      }
    );
  }

  cancel(): void {
    this.dialogRef.close(null);
  }

  previousStep(): void {
    this.addClientCredentialsWizardStepper.previous();
  }

  nextStep(): void {
    this.addClientCredentialsWizardStepper.next();
  }

  getFormLabel(index: number): string {
    switch (index) {
      case 0:
        return 'mqtt-client-credentials.client-credentials-details';
      case 1:
        return 'mqtt-client-credentials.authentication-authorization';
    }
  }

  get maxStepperIndex(): number {
    return this.addClientCredentialsWizardStepper?._steps?.length - 1;
  }

  add(): void {
    if (this.allValid()) {
      this.createClientCredentials().subscribe(
        (entity) => {
          const credentialsValue = JSON.parse(this.authenticationFormGroup.get('credentialsValue').value);
          const password = credentialsValue.password;
          if (this.clientCredentialsWizardFormGroup.get('credentialsType').value === CredentialsType.MQTT_BASIC) {
            return this.dialogRef.close({...entity, ...{password}})
          }
          return this.dialogRef.close(entity);
        }
      );
    }
  }

  private createClientCredentials(): Observable<ClientCredentials> {
    const clientCredentials: ClientCredentials = {
      name: this.clientCredentialsWizardFormGroup.get('name').value,
      clientType: this.clientCredentialsWizardFormGroup.get('clientType').value,
      credentialsType: this.clientCredentialsWizardFormGroup.get('credentialsType').value,
      credentialsValue: this.authenticationFormGroup.get('credentialsValue').value
    };
    return this.clientCredentialsService.saveClientCredentials(deepTrim(clientCredentials)).pipe(
      catchError(e => {
        this.addClientCredentialsWizardStepper.selectedIndex = 0;
        return throwError(e);
      })
    );
  }

  allValid(): boolean {
    return !this.addClientCredentialsWizardStepper.steps.find((item, index) => {
      if (item.stepControl.invalid) {
        item.interacted = true;
        this.addClientCredentialsWizardStepper.selectedIndex = index;
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

  onChangePasswordCloseDialog($event: ClientCredentials) {
    // this.updateForm($event);
  }

}
