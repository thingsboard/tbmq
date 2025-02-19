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

import { booleanAttribute, Component, forwardRef, HostBinding, input, model } from '@angular/core';
import { Store } from '@ngrx/store';
import { AppState } from '@core/core.state';
import { ControlValueAccessor, NG_VALUE_ACCESSOR } from '@angular/forms';
import { _ToggleBase, ToggleHeaderAppearance } from '@shared/components/toggle-header.component';
import { ToggleHeaderComponent } from './toggle-header.component';

@Component({
    selector: 'tb-toggle-select',
    templateUrl: './toggle-select.component.html',
    styleUrls: ['./toggle-select.component.scss'],
    providers: [
        {
            provide: NG_VALUE_ACCESSOR,
            useExisting: forwardRef(() => ToggleSelectComponent),
            multi: true
        }
    ],
    imports: [ToggleHeaderComponent]
})
export class ToggleSelectComponent extends _ToggleBase implements ControlValueAccessor {

  @HostBinding('style.maxWidth')
  get maxWidth() { return '100%'; }

  disabled = model<boolean>();
  readonly selectMediaBreakpoint = input<string>();
  readonly appearance = input<ToggleHeaderAppearance>('stroked');
  readonly disablePagination = input(false, {transform: booleanAttribute});
  readonly fillHeight = input(false, {transform: booleanAttribute});
  readonly extraPadding = input(false, {transform: booleanAttribute});
  readonly primaryBackground = input(false, {transform: booleanAttribute});

  modelValue: any;
  private propagateChange = null;

  constructor(protected store: Store<AppState>) {
    super(store);
  }

  registerOnChange(fn: any): void {
    this.propagateChange = fn;
  }

  registerOnTouched(fn: any): void {
  }

  setDisabledState(isDisabled: boolean): void {
    this.disabled.set(isDisabled);
  }

  writeValue(value: any): void {
    this.modelValue = value;
  }

  updateModel(value: any) {
    this.modelValue = value;
    this.propagateChange(this.modelValue);
  }
}
