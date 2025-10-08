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

import { ChangeDetectorRef, Component, Inject } from '@angular/core';
import { FormsModule, ReactiveFormsModule, UntypedFormBuilder, UntypedFormGroup, Validators, AbstractControl, ValidationErrors } from '@angular/forms';
import { Store } from '@ngrx/store';
import { AppState } from '@core/core.state';
import { EntityComponent } from '@home/components/entity/entity.component';
import { EntityTableConfig } from '@home/models/entity/entities-table-config.models';
import { MatIcon } from '@angular/material/icon';
import { TranslateModule } from '@ngx-translate/core';
import { MatFormField, MatLabel, MatSuffix, MatError } from '@angular/material/form-field';
import { MatInput } from '@angular/material/input';
import { AsyncPipe } from '@angular/common';
import { MatTooltip } from '@angular/material/tooltip';
import {
  BlockedClient,
  BlockedClientType,
  blockedClientTypeTranslationMap,
  blockedClientTypeValuePropertyMap,
  RegexMatchTarget,
  regexMatchTargetTranslationMap
} from '@shared/models/blocked-client.models';
import { MatOption } from '@angular/material/core';
import { MatSelect } from '@angular/material/select';
import { MatDatetimepickerModule, MatNativeDatetimeModule } from '@mat-datetimepicker/core';
import { MatSlideToggle } from '@angular/material/slide-toggle';
import { MatTimepicker, MatTimepickerInput, MatTimepickerToggle } from '@angular/material/timepicker';
import { MatDatepicker, MatDatepickerInput, MatDatepickerToggle } from '@angular/material/datepicker';
import { isOnlyWhitespace } from '@core/utils';

@Component({
    selector: 'tb-blocked-client',
    templateUrl: './blocked-client.component.html',
    styleUrls: ['./blocked-client.component.scss'],
    imports: [MatIcon, TranslateModule, FormsModule, ReactiveFormsModule, MatFormField, MatLabel, MatInput, AsyncPipe, MatSuffix, MatTooltip, MatOption, MatSelect, MatDatetimepickerModule, MatNativeDatetimeModule, MatSlideToggle, MatTimepickerInput, MatTimepickerToggle, MatTimepicker, MatDatepicker, MatDatepickerToggle, MatDatepickerInput, MatError]
})
export class BlockedClientComponent extends EntityComponent<BlockedClient> {

  blockedClientTypes = Object.values(BlockedClientType);
  regexMatchTargets = Object.values(RegexMatchTarget);

  blockedClientTypeTranslationMap = blockedClientTypeTranslationMap;
  regexMatchTargetTranslationMap = regexMatchTargetTranslationMap;
  blockedClientTypeValuePropertyMap = blockedClientTypeValuePropertyMap;

  neverExpires = true;
  neverExpiresLabel: string;

  get isRegexType(): boolean {
    return this.entityForm.get('type').value === BlockedClientType.REGEX;
  }

  constructor(protected store: Store<AppState>,
              @Inject('entity') protected entityValue: BlockedClient,
              @Inject('entitiesTableConfig') protected entitiesTableConfigValue: EntityTableConfig<BlockedClient>,
              public fb: UntypedFormBuilder,
              protected cd: ChangeDetectorRef) {
    super(store, fb, entityValue, entitiesTableConfigValue, cd);
    this.onNeverExpiresChange();
  }

  buildForm(): UntypedFormGroup {
    const form = this.fb.group(
      {
        type: [BlockedClientType.CLIENT_ID, []],
        value: [null, [Validators.required]],
        regexMatchTarget: [RegexMatchTarget.BY_CLIENT_ID, []],
        description: [null],
        expirationTime: [this.defaultExpirationDate(), []],
        expirationTimeHourMinute: [this.defaultExpirationDate(), []],
      }
    );
    form.get('type').valueChanges.subscribe(() => this.updateValidators());
    form.get('expirationTimeHourMinute').valueChanges.subscribe(() => this.onTimeChange());
    return form;
  }

  prepareFormValue(formValue: BlockedClient): any {
    const valueProperty = this.blockedClientTypeValuePropertyMap.get(formValue.type);
    let value = formValue.value;
    if (!isOnlyWhitespace(value) && value !== value.trim()) {
      value = value.trim();
      this.entityForm.get('value').patchValue(value, {emitEvent: false});
    }
    formValue[valueProperty] = value;
    delete formValue.value;

    if (this.neverExpires) {
      formValue.expirationTime = 0;
    } else {
      formValue.expirationTime = (formValue.expirationTime as unknown as Date).getTime();
    }
    return formValue;
  }

  updateForm() {}

  updateFormState() {
    if (this.entityForm) {
      if (this.isEditValue) {
        this.entityForm.enable({emitEvent: false});
      } else {
        this.entityForm.disable({emitEvent: false});
      }
      this.updateValidators();
    }
  }

  onDateChange(event: MatDatepickerInput<Date>) {
    const date: Date | null = event?.value ?? null;
    if (!date) {
      return;
    }
    const control = this.entityForm.get('expirationTimeHourMinute');
    const current: Date | number | null = control.value;
    const base = current ? new Date(current) : new Date();
    const merged = new Date(
      date.getFullYear(),
      date.getMonth(),
      date.getDate(),
      base.getHours(),
      base.getMinutes(),
      base.getSeconds(),
      base.getMilliseconds()
    );
    control.setValue(merged, { emitEvent: true });
  }

  onTimeChange() {
    const dateCtrl = this.entityForm.get('expirationTime');
    const timeCtrl = this.entityForm.get('expirationTimeHourMinute');
    const baseDateRaw = dateCtrl?.value;
    const timeRaw = timeCtrl?.value;
    const baseDate = baseDateRaw ? new Date(baseDateRaw) : new Date();
    const extractHM = (val: any): { h: number; m: number } => {
      if (val instanceof Date && !isNaN(val.getTime())) {
        return { h: val.getHours(), m: val.getMinutes() };
      }
      if (typeof val === 'number') {
        const d = new Date(val);
        if (!isNaN(d.getTime())) {
          return { h: d.getHours(), m: d.getMinutes() };
        }
      }
      if (typeof val === 'string') {
        const match = val.trim().match(/^(\d{1,2}):(\d{2})(?::(\d{2}))?$/);
        if (match) {
          const h = Math.min(23, parseInt(match[1], 10));
          const m = Math.min(59, parseInt(match[2], 10));
          return { h, m };
        }
        const d = new Date(val);
        if (!isNaN(d.getTime())) {
          return { h: d.getHours(), m: d.getMinutes() };
        }
      }
      const now = new Date();
      return { h: now.getHours(), m: now.getMinutes() };
    };
    const { h, m } = extractHM(timeRaw);
    const merged = new Date(
      baseDate.getFullYear(),
      baseDate.getMonth(),
      baseDate.getDate(),
      h,
      m,
      baseDate.getSeconds(),
      baseDate.getMilliseconds()
    );
    dateCtrl?.setValue(merged, { emitEvent: true });
  }

  onNeverExpiresChange() {
    this.neverExpiresLabel = this.neverExpires ? 'blocked-client.expires-never' : 'blocked-client.expires';
    if (this.neverExpires) {
      this.entityForm.get('expirationTime').clearValidators();
    } else {
      this.entityForm.get('expirationTime').setValue(this.defaultExpirationDate(), {emitEvent: false});
      this.entityForm.get('expirationTime').setValidators([this.expirationTimeValidator]);
    }
    this.entityForm.get('expirationTime').updateValueAndValidity();
  }

  private updateValidators() {
    const type = this.entityForm.get('type').value;
    switch (type) {
      case BlockedClientType.REGEX:
        this.entityForm.get('regexMatchTarget').enable({onlySelf: true});
        break;
      default:
        this.entityForm.get('regexMatchTarget').disable({onlySelf: true});
        break;
    }
    this.entityForm.updateValueAndValidity();
  }

  private defaultExpirationDate(): Date {
    const date = new Date();
    date.setMonth(date.getMonth() + 1);
    return date;
  }

  private expirationTimeValidator(control: AbstractControl): ValidationErrors | null {
    const val = control.value;
    const time = val instanceof Date ? val.getTime() : new Date(val).getTime();
    if (time === 0 || isNaN(time)) {
      return { invalidFormat: true };
    }
    if (time < Date.now()) {
      return { expiresInPast: true };
    }
    return null;
  }
}
