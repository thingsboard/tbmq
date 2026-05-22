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

import { Component, forwardRef, input, model } from '@angular/core';
import { ControlValueAccessor, NG_VALUE_ACCESSOR, FormsModule, ReactiveFormsModule } from '@angular/forms';
import { MatChipEditedEvent, MatChipInputEvent, MatChipGrid, MatChipRow, MatChipRemove, MatChipInput, MatChipEdit } from '@angular/material/chips';
import { ENTER, SEMICOLON, TAB } from '@angular/cdk/keycodes';
import { MatFormField, MatLabel, MatSuffix } from '@angular/material/form-field';
import { MatInput } from '@angular/material/input';
import { MatIcon } from '@angular/material/icon';
import { MatTooltip } from '@angular/material/tooltip';
import { TranslateModule } from '@ngx-translate/core';
import { CopyButtonComponent } from '@shared/components/button/copy-button.component';

@Component({
  selector: 'tb-topic-rules-chip-list',
  templateUrl: './topic-rules-chip-list.component.html',
  providers: [
    {
      provide: NG_VALUE_ACCESSOR,
      useExisting: forwardRef(() => TopicRulesChipListComponent),
      multi: true
    }
  ],
  imports: [
    FormsModule,
    ReactiveFormsModule,
    MatFormField,
    MatLabel,
    MatInput,
    MatChipGrid,
    MatChipRow,
    MatChipRemove,
    MatChipInput,
    MatChipEdit,
    MatIcon,
    MatSuffix,
    MatTooltip,
    TranslateModule,
    CopyButtonComponent
  ]
})
export class TopicRulesChipListComponent implements ControlValueAccessor {

  readonly label = input<string>('');
  readonly placeholder = input<string>('');
  readonly warningTooltip = input<string>('');
  disabled = model<boolean>(false);

  public rules: string[] = [];
  public separatorKeyCodes = [ENTER, TAB, SEMICOLON];

  private propagateChange = (_: string[]) => {};
  private propagateTouched = () => {};

  public writeValue(value: string[]): void {
    this.rules = value ? [...value] : [];
  }

  public registerOnChange(fn: any): void {
    this.propagateChange = fn;
  }

  public registerOnTouched(fn: any): void {
    this.propagateTouched = fn;
  }

  public setDisabledState(isDisabled: boolean): void {
    this.disabled.set(isDisabled);
  }

  public addRule(event: MatChipInputEvent): void {
    const value = (event.value || '').trim();

    if (value && !this.rules.includes(value)) {
      this.rules.push(value);
      this.emitChange();
    }

    event.chipInput.clear();
  }

  public removeRule(rule: string): void {
    const index = this.rules.indexOf(rule);

    if (index >= 0) {
      this.rules.splice(index, 1);
      this.emitChange();
    }
  }

  public editRule(event: MatChipEditedEvent): void {
    const oldRule = event.chip.value;
    const newRule = (event.value || '').trim();
    const index = this.rules.indexOf(oldRule);

    if (index !== -1) {
      if (newRule && !this.rules.includes(newRule)) {
        this.rules[index] = newRule;
      } else if (!newRule) {
        this.rules.splice(index, 1);
      }

      this.emitChange();
    }
  }

  public onPaste(event: ClipboardEvent): void {
    event.preventDefault();

    const pastedText = event.clipboardData?.getData('text') || '';
    const newRules = [...new Set(pastedText
      .split(/[\n\r;]+/)
      .map(r => r.trim())
      .filter(r => r.length > 0 && !this.rules.includes(r)))];

    if (newRules.length) {
      this.rules.push(...newRules);
      this.emitChange();
    }
  }

  private emitChange(): void {
    this.propagateChange([...this.rules]);
    this.propagateTouched();
  }
}
