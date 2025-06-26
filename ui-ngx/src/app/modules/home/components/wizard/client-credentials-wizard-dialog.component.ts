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

import { Component, Inject, viewChild } from '@angular/core';
import { MAT_DIALOG_DATA, MatDialogRef, MatDialogContent, MatDialogActions } from '@angular/material/dialog';
import { Store } from '@ngrx/store';
import { AppState } from '@core/core.state';
import { FormBuilder, UntypedFormGroup, Validators, FormsModule, ReactiveFormsModule } from '@angular/forms';
import { DialogComponent } from '@shared/components/dialog.component';
import { Router } from '@angular/router';
import { MatStepper, StepperOrientation, MatStepperIcon, MatStep, MatStepLabel } from '@angular/material/stepper';
import { Observable, throwError } from 'rxjs';
import { catchError, map } from 'rxjs/operators';
import { StepperSelectionEvent } from '@angular/cdk/stepper';
import { BreakpointObserver } from '@angular/cdk/layout';
import { MediaBreakpoints } from '@shared/models/constants';
import { deepTrim, isDefinedAndNotNull } from '@core/utils';
import { ClientCredentials, CredentialsType, CredentialsTypes, credentialsTypeTranslationMap } from '@shared/models/credentials.model';
import { ClientCredentialsService } from '@core/http/client-credentials.service';
import { ClientType, clientTypeTranslationMap } from '@shared/models/client.model';
import { AddEntityDialogData } from '@home/models/entity/entity-component.models';
import { BaseData } from '@shared/models/base-data';
import { MatToolbar } from '@angular/material/toolbar';
import { TranslateModule } from '@ngx-translate/core';
import { HelpComponent } from '@shared/components/help.component';
import { MatIconButton, MatButton } from '@angular/material/button';
import { MatIcon } from '@angular/material/icon';
import { AsyncPipe } from '@angular/common';
import { MatProgressBar } from '@angular/material/progress-bar';
import { MatFormField, MatLabel, MatSuffix } from '@angular/material/form-field';
import { MatInput } from '@angular/material/input';
import { MatSelect } from '@angular/material/select';
import { MatOption } from '@angular/material/core';
import { MqttCredentialsBasicComponent } from '../client-credentials-templates/basic/basic.component';
import { MqttCredentialsSslComponent } from '../client-credentials-templates/ssl/ssl.component';
import { MqttCredentialsScramComponent } from '../client-credentials-templates/scram/scram.component';
import { MatDivider } from '@angular/material/divider';
import { MatTooltip } from '@angular/material/tooltip';

@Component({
    selector: 'tb-client-credentials-wizard',
    templateUrl: './client-credentials-wizard-dialog.component.html',
    styleUrls: ['./client-credentials-wizard-dialog.component.scss'],
    imports: [MatToolbar, TranslateModule, HelpComponent, MatIconButton, MatIcon, MatProgressBar, MatDialogContent, MatStepper, MatStepperIcon, MatStep, FormsModule, ReactiveFormsModule, MatStepLabel, MatFormField, MatLabel, MatInput, MatSelect, MatOption, MqttCredentialsBasicComponent, MqttCredentialsSslComponent, MqttCredentialsScramComponent, MatDialogActions, MatButton, MatDivider, AsyncPipe, MatTooltip, MatSuffix]
})
export class ClientCredentialsWizardDialogComponent extends DialogComponent<ClientCredentialsWizardDialogComponent, ClientCredentials> {

  readonly addClientCredentialsWizardStepper = viewChild<MatStepper>('addClientCredentialsWizardStepper');

  stepperOrientation: Observable<StepperOrientation>;
  stepperLabelPosition: Observable<'bottom' | 'end'>;
  selectedIndex = 0;
  showNext = true;
  CredentialsType = CredentialsType;
  ClientType = ClientType;
  CredentialsTypes = CredentialsTypes;
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
        additionalInfo: this.fb.group(
          {
            description: [null, []]
          }
        )
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
    this.addClientCredentialsWizardStepper().previous();
  }

  nextStep(): void {
    this.addClientCredentialsWizardStepper().next();
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
    return this.addClientCredentialsWizardStepper()?._steps?.length - 1;
  }

  add(): void {
    if (this.allValid()) {
      this.createClientCredentials().subscribe(
        (entity) => {
          const credentialsValue = JSON.parse(this.authenticationFormGroup.get('credentialsValue').value);
          const password = credentialsValue.password;
          if (this.clientCredentialsWizardFormGroup.get('credentialsType').value === CredentialsType.MQTT_BASIC) {
            entity.credentialsValue = JSON.stringify(credentialsValue);
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
      additionalInfo: this.clientCredentialsWizardFormGroup.get('additionalInfo').value,
      credentialsValue: this.authenticationFormGroup.get('credentialsValue').value
    };
    return this.clientCredentialsService.saveClientCredentials(deepTrim(clientCredentials)).pipe(
      catchError(e => {
        this.addClientCredentialsWizardStepper().selectedIndex = 0;
        return throwError(e);
      })
    );
  }

  allValid(): boolean {
    return !this.addClientCredentialsWizardStepper().steps.find((item, index) => {
      if (item.stepControl.invalid) {
        item.interacted = true;
        this.addClientCredentialsWizardStepper().selectedIndex = index;
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
}
