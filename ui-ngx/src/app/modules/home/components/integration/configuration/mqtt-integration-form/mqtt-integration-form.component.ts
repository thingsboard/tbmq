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

import { Component, forwardRef } from '@angular/core';
import {
  ControlValueAccessor,
  UntypedFormBuilder,
  UntypedFormGroup,
  NG_VALIDATORS,
  NG_VALUE_ACCESSOR,
  ValidationErrors,
  Validator,
  Validators, ReactiveFormsModule
} from '@angular/forms';
import { privateNetworkAddressValidator } from '@home/components/integration/integration.models';
import { takeUntil } from 'rxjs/operators';
import { isDefinedAndNotNull } from '@core/utils';
import { IntegrationForm } from '@home/components/integration/configuration/integration-form';
import { IntegrationCredentialType, MqttIntegration } from '@shared/models/integration.models';
import { MatError, MatFormField, MatLabel } from '@angular/material/form-field';
import { MatInput } from '@angular/material/input';
import { NgTemplateOutlet } from '@angular/common';
import {
  HttpTopicFiltersComponent
} from '@home/components/integration/http-topic-filters/http-topic-filters.component';
import {
  IntegrationCredentialsComponent
} from '@home/components/integration/integration-credentials/integration-credentials.component';
import { MatSlideToggle } from '@angular/material/slide-toggle';
import { TranslateModule } from '@ngx-translate/core';
import {
  MatExpansionPanel,
  MatExpansionPanelContent,
  MatExpansionPanelDescription,
  MatExpansionPanelHeader
} from '@angular/material/expansion';

@Component({
  selector: 'tb-mqtt-integration-form',
  templateUrl: './mqtt-integration-form.component.html',
  styleUrls: ['./mqtt-integration-form.component.scss'],
  imports: [
    ReactiveFormsModule,
    MatFormField,
    MatInput,
    HttpTopicFiltersComponent,
    IntegrationCredentialsComponent,
    MatSlideToggle,
    TranslateModule,
    MatExpansionPanel,
    MatExpansionPanelHeader,
    MatExpansionPanelDescription,
    MatExpansionPanelContent,
    MatLabel,
    MatError,
    NgTemplateOutlet
  ],
  providers: [{
    provide: NG_VALUE_ACCESSOR,
    useExisting: forwardRef(() => MqttIntegrationFormComponent),
    multi: true
  },
  {
    provide: NG_VALIDATORS,
    useExisting: forwardRef(() => MqttIntegrationFormComponent),
    multi: true,
  }]
})
export class MqttIntegrationFormComponent extends IntegrationForm implements ControlValueAccessor, Validator {

  mqttIntegrationConfigForm: UntypedFormGroup;

  IntegrationCredentialType = IntegrationCredentialType;

  private propagateChange = (v: any) => { };

  constructor(private fb: UntypedFormBuilder) {
    super();
    this.mqttIntegrationConfigForm = this.fb.group({
      clientConfiguration: this.fb.group({
        host: ['', Validators.required],
        port: [1883, [Validators.min(1), Validators.max(65535)]],
        cleanSession: [true],
        retainedMessage: [false],
        ssl: [false],
        connectTimeoutSec: [10, [Validators.required, Validators.min(1), Validators.max(200)]],
        clientId: [''],
        maxBytesInMessage: [32368, [Validators.min(1), Validators.max(256000000)]],
        credentials: [{
          type: IntegrationCredentialType.Anonymous
        }],
        topicFilters: [[{
          filter: '#',
          qos: 0
        }], Validators.required]
      })
    });
    this.mqttIntegrationConfigForm.valueChanges.pipe(
      takeUntil(this.destroy$)
    ).subscribe(() => this.updateModels(this.mqttIntegrationConfigForm.getRawValue()));
  }

  writeValue(value: MqttIntegration) {
    if (isDefinedAndNotNull(value)) {
      this.mqttIntegrationConfigForm.reset(value, {emitEvent: false});
    }
  }

  registerOnChange(fn: any): void {
    this.propagateChange = fn;
  }

  registerOnTouched(fn: any) { }

  setDisabledState(isDisabled: boolean) {
    this.disabled = isDisabled;
    if (isDisabled) {
      this.mqttIntegrationConfigForm.disable({emitEvent: false});
    } else {
      this.mqttIntegrationConfigForm.enable({emitEvent: false});
    }
  }

  private updateModels(value) {
    this.propagateChange(value);
  }

  validate(): ValidationErrors | null {
    return this.mqttIntegrationConfigForm.valid ? null : {
      mqttIntegrationConfigForm: {valid: false}
    };
  }

  updatedValidationPrivateNetwork() {
    if (this.allowLocalNetwork) {
      this.mqttIntegrationConfigForm.get('clientConfiguration.host').removeValidators(privateNetworkAddressValidator);
    } else {
      this.mqttIntegrationConfigForm.get('clientConfiguration.host').addValidators(privateNetworkAddressValidator);
    }
    this.mqttIntegrationConfigForm.get('clientConfiguration.host').updateValueAndValidity({emitEvent: false});
  }
}
