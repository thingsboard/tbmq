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

import { Component, forwardRef, input, Input, OnChanges, OnDestroy, signal, SimpleChanges } from '@angular/core';
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
import { IntegrationTopicFilter, Integration } from '@shared/models/integration.models';
import { Subject } from 'rxjs';
import { takeUntil } from 'rxjs/operators';

import { MatError, MatFormField, MatLabel, MatSuffix } from '@angular/material/form-field';
import { TranslateModule } from '@ngx-translate/core';
import { MatTooltip } from '@angular/material/tooltip';
import { MatIcon } from '@angular/material/icon';
import { MatButton, MatIconButton } from '@angular/material/button';
import { MatInput } from '@angular/material/input';
import { IntegrationService } from '@core/http/integration.service';
import { filterTopics, getLocalStorageTopics, isDefinedAndNotNull } from '@core/utils';
import { CopyButtonComponent } from '@shared/components/button/copy-button.component';
import { MatAutocomplete, MatAutocompleteTrigger, MatOption } from '@angular/material/autocomplete';

@Component({
  selector: 'tb-integration-topic-filters',
  templateUrl: './integration-topic-filters.component.html',
  styleUrls: ['./integration-topic-filters.component.scss'],
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
    CopyButtonComponent,
    MatAutocomplete,
    MatOption,
    MatAutocompleteTrigger
  ],
  providers: [{
    provide: NG_VALUE_ACCESSOR,
    useExisting: forwardRef(() => IntegrationTopicFiltersComponent),
    multi: true
  },
  {
    provide: NG_VALIDATORS,
    useExisting: forwardRef(() => IntegrationTopicFiltersComponent),
    multi: true,
  }]
})
export class IntegrationTopicFiltersComponent implements ControlValueAccessor, Validator, OnDestroy, OnChanges {

  integrationTopicFiltersForm: UntypedFormGroup;

  @Input()
  disabled: boolean;

  topicFiltersHasDuplicates = signal<boolean>(false);
  integration = input<Integration>();
  isEdit = input<boolean>();
  activeSubscriptions: string[] = [];
  subscriptionsLoaded = false;
  filteredTopics = [];

  private destroy$ = new Subject<void>();
  private propagateChange = (v: any) => { };

  constructor(private fb: UntypedFormBuilder,
              private integrationService: IntegrationService) {
    this.integrationTopicFiltersForm = this.fb.group({
      filters: this.fb.array([], [Validators.required, this.isUnique])
    });
    this.integrationTopicFiltersForm.valueChanges.pipe(
      takeUntil(this.destroy$)
    ).subscribe((value) => {
      this.updateModel(value.filters);
    });
    this.integrationService.restarted
      .pipe(takeUntil(this.destroy$))
      .subscribe(() => this.updateActiveSubscriptions());
  }

  ngOnDestroy() {
    this.destroy$.next();
    this.destroy$.complete();
  }

  ngOnChanges(changes: SimpleChanges): void {
    for (const propName of Object.keys(changes)) {
      const change = changes[propName];
      if (!change.firstChange && change.currentValue !== change.previousValue) {
        if (propName === 'integration' && change.currentValue) {
          this.updateActiveSubscriptions();
        }
      }
    }
  }

  writeValue(value: IntegrationTopicFilter[]) {
    this.subscriptionsLoaded = false;
    if (this.integrationFiltersFromArray.length === value?.length) {
      const filters = value.map(el => {
        return {filter: el}
      });
      this.integrationTopicFiltersForm.get('filters').patchValue(filters, {emitEvent: false});
    } else {
      const filtersControls: Array<AbstractControl> = [];
      if (value) {
        value.forEach((filter) => {
          filtersControls.push(this.fb.group({
            filter: [filter, [Validators.required]]
          }));
        });
      }
      this.integrationTopicFiltersForm.setControl('filters', this.fb.array(filtersControls), {emitEvent: true});
      this.integrationTopicFiltersForm.addValidators(this.isUnique());
      if (this.disabled) {
        this.integrationTopicFiltersForm.disable({emitEvent: false});
      } else {
        this.integrationTopicFiltersForm.enable({emitEvent: false});
      }
      this.integrationTopicFiltersForm.updateValueAndValidity();
    }
    this.topicFiltersSubscribeValueChanges();
  }

  registerOnChange(fn: any) {
    this.propagateChange = fn;
  }

  registerOnTouched(fn: any) { }

  setDisabledState(isDisabled: boolean): void {
    this.disabled = isDisabled;
    if (this.disabled) {
      this.integrationTopicFiltersForm.disable({emitEvent: false});
    } else {
      this.integrationTopicFiltersForm.enable({emitEvent: false});
    }
  }

  get integrationFiltersFromArray(): UntypedFormArray {
    return this.integrationTopicFiltersForm.get('filters') as UntypedFormArray;
  }

  addTopicFilter() {
    const formGroup = this.fb.group({
      filter: ['', [Validators.required]]
    });
    this.subscribeTopicValueChanges(formGroup);
    this.integrationFiltersFromArray.push(formGroup);
  }

  private updateModel(value: IntegrationTopicFilter[]) {
    const transformedValue = value.map(el => el.filter);
    this.propagateChange(transformedValue);
  }

  topicFiltersSubscribeValueChanges() {
    this.integrationFiltersFromArray.controls.forEach(control => this.subscribeTopicValueChanges(control));
  }

  clearFilteredOptions() {
    setTimeout(() => {
      this.filteredTopics = getLocalStorageTopics();
    }, 100);
  }

  private subscribeTopicValueChanges(control) {
    control.get('filter').valueChanges
      .pipe(takeUntil(this.destroy$))
      .subscribe(value => this.filteredTopics = filterTopics(value));
  }

  validate(): ValidationErrors | null {
    return this.integrationTopicFiltersForm.valid ? null : {
      integrationTopicFilters: {valid: false}
    };
  }

  subscriptionActive(topicFilter: AbstractControl<IntegrationTopicFilter>): boolean {
    return this.activeSubscriptions.indexOf(topicFilter.value.filter) > -1;
  }

  topicFilterValue(control: AbstractControl): string {
    return control.value.filter;
  }

  private updateActiveSubscriptions() {
    if (this.integration()?.id) {
      this.integrationService.getIntegrationSubscriptions(this.integration().id)
        .pipe(takeUntil(this.destroy$))
        .subscribe(subscriptions => {
          this.activeSubscriptions = subscriptions;
          this.subscriptionsLoaded = true;
        });
    }
  }

  private isUnique(): ValidatorFn {
    return (control: FormControl) => {
      const filtersList = this.integrationFiltersFromArray.value.map(item => item.filter);
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
