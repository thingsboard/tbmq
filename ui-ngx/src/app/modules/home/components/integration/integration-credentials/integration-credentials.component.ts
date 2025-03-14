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
  ControlValueAccessor,
  UntypedFormBuilder,
  UntypedFormGroup,
  NG_VALIDATORS,
  NG_VALUE_ACCESSOR,
  ValidationErrors,
  Validator,
  Validators, ReactiveFormsModule
} from '@angular/forms';
import { Component, forwardRef, Input, OnDestroy, OnInit, input } from '@angular/core';
import { Subject } from 'rxjs';
import { takeUntil } from 'rxjs/operators';
import { IntegrationCredentialType, IntegrationCredentialTypeTranslation } from '@shared/models/integration.models';
import { coerceBooleanProperty } from '@angular/cdk/coercion';
import { MatError, MatFormField, MatLabel, MatSuffix } from '@angular/material/form-field';
import { MatOption, MatSelect } from '@angular/material/select';
import { NgTemplateOutlet } from '@angular/common';
import { TranslateModule } from '@ngx-translate/core';
import { MatInput } from '@angular/material/input';
import { TogglePasswordComponent } from '@shared/components/button/toggle-password.component';
import { CertUploadComponent } from '@home/components/integration/cert-upload/cert-upload.component';
import { CopyButtonComponent } from '@shared/components/button/copy-button.component';
import { MatIcon } from '@angular/material/icon';
import { MatTooltip } from '@angular/material/tooltip';

@Component({
  selector: 'tb-integration-credentials',
  templateUrl: 'integration-credentials.component.html',
  styleUrls: ['integration-credentials.component.scss'],
  imports: [
    ReactiveFormsModule,
    MatFormField,
    MatSelect,
    MatOption,
    TranslateModule,
    MatInput,
    TogglePasswordComponent,
    CertUploadComponent,
    MatError,
    MatLabel,
    MatSuffix,
    NgTemplateOutlet,
    CopyButtonComponent,
    MatIcon,
    MatTooltip
  ],
  providers: [{
    provide: NG_VALUE_ACCESSOR,
    useExisting: forwardRef(() => IntegrationCredentialsComponent),
    multi: true
  },
  {
    provide: NG_VALIDATORS,
    useExisting: forwardRef(() => IntegrationCredentialsComponent),
    multi: true,
  }]
})
export class IntegrationCredentialsComponent implements ControlValueAccessor, Validator, OnInit, OnDestroy {

  integrationCredentialForm: UntypedFormGroup;
  hideSelectType = false;

  private allowCredentialTypesValue: IntegrationCredentialType[] = [];
  @Input()
  set allowCredentialTypes(types: IntegrationCredentialType[]) {
    this.allowCredentialTypesValue = types;
    this.hideSelectType = types.length === 1;
  }

  get allowCredentialTypes(): IntegrationCredentialType[] {
    return this.allowCredentialTypesValue;
  }

  private ignoreCaCertValue = true;
  @Input()
  set ignoreCaCert(value: boolean) {
    this.ignoreCaCertValue = coerceBooleanProperty(value);
    if (this.integrationCredentialForm) {
      if (this.ignoreCaCertValue) {
        this.integrationCredentialForm.get('caCertFileName').clearValidators();
        this.integrationCredentialForm.get('caCert').clearValidators();
      } else {
        this.integrationCredentialForm.get('caCertFileName').setValidators(Validators.required);
        this.integrationCredentialForm.get('caCert').setValidators(Validators.required);
      }
      this.integrationCredentialForm.get('caCertFileName').updateValueAndValidity({emitEvent: false});
      this.integrationCredentialForm.get('caCert').updateValueAndValidity({emitEvent: false});
    }
  }

  get ignoreCaCert(): boolean {
    return this.ignoreCaCertValue;
  }

  readonly userNameLabel = input('integration.username');
  readonly userNameRequired = input('integration.username-required');
  readonly passwordLabel = input('integration.password');
  readonly passwordRequired = input('integration.password-required');
  private passwordOptionalValue = false;
  private userNameOptionalValue = false;

  get passwordOptional(): boolean {
    return this.passwordOptionalValue;
  }
  @Input()
  set passwordOptional(value: boolean) {
    this.passwordOptionalValue = coerceBooleanProperty(value);
  }

  get userNameOptional(): boolean {
    return this.userNameOptionalValue;
  }
  @Input()
  set userNameOptional(value: boolean) {
    this.userNameOptionalValue = coerceBooleanProperty(value);
  }

  IntegrationCredentialTypeTranslation = IntegrationCredentialTypeTranslation;
  IntegrationCredentialType = IntegrationCredentialType;

  @Input()
  disabled: boolean;

  private destroy$ = new Subject<void>();
  private propagateChange = (v: any) => { };

  constructor(private fb: UntypedFormBuilder) {
  }

  ngOnInit() {
    this.integrationCredentialForm = this.fb.group({
      type: ['', Validators.required],
      username: [{value: '', disabled: true}, this.userNameOptional ? null : Validators.required],
      password: [{value: '', disabled: true}, this.passwordOptional ? null : Validators.required],
      caCertFileName: [{value: '', disabled: true}, this.ignoreCaCert ? null : Validators.required],
      caCert: [{value: '', disabled: true},  this.ignoreCaCert ? null : Validators.required],
      certFileName: [{value: '', disabled: true}, []],
      cert: [{value: '', disabled: true}, []],
      privateKeyFileName: [{value: '', disabled: true}, []],
      privateKey: [{value: '', disabled: true}, []],
      privateKeyPassword: [{value: '', disabled: true}],
      token: [{value: '', disabled: true}, Validators.required],
      sasKey: [{value: '', disabled: true}, Validators.required],
    });
    this.integrationCredentialForm.get('type').valueChanges.pipe(
      takeUntil(this.destroy$)
    ).subscribe(type => this.updatedValidation(type));
    this.integrationCredentialForm.valueChanges.pipe(
      takeUntil(this.destroy$)
    ).subscribe(value => this.updateModel(value));
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
    this.disabled = isDisabled;
    if (this.disabled) {
      this.integrationCredentialForm.disable({emitEvent: false});
    } else {
      this.integrationCredentialForm.enable({emitEvent: false});
      this.integrationCredentialForm.get('type').updateValueAndValidity({onlySelf: true});
    }
  }

  writeValue(value) {
    this.integrationCredentialForm.patchValue(value, {emitEvent: false});
    if (!this.disabled) {
      this.integrationCredentialForm.get('type').updateValueAndValidity({onlySelf: true});
    }
  }

  private updatedValidation(type: IntegrationCredentialType) {
    this.integrationCredentialForm.disable({emitEvent: false});
    switch (type) {
      case IntegrationCredentialType.Anonymous:
        break;
      case IntegrationCredentialType.Basic:
        this.integrationCredentialForm.get('username').enable({emitEvent: false});
        this.integrationCredentialForm.get('password').enable({emitEvent: false});
        break;
      case IntegrationCredentialType.CertPEM:
        this.integrationCredentialForm.get('caCertFileName').enable({emitEvent: false});
        this.integrationCredentialForm.get('caCert').enable({emitEvent: false});
        this.integrationCredentialForm.get('certFileName').enable({emitEvent: false});
        this.integrationCredentialForm.get('cert').enable({emitEvent: false});
        this.integrationCredentialForm.get('privateKeyFileName').enable({emitEvent: false});
        this.integrationCredentialForm.get('privateKey').enable({emitEvent: false});
        this.integrationCredentialForm.get('privateKeyPassword').enable({emitEvent: false});
        break;
    }
    this.integrationCredentialForm.get('type').enable({emitEvent: false});
  }

  private updateModel(value) {
    this.propagateChange(value);
  }

  validate(): ValidationErrors | null {
    return this.integrationCredentialForm.valid ? null : {
      integrationCredential: {valid: false}
    };
  }
}
