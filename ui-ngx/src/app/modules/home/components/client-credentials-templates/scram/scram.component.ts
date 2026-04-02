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
  model,
} from '@angular/core';
import { ControlValueAccessor, FormBuilder, NG_VALIDATORS, NG_VALUE_ACCESSOR, UntypedFormGroup, ValidationErrors, Validator, Validators, FormsModule, ReactiveFormsModule } from '@angular/forms';
import { Subject } from 'rxjs';
import { takeUntil } from 'rxjs/operators';
import { isDefinedAndNotNull, isEmptyStr } from '@core/utils';
import {
  ANY_CHARACTERS,
  ScramCredentials,
  ClientCredentials,
  ShaType,
  shaTypeTranslationMap
} from '@shared/models/credentials.model';
import { CopyButtonComponent } from '@shared/components/button/copy-button.component';
import { clientUserNameRandom } from '@shared/models/ws-client.model';
import { TranslateModule } from '@ngx-translate/core';
import { MatFormField, MatLabel, MatSuffix, MatHint } from '@angular/material/form-field';
import { MatInput } from '@angular/material/input';
import { MatIconButton } from '@angular/material/button';
import { MatTooltip } from '@angular/material/tooltip';
import { MatIcon } from '@angular/material/icon';

import { TogglePasswordComponent } from '@shared/components/button/toggle-password.component';
import { MatSelect } from '@angular/material/select';
import { MatOption } from '@angular/material/core';
import { MatExpansionPanel, MatExpansionPanelHeader, MatExpansionPanelTitle } from '@angular/material/expansion';
import { TopicRulesChipListComponent } from '@shared/components/topic-rules-chip-list.component';

@Component({
    selector: 'tb-mqtt-credentials-scram',
    templateUrl: './scram.component.html',
    providers: [
        {
            provide: NG_VALUE_ACCESSOR,
            useExisting: forwardRef(() => MqttCredentialsScramComponent),
            multi: true
        },
        {
            provide: NG_VALIDATORS,
            useExisting: forwardRef(() => MqttCredentialsScramComponent),
            multi: true,
        }
    ],
    styleUrls: ['./scram.component.scss'],
    imports: [FormsModule, ReactiveFormsModule, TranslateModule, MatFormField, MatLabel, MatInput, CopyButtonComponent, MatSuffix, MatIconButton, MatTooltip, MatIcon, TogglePasswordComponent, MatSelect, MatOption, MatHint, MatExpansionPanel, MatExpansionPanelHeader, MatExpansionPanelTitle, TopicRulesChipListComponent]
})
export class MqttCredentialsScramComponent implements ControlValueAccessor, Validator, OnDestroy, AfterViewInit {

  disabled = model<boolean>();
  readonly entity = input<ClientCredentials>();

  credentialsMqttFormGroup: UntypedFormGroup;
  shaTypes = Object.values(ShaType);
  shaTypeTranslations = shaTypeTranslationMap;

  private maskedPassword = '********';
  private newPassword: string;
  private destroy$ = new Subject<void>();
  private propagateChange = (v: any) => {};

  constructor(public fb: FormBuilder,
              private cd: ChangeDetectorRef) {
    this.credentialsMqttFormGroup = this.fb.group({
      userName: [null, [Validators.required]],
      password: [null, [Validators.required]],
      algorithm: [ShaType.SHA_256, [Validators.required]],
      authRules: this.fb.group({
        pubAuthRulePatterns: [[ANY_CHARACTERS], []],
        subAuthRulePatterns: [[ANY_CHARACTERS], []]
      }),
      salt: [null, []],
      serverKey: [null, []],
      storedKey: [null, []]
    });
    this.credentialsMqttFormGroup.valueChanges.pipe(
      takeUntil(this.destroy$)
    ).subscribe((value) => {
      this.updateView(value);
    });
    this.credentialsMqttFormGroup.get('password').valueChanges.subscribe(value => {
      if (value !== this.maskedPassword) {
        this.newPassword = value;
      }
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
      credentialsMqttScram: false
    };
  }

  writeValue(value: string) {
    if (isDefinedAndNotNull(value) && !isEmptyStr(value)) {
      const valueJson = JSON.parse(value);
      valueJson.password = this.maskedPassword;
      this.credentialsMqttFormGroup.patchValue(valueJson, {emitEvent: false});
      this.newPassword = null;
    }
  }

  updateView(value: ScramCredentials) {
    if (this.newPassword) {
      value.password = this.newPassword;
    } else {
      delete value.password;
    }
    const formValue = JSON.stringify(value);
    this.propagateChange(formValue);
  }

  regenerate(type: string) {
    switch (type) {
      case 'userName':
        this.credentialsMqttFormGroup.patchValue({
          userName: clientUserNameRandom()
        });
        break;
    }
  }

  algorithmChanged() {
    if (this.entity()?.id) {
      this.newPassword = null;
      this.credentialsMqttFormGroup.get('password').patchValue(null);
    }
  }

}
