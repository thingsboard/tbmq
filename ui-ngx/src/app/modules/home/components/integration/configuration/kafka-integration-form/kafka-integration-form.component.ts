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
import { MatError, MatFormField, MatLabel } from '@angular/material/form-field';
import { MatInput } from '@angular/material/input';
import { NgTemplateOutlet } from '@angular/common';
import { TranslateModule } from '@ngx-translate/core';
import {
  MatExpansionPanel,
  MatExpansionPanelContent,
  MatExpansionPanelDescription,
  MatExpansionPanelHeader
} from '@angular/material/expansion';
import { MatOption, MatSelect } from '@angular/material/select';
import { KeyValMapComponent } from '@shared/components/key-val-map.component';

@Component({
  selector: 'tb-kafka-integration-form',
  templateUrl: './kafka-integration-form.component.html',
  styleUrls: ['./kafka-integration-form.component.scss'],
  imports: [
    ReactiveFormsModule,
    MatFormField,
    MatInput,
    MatLabel,
    TranslateModule,
    MatExpansionPanel,
    MatExpansionPanelHeader,
    MatExpansionPanelDescription,
    MatExpansionPanelContent,
    NgTemplateOutlet,
    MatSelect,
    MatOption,
    MatError,
    KeyValMapComponent,
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
  ackValues: string[] = ['all', '-1', '0', '1'];
  compressionValues: string[] = ['none', 'gzip', 'snappy', 'lz4', 'zstd'];

  private propagateChange = (v: any) => { };

  constructor(private fb: UntypedFormBuilder) {
    super();
    this.kafkaIntegrationConfigForm = this.fb.group({
      topic: ['tbmq', [Validators.required]],
      key: [null, []],
      bootstrapServers: ['localhost:9092', [Validators.required]],
      clientIdPrefix: [null, []],
      retries: [0, [Validators.min(0)]],
      batchSize: [16384, [Validators.min(0)]],
      linger: [0, [Validators.min(0)]],
      bufferMemory: [33554432, [Validators.min(0)]],
      compression: ['none', [Validators.required]],
      acks: ['-1', [Validators.required]],
      keySerializer: ['org.apache.kafka.common.serialization.StringSerializer', [Validators.required]],
      valueSerializer: ['org.apache.kafka.common.serialization.StringSerializer', [Validators.required]],
      otherProperties: [null, []],
      kafkaHeaders: [null, []],
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
