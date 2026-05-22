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

import { Component, forwardRef, model } from '@angular/core';
import {
  ControlValueAccessor,
  FormBuilder,
  NG_VALUE_ACCESSOR,
  ReactiveFormsModule
} from '@angular/forms';
import { DAY, FixedWindow, MINUTE } from '@shared/models/time/time.models';
import { MatFormField, MatLabel, MatSuffix } from '@angular/material/form-field';
import { TranslateModule } from '@ngx-translate/core';
import { MatInput } from '@angular/material/input';
import { DatetimeAdapter, MatDatetimepickerModule, MatNativeDatetimeModule } from '@mat-datetimepicker/core';
import { CustomDateAdapter } from '@shared/adapter/custom-datetime-adapter';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import { distinctUntilChanged } from 'rxjs/operators';

interface DateTimePeriod {
  startDate: Date;
  endDate: Date;
}

@Component({
    selector: 'tb-datetime-period',
    templateUrl: './datetime-period.component.html',
    styleUrls: ['./datetime-period.component.scss'],
    providers: [
        {
            provide: NG_VALUE_ACCESSOR,
            useExisting: forwardRef(() => DatetimePeriodComponent),
            multi: true
        },
        { provide: DatetimeAdapter, useClass: CustomDateAdapter }
    ],
    imports: [
      MatFormField, MatLabel, MatSuffix, MatInput, TranslateModule, ReactiveFormsModule,
      MatDatetimepickerModule, MatNativeDatetimeModule
    ]
})
export class DatetimePeriodComponent implements ControlValueAccessor {

  disabled = model<boolean>();

  private modelValue: FixedWindow;

  maxStartDate: Date;
  maxEndDate: Date;

  private maxStartDateTs: number;
  private minEndDateTs: number;
  private maxStartTs: number;
  private maxEndTs: number;

  private readonly timeShiftMs = MINUTE;

  dateTimePeriodFormGroup = this.fb.group({
    startDate: this.fb.control<Date>(null),
    endDate: this.fb.control<Date>(null)
  });

  private changePending = false;
  private propagateChange: (value: FixedWindow) => void = null;

  constructor(private fb: FormBuilder) {
    this.dateTimePeriodFormGroup.valueChanges.pipe(
      distinctUntilChanged((prev, curr) =>
        prev.startDate?.getTime() === curr.startDate?.getTime() &&
        prev.endDate?.getTime() === curr.endDate?.getTime()),
      takeUntilDestroyed()
    ).subscribe((dateTimePeriod) => {
      this.updateMinMaxDates(dateTimePeriod as DateTimePeriod);
      this.updateView();
    });

    this.dateTimePeriodFormGroup.get('startDate').valueChanges.pipe(
      distinctUntilChanged((a, b) => a?.getTime() === b?.getTime()),
      takeUntilDestroyed()
    ).subscribe(startDate => this.onStartDateChange(startDate));

    this.dateTimePeriodFormGroup.get('endDate').valueChanges.pipe(
      distinctUntilChanged((a, b) => a?.getTime() === b?.getTime()),
      takeUntilDestroyed()
    ).subscribe(endDate => this.onEndDateChange(endDate));
  }

  registerOnChange(fn: any): void {
    this.propagateChange = fn;
    if (this.changePending && this.propagateChange) {
      this.changePending = false;
      this.propagateChange(this.modelValue);
    }
  }

  registerOnTouched(_fn: any): void {
  }

  setDisabledState(isDisabled: boolean): void {
    this.disabled.set(isDisabled);
    if (isDisabled) {
      this.dateTimePeriodFormGroup.disable({emitEvent: false});
    } else {
      this.dateTimePeriodFormGroup.enable({emitEvent: false});
    }
  }

  writeValue(datePeriod: FixedWindow): void {
    this.modelValue = datePeriod;
    if (this.modelValue) {
      this.dateTimePeriodFormGroup.patchValue({
        startDate: new Date(this.modelValue.startTimeMs),
        endDate: new Date(this.modelValue.endTimeMs)
      }, {emitEvent: false});
    } else {
      const now = new Date();
      this.dateTimePeriodFormGroup.patchValue({
        startDate: new Date(now.getTime() - DAY),
        endDate: now
      }, {emitEvent: false});
      this.updateView();
    }
    this.updateMinMaxDates(this.dateTimePeriodFormGroup.value as DateTimePeriod);
  }

  private updateView() {
    const {startDate, endDate} = this.dateTimePeriodFormGroup.value;
    let value: FixedWindow = null;
    if (startDate && endDate) {
      value = {
        startTimeMs: startDate.getTime(),
        endTimeMs: endDate.getTime()
      };
    }
    this.modelValue = value;
    if (!this.propagateChange) {
      this.changePending = true;
    } else {
      this.propagateChange(this.modelValue);
    }
  }

  private updateMinMaxDates(dateTimePeriod: Partial<DateTimePeriod>) {
    this.maxEndDate = new Date();
    this.maxEndTs = this.maxEndDate.getTime();
    this.maxStartTs = this.maxEndTs - this.timeShiftMs;
    this.maxStartDate = new Date(this.maxStartTs);

    if (dateTimePeriod.endDate) {
      this.maxStartDateTs = dateTimePeriod.endDate.getTime() - this.timeShiftMs;
    }
    if (dateTimePeriod.startDate) {
      this.minEndDateTs = dateTimePeriod.startDate.getTime() + this.timeShiftMs;
    }
  }

  private onStartDateChange(startDate: Date) {
    if (!startDate) {
      return;
    }
    let startDateTs = startDate.getTime();
    if (startDateTs > this.maxStartTs) {
      startDateTs = this.maxStartTs;
      this.dateTimePeriodFormGroup.get('startDate').patchValue(new Date(startDateTs), {emitEvent: false});
    }
    if (this.maxStartDateTs !== undefined && startDateTs > this.maxStartDateTs) {
      this.dateTimePeriodFormGroup.get('endDate').patchValue(new Date(startDateTs + this.timeShiftMs), {emitEvent: false});
    }
  }

  private onEndDateChange(endDate: Date) {
    if (!endDate) {
      return;
    }
    let endDateTs = endDate.getTime();
    if (endDateTs > this.maxEndTs) {
      endDateTs = this.maxEndTs;
      this.dateTimePeriodFormGroup.get('endDate').patchValue(new Date(endDateTs), {emitEvent: false});
    }
    if (this.minEndDateTs !== undefined && endDateTs < this.minEndDateTs) {
      this.dateTimePeriodFormGroup.get('startDate').patchValue(new Date(endDateTs - this.timeShiftMs), {emitEvent: false});
    }
  }
}
