///
/// Copyright © 2016-2026 The Thingsboard Authors
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
  AfterViewInit,
  ChangeDetectorRef,
  Component,
  forwardRef,
  OnDestroy,
  input,
  model
} from '@angular/core';
import { ControlValueAccessor, FormBuilder, NG_VALIDATORS, NG_VALUE_ACCESSOR, UntypedFormGroup, ValidationErrors, Validator, ValidatorFn, Validators, FormsModule, ReactiveFormsModule } from '@angular/forms';
import { Subject } from 'rxjs';
import { takeUntil } from 'rxjs/operators';
import { isDefinedAndNotNull, isEmptyStr } from '@core/utils';
import { ANY_CHARACTERS, BasicCredentials, ClientCredentials } from '@shared/models/credentials.model';
import { CopyButtonComponent } from '@shared/components/button/copy-button.component';
import { clientIdRandom, clientUserNameRandom } from '@shared/models/ws-client.model';
import { TranslateModule } from '@ngx-translate/core';
import { MatFormField, MatLabel, MatSuffix } from '@angular/material/form-field';
import { MatInput } from '@angular/material/input';
import { MatIconButton } from '@angular/material/button';
import { MatTooltip } from '@angular/material/tooltip';
import { MatIcon } from '@angular/material/icon';

import { TogglePasswordComponent } from '@shared/components/button/toggle-password.component';
import { MatExpansionPanel, MatExpansionPanelHeader, MatExpansionPanelTitle } from '@angular/material/expansion';
import { TopicRulesChipListComponent } from '@shared/components/topic-rules-chip-list.component';

@Component({
    selector: 'tb-mqtt-credentials-basic',
    templateUrl: './basic.component.html',
    providers: [
        {
            provide: NG_VALUE_ACCESSOR,
            useExisting: forwardRef(() => MqttCredentialsBasicComponent),
            multi: true
        },
        {
            provide: NG_VALIDATORS,
            useExisting: forwardRef(() => MqttCredentialsBasicComponent),
            multi: true,
        }
    ],
    styleUrls: ['./basic.component.scss'],
    imports: [FormsModule, ReactiveFormsModule, TranslateModule, MatFormField, MatLabel, MatInput, CopyButtonComponent, MatSuffix, MatIconButton, MatTooltip, MatIcon, TogglePasswordComponent, MatExpansionPanel, MatExpansionPanelHeader, MatExpansionPanelTitle, TopicRulesChipListComponent]
})
export class MqttCredentialsBasicComponent implements ControlValueAccessor, Validator, OnDestroy, AfterViewInit {

  disabled = model<boolean>();
  readonly entity = input<ClientCredentials>();

  credentialsMqttFormGroup: UntypedFormGroup;

  private destroy$ = new Subject<void>();
  private propagateChange = (v: any) => {};

  constructor(public fb: FormBuilder,
              private cd: ChangeDetectorRef) {
    this.credentialsMqttFormGroup = this.fb.group({
      clientId: [null],
      userName: [null],
      password: [null],
      authRules: this.fb.group({
        pubAuthRulePatterns: [[ANY_CHARACTERS], []],
        subAuthRulePatterns: [[ANY_CHARACTERS], []]
      })
    }, {validators: this.atLeastOne(Validators.required, ['clientId', 'userName'])});
    this.credentialsMqttFormGroup.valueChanges.pipe(
      takeUntil(this.destroy$)
    ).subscribe((value) => {
      this.updateView(value);
    });
  }

  ngAfterViewInit() {
    this.cd.detectChanges();
  }

  ngOnDestroy(): void {
    this.destroy$.next();
    this.destroy$.complete();
  }

  registerOnChange(fn: any): void {
    this.propagateChange = fn;
  }

  registerOnTouched(fn: any): void {}

  setDisabledState(isDisabled: boolean) {
    this.disabled.set(isDisabled);
    if (this.disabled()) {
      this.credentialsMqttFormGroup.disable({emitEvent: false});
    } else {
      this.credentialsMqttFormGroup.enable({emitEvent: false});
    }
  }

  validate(): ValidationErrors | null {
    return this.credentialsMqttFormGroup.valid ? null : {
      deviceCredentialsMqttBasic: false
    };
  }

  writeValue(value: string) {
    if (isDefinedAndNotNull(value) && !isEmptyStr(value)) {
      const valueJson = JSON.parse(value);
      this.credentialsMqttFormGroup.patchValue(valueJson, {emitEvent: false});
    }
  }

  updateView(value: BasicCredentials) {
    const formValue = JSON.stringify(value);
    this.propagateChange(formValue);
  }

  regenerate(type: string) {
    switch (type) {
      case 'clientId':
        this.credentialsMqttFormGroup.patchValue({
          clientId: clientIdRandom()
        });
        break;
      case 'userName':
        this.credentialsMqttFormGroup.patchValue({
          userName: clientUserNameRandom()
        });
        break;
    }
  }

  private atLeastOne(validator: ValidatorFn, controls: string[] = null) {
    return (group: UntypedFormGroup): ValidationErrors | null => {
      if (!controls) {
        controls = Object.keys(group.controls);
      }
      const hasAtLeastOne = group?.controls && controls.some(k => !validator(group.controls[k]));
      return hasAtLeastOne ? null : {atLeastOne: true};
    };
  }
}
