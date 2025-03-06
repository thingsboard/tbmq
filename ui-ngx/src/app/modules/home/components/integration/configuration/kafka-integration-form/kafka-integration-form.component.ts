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

import { Component, forwardRef, input, OnInit } from '@angular/core';
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
import {
  Integration,
  KafkaIntegration,
  ToByteStandartCharsetTypes,
  ToByteStandartCharsetTypeTranslations
} from '@shared/models/integration.models';
import { MatFormField, MatLabel, MatSuffix } from '@angular/material/form-field';
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
import { MatIcon } from '@angular/material/icon';
import { MatTooltip } from '@angular/material/tooltip';
import {
  HttpTopicFiltersComponent
} from '@home/components/integration/http-topic-filters/http-topic-filters.component';

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
    KeyValMapComponent,
    MatSuffix,
    MatIcon,
    MatTooltip,
    HttpTopicFiltersComponent
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
export class KafkaIntegrationFormComponent extends IntegrationForm implements ControlValueAccessor, Validator, OnInit {

  integration = input<Integration>();
  isEdit = input<boolean>();

  kafkaIntegrationConfigForm: UntypedFormGroup;
  ackValues: string[] = ['all', '-1', '0', '1'];
  compressionValues: string[] = ['none', 'gzip', 'snappy', 'lz4', 'zstd'];
  ToByteStandartCharsetTypesValues = ToByteStandartCharsetTypes;
  ToByteStandartCharsetTypeTranslationMap = ToByteStandartCharsetTypeTranslations;
  isNew: boolean;

  private propagateChangePending = false;
  private propagateChange = (v: any) => { };

  get clientConfigurationFormGroup() {
    return this.kafkaIntegrationConfigForm.get('clientConfiguration') as UntypedFormGroup;
  }

  constructor(private fb: UntypedFormBuilder) {
    super();
  }

  ngOnInit() {
    this.kafkaIntegrationConfigForm = this.fb.group({
      topicFilters: [['tbmq/#'], Validators.required],
      clientConfiguration: this.fb.group({
        topic: ['tbmq.messages', [Validators.required]],
        key: [null, []],
        bootstrapServers: ['localhost:9092', [Validators.required]],
        clientIdPrefix: [null, []],
        retries: [0, [Validators.min(0)]],
        batchSize: [16384, [Validators.min(0)]],
        linger: [0, [Validators.min(0)]],
        bufferMemory: [33554432, [Validators.min(0)]],
        compression: ['none', [Validators.required]],
        acks: ['-1', [Validators.required]],
        // keySerializer: ['org.apache.kafka.common.serialization.StringSerializer', [Validators.required]],
        // valueSerializer: ['org.apache.kafka.common.serialization.StringSerializer', [Validators.required]],
        otherProperties: [null, []],
        kafkaHeaders: [null, []],
        kafkaHeadersCharset: ['UTF-8', []],
      })
    });
    this.kafkaIntegrationConfigForm.valueChanges
      .pipe(takeUntil(this.destroy$))
      .subscribe(() => this.updateModels(this.kafkaIntegrationConfigForm.getRawValue()));
  }

  writeValue(value: KafkaIntegration) {
    if (isDefinedAndNotNull(value?.clientConfiguration)) {
      this.isNew = false;
      this.kafkaIntegrationConfigForm.reset(value, {emitEvent: false});
    } else {
      this.isNew = true;
      this.propagateChangePending = true;
    }
  }

  registerOnChange(fn: any): void {
    this.propagateChange = fn;
    if (this.propagateChangePending) {
      this.propagateChangePending = false;
      setTimeout(() => {
        this.updateModels(this.kafkaIntegrationConfigForm.getRawValue());
      }, 0);
    }
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
    if (this.isNew) {
      delete value.topicFilters;
    }
    this.propagateChange(value);
  }

  validate(): ValidationErrors | null {
    return this.kafkaIntegrationConfigForm.valid ? null : {
      kafkaIntegrationConfigForm: {valid: false}
    };
  }
}
