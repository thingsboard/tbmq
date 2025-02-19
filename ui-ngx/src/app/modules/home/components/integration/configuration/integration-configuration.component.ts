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

import {
  Component,
  forwardRef,
  OnDestroy,
  TemplateRef,
  ViewEncapsulation,
  input,
  model
} from '@angular/core';
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
import { Integration, IntegrationType } from '@shared/models/integration.models';
import { Subject } from 'rxjs';
import { takeUntil } from 'rxjs/operators';
import {
  HttpIntegrationFormComponent
} from '@home/components/integration/configuration/http-integration-form/http-integration-form.component';

@Component({
  selector: 'tb-integration-configuration',
  templateUrl: './integration-configuration.component.html',
  styleUrls: ['./integration-configuration.component.scss'],
  encapsulation: ViewEncapsulation.None,
  imports: [
    ReactiveFormsModule,
    HttpIntegrationFormComponent
  ],
  providers: [{
    provide: NG_VALUE_ACCESSOR,
    useExisting: forwardRef(() => IntegrationConfigurationComponent),
    multi: true
  },
  {
    provide: NG_VALIDATORS,
    useExisting: forwardRef(() => IntegrationConfigurationComponent),
    multi: true,
  }]
})
export class IntegrationConfigurationComponent implements ControlValueAccessor, Validator, OnDestroy {

  integrationConfigurationForm: UntypedFormGroup;
  integrationTypes = IntegrationType;

  readonly genericAdditionalInfoTemplate = input<TemplateRef<any>>();
  readonly executeRemotelyTemplate = input<TemplateRef<any>>();
  readonly integrationType = input<IntegrationType>();
  readonly allowLocalNetwork = input(true);
  readonly integration = input<Integration>();
  readonly isEdit = input<boolean>();
  disabled = model<boolean>();

  private destroy$ = new Subject<void>();
  private propagateChange = (v: any) => { };

  constructor(private fb: UntypedFormBuilder) {
    this.integrationConfigurationForm = this.fb.group({
      configuration: [null, Validators.required]
    });
    this.integrationConfigurationForm.valueChanges.pipe(
      takeUntil(this.destroy$)
    ).subscribe(value => this.updateModel(value.configuration));
  }

  ngOnDestroy() {
    this.destroy$.next();
    this.destroy$.complete();
  }

  registerOnChange(fn: any) {
    this.propagateChange = fn;
  }

  registerOnTouched(fn: any) { }

  setDisabledState(isDisabled: boolean) {
    this.disabled.set(isDisabled);
    if (isDisabled) {
      this.integrationConfigurationForm.disable({emitEvent: false});
    } else {
      this.integrationConfigurationForm.enable({emitEvent: false});
    }
  }

  writeValue(value: any) {
    this.integrationConfigurationForm.get('configuration').reset(value, {emitEvent: false});
  }

  private updateModel(value: any) {
    this.propagateChange(value);
  }

  validate(): ValidationErrors | null {
    return this.integrationConfigurationForm.valid ? null : {
      integrationConfiguration: {valid: false}
    };
  }
}
