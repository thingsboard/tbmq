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
  AfterViewInit,
  Component,
  ElementRef,
  forwardRef,
  Input,
  OnChanges,
  OnInit,
  SimpleChanges,
  ViewChild
} from '@angular/core';
import { ControlValueAccessor, NG_VALIDATORS, NG_VALUE_ACCESSOR, UntypedFormBuilder, UntypedFormGroup, ValidationErrors, Validators, FormsModule, ReactiveFormsModule } from '@angular/forms';
import { Observable, of } from 'rxjs';
import { filter, map, mergeMap, share, tap } from 'rxjs/operators';
import { TranslateService, TranslateModule } from '@ngx-translate/core';
import { EntityType } from '@shared/models/entity-type.models';
import { BaseData } from '@shared/models/base-data';
import { EntityService } from '@core/http/entity.service';
import { MatAutocomplete, MatAutocompleteTrigger, MatAutocompleteOrigin } from '@angular/material/autocomplete';
import { MatChipGrid, MatChipRow, MatChipRemove, MatChipInput } from '@angular/material/chips';
import { coerceBooleanProperty } from '@angular/cdk/coercion';
import { MatFormFieldAppearance, SubscriptSizing, MatFormField, MatLabel, MatHint, MatError, MatSuffix } from '@angular/material/form-field';
import { coerceBoolean } from '@shared/decorators/coercion';
import { isArray } from 'lodash';
import { AsyncPipe } from '@angular/common';
import { MatIcon } from '@angular/material/icon';
import { MatInput } from '@angular/material/input';
import { MatOption } from '@angular/material/core';
import { HighlightPipe } from '../../pipe/highlight.pipe';

@Component({
    selector: 'tb-entity-list',
    templateUrl: './entity-list.component.html',
    styleUrls: [],
    providers: [
        {
            provide: NG_VALUE_ACCESSOR,
            useExisting: forwardRef(() => EntityListComponent),
            multi: true
        },
        {
            provide: NG_VALIDATORS,
            useExisting: forwardRef(() => EntityListComponent),
            multi: true
        }
    ],
    standalone: true,
    imports: [MatFormField, FormsModule, ReactiveFormsModule, MatLabel, MatChipGrid, MatChipRow, MatIcon, MatChipRemove, MatInput, MatAutocompleteTrigger, MatChipInput, MatAutocompleteOrigin, MatAutocomplete, MatOption, MatHint, MatError, MatSuffix, AsyncPipe, TranslateModule, HighlightPipe]
})
export class EntityListComponent implements ControlValueAccessor, OnInit, OnChanges {

  entityListFormGroup: UntypedFormGroup;

  private modelValue: Array<string> | null;

  @Input()
  fetchEntitiesFunction: (searchText?: string) => Observable<Array<BaseData>>;

  @Input()
  appearance: MatFormFieldAppearance = 'fill';

  @Input()
  entityType: EntityType;

  @Input()
  entitySubType = '';

  @Input()
  entityListText = 'entity.entity-list';

  @Input()
  noEntitiesText = 'entity.no-entities-matching';

  @Input()
  entitiesRequiredText = 'entity.entity-list-empty';

  // @Input()
  // subType: string;

  @Input()
  labelText: string;

  @Input()
  placeholderText = this.translate.instant('entity.entity-list');

  @Input()
  requiredText = this.translate.instant('entity.entity-list-empty');

  private requiredValue: boolean;
  get required(): boolean {
    return this.requiredValue;
  }
  @Input()
  set required(value: boolean) {
    const newVal = coerceBooleanProperty(value);
    if (this.requiredValue !== newVal) {
      this.requiredValue = newVal;
      this.updateValidators();
    }
  }

  @Input()
  disabled: boolean;

  @Input()
  subscriptSizing: SubscriptSizing = 'fixed';

  @Input()
  hint: string;

  @Input()
  @coerceBoolean()
  syncIdsWithDB = false;

  @ViewChild('entityInput') entityInput: ElementRef<HTMLInputElement>;
  @ViewChild('entityAutocomplete') matAutocomplete: MatAutocomplete;
  @ViewChild('chipList', {static: true}) chipList: MatChipGrid;

  entities: Array<BaseData> = [];
  filteredEntities: Observable<Array<BaseData>>;

  searchText = '';

  private dirty = false;

  private propagateChange = (v: any) => { };

  constructor(public translate: TranslateService,
              private entityService: EntityService,
              private fb: UntypedFormBuilder) {
    this.entityListFormGroup = this.fb.group({
      entities: [this.entities],
      entity: [null]
    });
  }

  private updateValidators() {
    this.entityListFormGroup.get('entities').setValidators(this.required ? [Validators.required] : []);
    this.entityListFormGroup.get('entities').updateValueAndValidity();
  }

  registerOnChange(fn: any): void {
    this.propagateChange = fn;
  }

  registerOnTouched(fn: any): void {
  }

  ngOnInit() {
    this.filteredEntities = this.entityListFormGroup.get('entity').valueChanges
    .pipe(
      tap((value) => {
        if (value && typeof value !== 'string') {
          this.add(value);
        } else if (value === null) {
          this.clear(this.entityInput.nativeElement.value);
        }
      }),
      filter((value) => typeof value === 'string'),
      map((value) => value ? value : ''),
      mergeMap(name => this.fetchEntities(name) ),
      share()
    );
  }

  ngOnChanges(changes: SimpleChanges): void {
    for (const propName of Object.keys(changes)) {
      const change = changes[propName];
      if (!change.firstChange && change.currentValue !== change.previousValue) {
        if (propName === 'entityType') {
          this.reset();
        }
      }
    }
  }

  setDisabledState(isDisabled: boolean): void {
    this.disabled = isDisabled;
    if (isDisabled) {
      this.entityListFormGroup.disable({emitEvent: false});
    } else {
      this.entityListFormGroup.enable({emitEvent: false});
    }
  }

  writeValue(value: Array<string> | Array<any> | null): void {
    this.searchText = '';
    if (value?.length > 0) {
      let entitiesObservable: Observable<Array<BaseData>>;
      if (typeof value[0] === 'string') {
        const entityIds = value as Array<string>;
        this.modelValue = [...entityIds];
        entitiesObservable = this.entityService.getEntities(this.entityType, entityIds);
      } else {
        const entities = value as Array<any>;
        this.modelValue = entities.map(entity => entity.id.id);
        entitiesObservable = of(entities);
      }
      entitiesObservable.subscribe(
        (entities) => {
          this.entities = entities;
          this.entityListFormGroup.get('entities').setValue(this.entities);
          if (this.syncIdsWithDB && this.modelValue.length !== entities.length) {
            this.modelValue = entities.map(entity => entity.id);
            this.propagateChange(this.modelValue);
          }
        }
      );
    } else {
      this.entities = [];
      this.entityListFormGroup.get('entities').setValue(this.entities);
      this.modelValue = null;
    }
    this.dirty = true;
  }

  validate(): ValidationErrors | null {
    return (isArray(this.modelValue) && this.modelValue.length) || !this.required ? null : {
      entities: {valid: false}
    };
  }

  private reset() {
    this.entities = [];
    this.entityListFormGroup.get('entities').setValue(this.entities);
    this.modelValue = null;
    if (this.entityInput) {
      this.entityInput.nativeElement.value = '';
    }
    this.entityListFormGroup.get('entity').patchValue('', {emitEvent: false});
    this.propagateChange(this.modelValue);
    this.dirty = true;
  }

  private add(entity: BaseData): void {
    if (!this.modelValue || this.modelValue.indexOf(entity.id) === -1) {
      if (!this.modelValue) {
        this.modelValue = [];
      }
      this.modelValue.push(entity.id);
      this.entities.push(entity);
      this.entityListFormGroup.get('entities').setValue(this.entities);
    }
    this.propagateChange(this.modelValue);
    this.clear();
  }

  public remove(entity: BaseData) {
    let index = this.entities.indexOf(entity);
    if (index >= 0) {
      this.entities.splice(index, 1);
      this.entityListFormGroup.get('entities').setValue(this.entities);
      index = this.modelValue.indexOf(entity.id);
      this.modelValue.splice(index, 1);
      if (!this.modelValue.length) {
        this.modelValue = null;
      }
      this.propagateChange(this.modelValue);
      this.clear();
    }
  }

  public displayEntityFn(entity?: BaseData): string | undefined {
    return entity ? entity.name : undefined;
  }

  private fetchEntities(searchText?: string): Observable<Array<BaseData>> {
    this.searchText = searchText;
    if (this.fetchEntitiesFunction) {
      return this.fetchEntitiesFunction(searchText).pipe(
        map((data) => data ? data : []));
    } else {
      return this.entityService.getEntitiesByNameFilter(this.entityType, searchText,
        50, this.entitySubType, {ignoreLoading: true}).pipe(
        map((data) => data ? data : []));
    }
  }

  public onFocus() {
    if (this.dirty) {
      this.entityListFormGroup.get('entity').updateValueAndValidity({onlySelf: true, emitEvent: true});
      this.dirty = false;
    }
  }

  private clear(value: string = '') {
    this.entityInput.nativeElement.value = value;
    this.entityListFormGroup.get('entity').patchValue(value, {emitEvent: true});
    setTimeout(() => {
      this.entityInput.nativeElement.blur();
      this.entityInput.nativeElement.focus();
    }, 0);
  }

  get placeholder(): string {
    return this.placeholderText ? this.placeholderText : (this.entityListText ? this.translate.instant(this.entityListText): undefined);
  }

  get requiredLabel(): string {
    return this.requiredText ? this.requiredText :
      (this.entitiesRequiredText ? this.translate.instant(this.entitiesRequiredText): undefined);
  }

  public textIsNotEmpty(text: string): boolean {
    return (text && text.length > 0);
  }
}
