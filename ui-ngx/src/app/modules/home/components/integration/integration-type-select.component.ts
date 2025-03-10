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

import { Component, ElementRef, forwardRef, Input, OnInit, viewChild } from '@angular/core';
import {
  ControlValueAccessor,
  UntypedFormBuilder,
  UntypedFormGroup,
  NG_VALUE_ACCESSOR,
  Validators,
  ReactiveFormsModule
} from '@angular/forms';
import { coerceBooleanProperty } from '@angular/cdk/coercion';
import { isNotEmptyStr, isString } from '@core/utils';
import { IntegrationType, IntegrationTypeInfo, integrationTypeInfoMap } from '@shared/models/integration.models';
import { Observable, of } from 'rxjs';
import { distinctUntilChanged, map, mergeMap, share, tap } from 'rxjs/operators';
import { TranslateModule, TranslateService } from '@ngx-translate/core';
import { MatAutocomplete, MatAutocompleteTrigger, MatOption } from '@angular/material/autocomplete';
import { MatFormField, MatLabel, MatSuffix } from '@angular/material/form-field';
import { MatIconButton } from '@angular/material/button';
import { MatIcon } from '@angular/material/icon';
import { AsyncPipe } from '@angular/common';
import { MatInput } from '@angular/material/input';
import { MatTooltip } from '@angular/material/tooltip';

type IntegrationInfo = IntegrationTypeInfo & {type: IntegrationType};

@Component({
  selector: 'tb-integration-type-select',
  templateUrl: 'integration-type-select.component.html',
  styleUrls: ['integration-type-select.component.scss'],
  imports: [
    MatFormField,
    ReactiveFormsModule,
    TranslateModule,
    MatAutocompleteTrigger,
    MatIconButton,
    MatIcon,
    MatAutocomplete,
    MatOption,
    AsyncPipe,
    MatInput,
    MatTooltip,
    MatSuffix,
    MatLabel
  ],
  providers: [{
    provide: NG_VALUE_ACCESSOR,
    useExisting: forwardRef(() => IntegrationTypeSelectComponent),
    multi: true
  }]
})
export class IntegrationTypeSelectComponent implements ControlValueAccessor, OnInit {

  integrationTypeFormGroup: UntypedFormGroup;
  searchText = '';

  filteredIntegrationTypes: Observable<Array<IntegrationInfo>>;
  modelValue: IntegrationInfo;

  private pristine = true;

  private integrationTypesInfo: Array<IntegrationInfo> = [];

  readonly integrationTypeInput = viewChild<ElementRef>('integrationTypeInput');
  readonly autocomplete = viewChild(MatAutocompleteTrigger);

  private requiredValue: boolean;

  get required(): boolean {
    return this.requiredValue;
  }

  @Input()
  set required(value: boolean) {
    this.requiredValue = coerceBooleanProperty(value);
    if (this.requiredValue) {
      this.integrationTypeFormGroup.get('type').setValidators(Validators.required);
    } else {
      this.integrationTypeFormGroup.get('type').clearValidators();
    }
    this.integrationTypeFormGroup.get('type').updateValueAndValidity({emitEvent: false});
  }

  @Input()
  disabled: boolean;

  private propagateChange = (v: any) => { };

  constructor(private fb: UntypedFormBuilder,
              private translate: TranslateService) {
    this.integrationTypeFormGroup = this.fb.group({
      type: ['']
    });
    Object.values(IntegrationType).forEach(integrationType => {
      const integration = integrationTypeInfoMap.get(integrationType);
      // @ts-ignore
      this.integrationTypesInfo.push({
        type: integrationType,
        ...integration,
        name: this.translate.instant(integration.name),
        description: integration.description ? this.translate.instant(integration.description) : ''
      });
    });
  }

  ngOnInit() {
    this.filteredIntegrationTypes = this.integrationTypeFormGroup.get('type').valueChanges
      .pipe(
        tap(value => {
          let modelValue;
          if (isString(value) || !value) {
            modelValue = null;
          } else {
            modelValue = this.integrationTypesInfo.find(info => info.type === value.type);
          }
          this.updateView(modelValue);
          if (value === null) {
            this.clear();
          }
        }),
        map(value => value ? (isString(value) ? value.trim() : value.type) : ''),
        distinctUntilChanged(),
        mergeMap(name => this.fetchIntegrationTypes(name)),
        share()
      );
  }

  registerOnChange(fn: any) {
    this.propagateChange = fn;
  }

  registerOnTouched(fn: any) {
  }

  setDisabledState(isDisabled: boolean) {
    this.disabled = isDisabled;
    if (this.disabled) {
      this.integrationTypeFormGroup.disable({emitEvent: false});
    } else {
      this.integrationTypeFormGroup.enable({emitEvent: false});
    }
  }

  writeValue(value: IntegrationType) {
    this.searchText = '';
    const integrationType = value != null && this.integrationTypesInfo.find(integration => integration.type === value);
    if (integrationType) {
      this.modelValue = integrationType;
      this.integrationTypeFormGroup.get('type').patchValue(this.modelValue, {emitEvent: false});
    } else {
      this.modelValue = null;
      this.integrationTypeFormGroup.get('type').patchValue('', {emitEvent: false});
    }
    this.pristine = true;
  }

  onFocus() {
    if (this.pristine) {
      this.integrationTypeFormGroup.get('type').updateValueAndValidity({onlySelf: true});
      this.pristine = false;
    }
  }

  selectedType() {
    if (isNotEmptyStr(this.searchText)) {
      const result = this.filterIntegrationType(this.searchText);
      if (result.length === 1) {
        this.integrationTypeFormGroup.get('type').patchValue(result[0]);
        this.autocomplete().closePanel();
      }
    }
  }

  clear() {
    this.integrationTypeFormGroup.get('type').patchValue('');
    setTimeout(() => {
      this.integrationTypeInput().nativeElement.blur();
      this.integrationTypeInput().nativeElement.focus();
    }, 0);
  }

  displayIntegrationTypeFn(inegration?: IntegrationInfo): string | undefined {
    return inegration ? inegration.name : undefined;
  }

  private updateView(value: IntegrationInfo | null) {
    if (this.modelValue !== value) {
      this.modelValue = value;
      this.propagateChange(this.modelValue?.type || null);
    }
  }

  private fetchIntegrationTypes(searchText?: string): Observable<Array<IntegrationInfo>> {
    this.searchText = searchText;
    let result = this.integrationTypesInfo;
    if (isNotEmptyStr(searchText)) {
      result = this.filterIntegrationType(searchText);
    }
    return of(result);
  }

  private filterIntegrationType(searchText: string): Array<IntegrationInfo> {
    const regex = new RegExp(searchText, 'i');
    return this.integrationTypesInfo.filter((integrationInfo) =>
      regex.test(integrationInfo.name) || regex.test(integrationInfo.description) ||
      searchText === integrationInfo.type || regex.test(integrationInfo.tags?.toString()));
  }
}
