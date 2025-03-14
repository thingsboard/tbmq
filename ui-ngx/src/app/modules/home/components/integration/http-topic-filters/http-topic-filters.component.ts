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

import { Component, forwardRef, input, Input, OnDestroy, signal } from '@angular/core';
import {
  AbstractControl,
  ControlValueAccessor,
  UntypedFormArray,
  UntypedFormBuilder,
  UntypedFormGroup,
  NG_VALIDATORS,
  NG_VALUE_ACCESSOR,
  ValidationErrors,
  Validator,
  Validators, ReactiveFormsModule, ValidatorFn, FormControl
} from '@angular/forms';
import { HttpTopicFilter, Integration } from '@shared/models/integration.models';
import { Subject } from 'rxjs';
import { takeUntil } from 'rxjs/operators';

import { MatError, MatFormField, MatLabel, MatSuffix } from '@angular/material/form-field';
import { TranslateModule } from '@ngx-translate/core';
import { MatTooltip } from '@angular/material/tooltip';
import { MatIcon } from '@angular/material/icon';
import { MatButton, MatIconButton } from '@angular/material/button';
import { MatInput } from '@angular/material/input';
import { IntegrationService } from '@core/http/integration.service';
import { isDefinedAndNotNull } from '@core/utils';
import { CopyButtonComponent } from '@shared/components/button/copy-button.component';

@Component({
  selector: 'tb-http-topic-filters',
  templateUrl: './http-topic-filters.component.html',
  styleUrls: ['./http-topic-filters.component.scss'],
  imports: [
    ReactiveFormsModule,
    MatFormField,
    TranslateModule,
    MatTooltip,
    MatIcon,
    MatButton,
    MatIconButton,
    MatInput,
    MatLabel,
    MatError,
    MatSuffix,
    CopyButtonComponent
  ],
  providers: [{
    provide: NG_VALUE_ACCESSOR,
    useExisting: forwardRef(() => HttpTopicFiltersComponent),
    multi: true
  },
  {
    provide: NG_VALIDATORS,
    useExisting: forwardRef(() => HttpTopicFiltersComponent),
    multi: true,
  }]
})
export class HttpTopicFiltersComponent implements ControlValueAccessor, Validator, OnDestroy {

  httpTopicFiltersForm: UntypedFormGroup;

  @Input()
  disabled: boolean;

  topicFiltersHasDuplicates = signal<boolean>(false);
  integration = input<Integration>();
  isEdit = input<boolean>();
  activeSubscriptions: string[] = [];
  subscriptionsLoaded = false;

  private destroy$ = new Subject<void>();
  private propagateChange = (v: any) => { };

  constructor(private fb: UntypedFormBuilder,
              private integrationService: IntegrationService) {
    this.httpTopicFiltersForm = this.fb.group({
      filters: this.fb.array([], [Validators.required, this.isUnique])
    });
    this.httpTopicFiltersForm.valueChanges.pipe(
      takeUntil(this.destroy$)
    ).subscribe((value) => {
      this.updateModel(value.filters);
    });
    this.integrationService.restarted.subscribe(() => this.updateActiveSubscriptions());
  }

  ngOnDestroy() {
    this.destroy$.next();
    this.destroy$.complete();
  }

  writeValue(value: HttpTopicFilter[]) {
    this.subscriptionsLoaded = false;
    // TODO refactor component / model HttpTopicFilter
    // @ts-ignore
    value = value.map(el => {
      return {filter: el}
    });
    if (this.httpFiltersFromArray.length === value?.length) {
      this.httpTopicFiltersForm.get('filters').patchValue(value, {emitEvent: false});
    } else {
      const filtersControls: Array<AbstractControl> = [];
      if (value) {
        value.forEach((filter) => {
          filtersControls.push(this.fb.group({
            filter: [filter.filter, [Validators.required]]
          }));
        });
      }
      this.httpTopicFiltersForm.setControl('filters', this.fb.array(filtersControls), {emitEvent: true});
      this.httpTopicFiltersForm.addValidators(this.isUnique());
      if (this.disabled) {
        this.httpTopicFiltersForm.disable({emitEvent: false});
      } else {
        this.httpTopicFiltersForm.enable({emitEvent: false});
      }
      this.httpTopicFiltersForm.updateValueAndValidity();
    }
    if (isDefinedAndNotNull(value)) {
      this.updateActiveSubscriptions();
    }
  }

  registerOnChange(fn: any) {
    this.propagateChange = fn;
  }

  registerOnTouched(fn: any) { }

  setDisabledState(isDisabled: boolean): void {
    this.disabled = isDisabled;
    if (this.disabled) {
      this.httpTopicFiltersForm.disable({emitEvent: false});
    } else {
      this.httpTopicFiltersForm.enable({emitEvent: false});
    }
  }

  get httpFiltersFromArray(): UntypedFormArray {
    return this.httpTopicFiltersForm.get('filters') as UntypedFormArray;
  }

  addTopicFilter() {
    this.httpFiltersFromArray.push(this.fb.group({
      filter: ['', [Validators.required]]
    }));
  }

  private updateModel(value: HttpTopicFilter[]) {
    const transformedValue = value.map(el => el.filter);
    this.propagateChange(transformedValue);
  }

  validate(): ValidationErrors | null {
    return this.httpTopicFiltersForm.valid ? null : {
      httpTopicFilters: {valid: false}
    };
  }

  subscriptionActive(topicFilter: AbstractControl<HttpTopicFilter>): boolean {
    return this.activeSubscriptions.indexOf(topicFilter.value.filter) > -1;
  }

  topicFilterValue(control: AbstractControl): string {
    return control.value.filter;
  }

  private updateActiveSubscriptions() {
    if (this.integration()?.id) {
      this.integrationService.getIntegrationSubscriptions(this.integration().id)
        .subscribe(subscriptions => {
          this.activeSubscriptions = subscriptions;
          this.subscriptionsLoaded = true;
        });
    }
  }

  private isUnique(): ValidatorFn {
    return (control: FormControl) => {
      const filtersList = this.httpFiltersFromArray.value.map(item => item.filter);
      const formArrayHasDuplicates = filtersList.some((item, idx) => filtersList.indexOf(item) !== idx);
      if (formArrayHasDuplicates) {
        this.topicFiltersHasDuplicates.set(true);
        return {isUnique: false};
      } else {
        this.topicFiltersHasDuplicates.set(false);
        return null;
      }
    };
  }

}
