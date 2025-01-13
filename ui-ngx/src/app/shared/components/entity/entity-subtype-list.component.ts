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
  ElementRef,
  forwardRef,
  Input,
  OnInit,
  ViewChild,
  input,
  model
} from '@angular/core';
import { ControlValueAccessor, FormBuilder, FormGroup, NG_VALUE_ACCESSOR, Validators, FormsModule, ReactiveFormsModule } from '@angular/forms';
import { Observable, ReplaySubject, throwError } from 'rxjs';
import { debounceTime, map, mergeMap, share } from 'rxjs/operators';
import { TranslateService, TranslateModule } from '@ngx-translate/core';
import { EntityType } from '@shared/models/entity-type.models';
import { MatAutocomplete, MatAutocompleteSelectedEvent, MatAutocompleteTrigger, MatAutocompleteOrigin } from '@angular/material/autocomplete';
import { MatChipGrid, MatChipInputEvent, MatChipRow, MatChipRemove, MatChipInput } from '@angular/material/chips';
import { COMMA, ENTER, SEMICOLON } from '@angular/cdk/keycodes';
import { FloatLabelType, MatFormFieldAppearance, SubscriptSizing, MatFormField, MatLabel, MatSuffix, MatError } from '@angular/material/form-field';
import { coerceBoolean } from '@shared/decorators/coercion';
import { ConfigService } from '@core/http/config.service';
import { AsyncPipe } from '@angular/common';
import { MatIcon } from '@angular/material/icon';
import { MatInput } from '@angular/material/input';
import { MatOption } from '@angular/material/core';
import { HighlightPipe } from '@shared/pipe/highlight.pipe';

@Component({
    selector: 'tb-entity-subtype-list',
    templateUrl: './entity-subtype-list.component.html',
    providers: [
        {
            provide: NG_VALUE_ACCESSOR,
            useExisting: forwardRef(() => EntitySubTypeListComponent),
            multi: true
        }
    ],
    imports: [MatFormField, FormsModule, ReactiveFormsModule, MatLabel, MatChipGrid, MatChipRow, MatIcon, MatChipRemove, MatInput, MatAutocompleteTrigger, MatChipInput, MatAutocompleteOrigin, MatAutocomplete, MatOption, MatSuffix, MatError, AsyncPipe, TranslateModule, HighlightPipe]
})
export class EntitySubTypeListComponent implements ControlValueAccessor, OnInit {

  entitySubtypeListFormGroup: FormGroup;

  modelValue: Array<string> | null;

  private requiredValue: boolean;

  get required(): boolean {
    return this.requiredValue;
  }

  @Input()
  @coerceBoolean()
  set required(value: boolean) {
    if (this.requiredValue !== value) {
      this.requiredValue = value;
      this.updateValidators();
    }
  }

  disabled = model<boolean>();
  readonly floatLabel = input<FloatLabelType>('auto');
  readonly label = input<string>();
  readonly entityType = input<EntityType>();
  readonly emptyInputPlaceholder = input<string>();
  readonly filledInputPlaceholder = input<string>();
  readonly appearance = input<MatFormFieldAppearance>('fill');
  readonly subscriptSizing = input<SubscriptSizing>('fixed');
  readonly additionalClasses = input<Array<string>>();
  readonly addValueOutOfList = input<boolean>(true);

  @ViewChild('entitySubtypeInput') entitySubtypeInput: ElementRef<HTMLInputElement>;
  @ViewChild('entitySubtypeAutocomplete') entitySubtypeAutocomplete: MatAutocomplete;
  @ViewChild('chipList', {static: true}) chipList: MatChipGrid;

  entitySubtypeList: Array<string> = [];
  filteredEntitySubtypeList: Observable<Array<string>>;
  private entitySubtypes: Observable<Array<string>>;
  private allEntitySubtypes: Array<string>;

  placeholder: string;
  secondaryPlaceholder: string;
  noSubtypesMathingText: string;
  subtypeListEmptyText: string;

  separatorKeysCodes: number[] = [ENTER, COMMA, SEMICOLON];

  searchText = '';

  private dirty = false;

  private propagateChange = (v: any) => { };

  constructor(public translate: TranslateService,
              private configService: ConfigService,
              private fb: FormBuilder) {
    this.entitySubtypeListFormGroup = this.fb.group({
      entitySubtypeList: [this.entitySubtypeList, this.required ? [Validators.required] : []],
      entitySubtype: [null]
    });
  }

  updateValidators() {
    this.entitySubtypeListFormGroup.get('entitySubtypeList').setValidators(this.required ? [Validators.required] : []);
    this.entitySubtypeListFormGroup.get('entitySubtypeList').updateValueAndValidity();
  }

  registerOnChange(fn: any): void {
    this.propagateChange = fn;
  }

  registerOnTouched(fn: any): void {
  }

  ngOnInit() {
    switch (this.entityType()) {
      case EntityType.MQTT_SESSION:
        this.placeholder = this.required ? this.translate.instant('asset.enter-asset-type')
          : this.translate.instant('asset.any-asset');
        this.secondaryPlaceholder = '+' + this.translate.instant('asset.asset-type');
        this.noSubtypesMathingText = 'asset.no-asset-types-matching';
        this.subtypeListEmptyText = 'asset.asset-type-list-empty';
        break;
    }

    const emptyInputPlaceholder = this.emptyInputPlaceholder();
    if (emptyInputPlaceholder) {
      this.placeholder = emptyInputPlaceholder;
    }
    const filledInputPlaceholder = this.filledInputPlaceholder();
    if (filledInputPlaceholder) {
      this.secondaryPlaceholder = filledInputPlaceholder;
    }

    this.filteredEntitySubtypeList = this.entitySubtypeListFormGroup.get('entitySubtype').valueChanges.pipe(
      debounceTime(150),
      map(value => value ? value : ''),
      mergeMap(name => this.fetchEntitySubtypes(name)),
      share()
    );
  }

  setDisabledState(isDisabled: boolean): void {
    this.disabled.set(isDisabled);
    if (isDisabled) {
      this.entitySubtypeListFormGroup.disable({emitEvent: false});
    } else {
      this.entitySubtypeListFormGroup.enable({emitEvent: false});
    }
  }

  writeValue(value: Array<string> | null): void {
    this.searchText = '';
    if (value != null && value.length > 0) {
      this.modelValue = [...value];
      this.entitySubtypeList = [...value];
      this.entitySubtypeListFormGroup.get('entitySubtypeList').setValue(this.entitySubtypeList);
    } else {
      this.entitySubtypeList = [];
      this.entitySubtypeListFormGroup.get('entitySubtypeList').setValue(this.entitySubtypeList);
      this.modelValue = null;
    }
    this.dirty = true;
  }

  private add(entitySubtype: string): void {
    if (!this.modelValue || this.modelValue.indexOf(entitySubtype) === -1) {
      if (!this.modelValue) {
        this.modelValue = [];
      }
      if (this.addValueOutOfList() || this.allEntitySubtypes.includes(entitySubtype)) {
        this.modelValue.push(entitySubtype);
        this.entitySubtypeList.push(entitySubtype);
        this.entitySubtypeListFormGroup.get('entitySubtypeList').setValue(this.entitySubtypeList);
      }
    }
    this.propagateChange(this.modelValue);
  }

  chipAdd(event: MatChipInputEvent): void {
    const value = (event.value || '').trim();
    if (value) {
      this.add(value);
    }
    this.clear('');
  }

  remove(entitySubtype: string) {
    const index = this.entitySubtypeList.indexOf(entitySubtype);
    if (index >= 0) {
      this.entitySubtypeList.splice(index, 1);
      this.entitySubtypeListFormGroup.get('entitySubtypeList').setValue(this.entitySubtypeList);
      this.modelValue.splice(index, 1);
      if (!this.modelValue.length) {
        this.modelValue = null;
      }
      this.propagateChange(this.modelValue);
    }
  }

  selected(event: MatAutocompleteSelectedEvent): void {
    this.add(event.option.viewValue);
    this.clear('');
  }

  displayEntitySubtypeFn(entitySubtype?: string): string | undefined {
    return entitySubtype ? entitySubtype : undefined;
  }

  private fetchEntitySubtypes(searchText?: string): Observable<Array<string>> {
    this.searchText = searchText;
    return this.getEntitySubtypes(searchText).pipe(
      map(subTypes => {
        let result;
        result = subTypes.filter(subType => searchText ? subType.toUpperCase().startsWith(searchText.toUpperCase()) : true);
        if (!result.length) {
          result = [searchText];
        }
        return result;
      })
    );
  }

  private getEntitySubtypes(searchText?: string): Observable<Array<string>> {
    if (!this.entitySubtypes) {
      let subTypesObservable: Observable<Array<string>>;
      switch (this.entityType()) {
        case EntityType.KAFKA_BROKER:
          subTypesObservable = this.configService.getBrokerServiceIds({ignoreLoading: true});
          break;
      }
      if (subTypesObservable) {
        this.entitySubtypes = subTypesObservable.pipe(
          map(subTypes => {
            this.allEntitySubtypes = subTypes;
            return subTypes.map(subType => subType)
          }),
          share({
            connector: () => new ReplaySubject(1),
            resetOnError: false,
            resetOnComplete: false,
            resetOnRefCountZero: true,
          }),
        );
      } else {
        return throwError(null);
      }
    }
    return this.entitySubtypes;
  }

  onFocus() {
    if (this.dirty) {
      this.entitySubtypeListFormGroup.get('entitySubtype').updateValueAndValidity({onlySelf: true, emitEvent: true});
      this.dirty = false;
    }
  }

  clear(value: string = '') {
    this.entitySubtypeInput.nativeElement.value = value;
    this.entitySubtypeListFormGroup.get('entitySubtype').patchValue(value, {emitEvent: true});
    setTimeout(() => {
      this.entitySubtypeInput.nativeElement.blur();
      this.entitySubtypeInput.nativeElement.focus();
    }, 0);
  }

}
