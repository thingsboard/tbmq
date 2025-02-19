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
import { isDefinedAndNotNull } from '@core/utils';
import { takeUntil } from 'rxjs/operators';
import { IntegrationForm } from '@home/components/integration/configuration/integration-form';
import { KafkaIntegration } from '@shared/models/integration.models';
import { privateNetworkAddressValidator } from '@home/components/integration/integration.models';
import { MatFormField } from '@angular/material/form-field';
import { MatInput } from '@angular/material/input';
import { NgTemplateOutlet } from '@angular/common';
import { MatSlideToggle } from '@angular/material/slide-toggle';
import { TranslateModule } from '@ngx-translate/core';
import { MatExpansionPanel, MatExpansionPanelContent, MatExpansionPanelDescription } from '@angular/material/expansion';
import { HeaderFilterMapComponent } from '@shared/components/header-filter-map.component';

@Component({
  selector: 'tb-kafka-integration-form',
  templateUrl: './kafka-integration-form.component.html',
  styleUrls: [],
  imports: [
    ReactiveFormsModule,
    MatFormField,
    MatInput,
    MatSlideToggle,
    TranslateModule,
    MatExpansionPanel,
    MatExpansionPanelDescription,
    MatExpansionPanelContent,
    HeaderFilterMapComponent,
    NgTemplateOutlet
  ],
  providers: [{
    provide: NG_VALUE_ACCESSOR,
    useExisting: forwardRef(() => KafkaIntegrationFormComponent),
    multi: true
  },
  {
    provide: NG_VALIDATORS,
    useExisting: forwardRef(() => KafkaIntegrationFormComponent),
    multi: true,
  }]
})
export class KafkaIntegrationFormComponent extends IntegrationForm implements ControlValueAccessor, Validator {

  kafkaIntegrationConfigForm: UntypedFormGroup;

  private propagateChange = (v: any) => { };

  constructor(private fb: UntypedFormBuilder) {
    super();
    this.kafkaIntegrationConfigForm = this.fb.group({
      groupId: ['', [Validators.required]],
      clientId: ['', [Validators.required]],
      topics: ['my-topic-output', [Validators.required]],
      bootstrapServers: ['localhost:9092', [Validators.required]],
      pollInterval: [5000, [Validators.required]],
      autoCreateTopics: [false],
      otherProperties: [null]
    });
    this.kafkaIntegrationConfigForm.valueChanges.pipe(
      takeUntil(this.destroy$)
    ).subscribe(() => {
      this.updateModels(this.kafkaIntegrationConfigForm.getRawValue());
    });
  }

  writeValue(value: KafkaIntegration) {
    if (isDefinedAndNotNull(value?.clientConfiguration)) {
      this.kafkaIntegrationConfigForm.reset(value.clientConfiguration, {emitEvent: false});
    }
  }

  registerOnChange(fn: any): void {
    this.propagateChange = fn;
  }

  registerOnTouched(fn: any) { }

  setDisabledState(isDisabled: boolean) {
    this.disabled = isDisabled;
    if (isDisabled) {
      this.kafkaIntegrationConfigForm.disable({emitEvent: false});
    } else {
      this.kafkaIntegrationConfigForm.enable({emitEvent: false});
    }
  }

  private updateModels(value) {
    this.propagateChange({clientConfiguration: value});
  }

  validate(): ValidationErrors | null {
    return this.kafkaIntegrationConfigForm.valid ? null : {
      kafkaIntegrationConfigForm: {valid: false}
    };
  }

  updatedValidationPrivateNetwork() {
    if (this.allowLocalNetwork) {
      this.kafkaIntegrationConfigForm.get('bootstrapServers').removeValidators(privateNetworkAddressValidator);
    } else {
      this.kafkaIntegrationConfigForm.get('bootstrapServers').addValidators(privateNetworkAddressValidator);
    }
    this.kafkaIntegrationConfigForm.get('bootstrapServers').updateValueAndValidity({emitEvent: false});
  }
}
