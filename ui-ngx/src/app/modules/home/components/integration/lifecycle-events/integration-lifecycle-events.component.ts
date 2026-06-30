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

import { Component, ElementRef, forwardRef, OnInit, viewChild } from '@angular/core';
import {
  ControlValueAccessor,
  FormsModule,
  NG_VALUE_ACCESSOR,
  ReactiveFormsModule,
  UntypedFormBuilder,
  UntypedFormGroup
} from '@angular/forms';
import { MatFormField, MatLabel } from '@angular/material/form-field';
import {
  MatAutocomplete,
  MatAutocompleteOrigin,
  MatAutocompleteTrigger
} from '@angular/material/autocomplete';
import { MatOption } from '@angular/material/core';
import { MatChipGrid, MatChipInput, MatChipRemove, MatChipRow } from '@angular/material/chips';
import { MatIcon } from '@angular/material/icon';
import { MatInput } from '@angular/material/input';
import { TranslateModule, TranslateService } from '@ngx-translate/core';
import { ClientLifecycleEventType } from '@shared/models/integration.models';
import { HighlightPipe } from '@shared/pipe/highlight.pipe';
import { AsyncPipe } from '@angular/common';
import { Observable, of } from 'rxjs';
import { filter, map, mergeMap, share, tap } from 'rxjs/operators';

interface EventTypeInfo {
  name: string;
  value: ClientLifecycleEventType;
}

@Component({
  selector: 'tb-integration-lifecycle-events',
  templateUrl: './integration-lifecycle-events.component.html',
  standalone: true,
  imports: [
    FormsModule, ReactiveFormsModule, MatFormField, MatLabel, MatChipGrid, MatChipRow, MatChipRemove,
    MatChipInput, MatInput, MatIcon, MatAutocomplete, MatAutocompleteTrigger, MatAutocompleteOrigin,
    MatOption, AsyncPipe, TranslateModule, HighlightPipe
  ],
  providers: [
    {
      provide: NG_VALUE_ACCESSOR,
      useExisting: forwardRef(() => IntegrationLifecycleEventsComponent),
      multi: true
    }
  ]
})
export class IntegrationLifecycleEventsComponent implements ControlValueAccessor, OnInit {

  readonly eventTypeInput = viewChild<ElementRef<HTMLInputElement>>('eventTypeInput');
  readonly eventTypeAutocomplete = viewChild<MatAutocomplete>('eventTypeAutocomplete');
  readonly chipList = viewChild<MatChipGrid>('chipList');

  private readonly eventTypeTranslations: Record<ClientLifecycleEventType, string> = {
    [ClientLifecycleEventType.CLIENT_CONNECTED]:     'integration.client-connected',
    [ClientLifecycleEventType.CLIENT_DISCONNECTED]:  'integration.client-disconnected',
    [ClientLifecycleEventType.CLIENT_SUBSCRIBED]:    'integration.client-subscribed',
    [ClientLifecycleEventType.CLIENT_UNSUBSCRIBED]:  'integration.client-unsubscribed',
    [ClientLifecycleEventType.CLIENT_AUTHENTICATION_FAILED]: 'integration.client-authentication-failed',
    [ClientLifecycleEventType.CLIENT_AUTHORIZATION_FAILED]:  'integration.client-authorization-failed',
    [ClientLifecycleEventType.CLIENT_CONNECTION_FAILED]:     'integration.client-connection-failed',
  };

  readonly allEventTypeList: Array<EventTypeInfo> = [
    ClientLifecycleEventType.CLIENT_CONNECTED,
    ClientLifecycleEventType.CLIENT_DISCONNECTED,
    ClientLifecycleEventType.CLIENT_SUBSCRIBED,
    ClientLifecycleEventType.CLIENT_UNSUBSCRIBED,
    ClientLifecycleEventType.CLIENT_AUTHENTICATION_FAILED,
    ClientLifecycleEventType.CLIENT_AUTHORIZATION_FAILED,
    ClientLifecycleEventType.CLIENT_CONNECTION_FAILED,
  ].map(value => ({value, name: this.translate.instant(this.eventTypeTranslations[value])}));

  lifecycleEventsListFormGroup: UntypedFormGroup;

  eventTypeList: Array<EventTypeInfo> = [];
  filteredEventTypeList: Observable<Array<EventTypeInfo>>;

  placeholder = this.translate.instant('integration.lifecycle-event-types');
  secondaryPlaceholder = '+' + this.translate.instant('integration.lifecycle-event');

  searchText = '';

  disabled = false;

  private dirty = false;
  private propagateChange = (_value: ClientLifecycleEventType[]) => {};

  constructor(public translate: TranslateService,
              private fb: UntypedFormBuilder) {
    this.lifecycleEventsListFormGroup = this.fb.group({
      lifecycleEventsList: [this.eventTypeList],
      eventType: [null]
    });
  }

  ngOnInit() {
    this.filteredEventTypeList = this.lifecycleEventsListFormGroup.get('eventType').valueChanges.pipe(
      tap((value: EventTypeInfo | string) => {
        if (value && typeof value !== 'string') {
          this.add(value);
        } else if (value === null) {
          this.clear(this.eventTypeInput().nativeElement.value);
        }
      }),
      filter((value) => typeof value === 'string'),
      map((value: string) => value ? value : ''),
      mergeMap(name => this.fetchEventTypes(name)),
      share()
    );
  }

  registerOnChange(fn: (value: ClientLifecycleEventType[]) => void): void {
    this.propagateChange = fn;
  }

  registerOnTouched(): void {}

  setDisabledState(isDisabled: boolean): void {
    this.disabled = isDisabled;
    if (isDisabled) {
      this.lifecycleEventsListFormGroup.disable({emitEvent: false});
    } else {
      this.lifecycleEventsListFormGroup.enable({emitEvent: false});
    }
  }

  writeValue(value: ClientLifecycleEventType[] | null): void {
    this.searchText = '';
    this.eventTypeList = [];
    (value ?? []).forEach(eventType => {
      const info = this.allEventTypeList.find(item => item.value === eventType);
      if (info) {
        this.eventTypeList.push(info);
      }
    });
    this.lifecycleEventsListFormGroup.get('lifecycleEventsList').setValue(this.eventTypeList);
    this.dirty = true;
  }

  add(eventType: EventTypeInfo): void {
    if (this.eventTypeList.findIndex(info => info.value === eventType.value) === -1) {
      this.eventTypeList.push(eventType);
      this.lifecycleEventsListFormGroup.get('lifecycleEventsList').setValue(this.eventTypeList);
      this.notifyValueChanged();
    }
    this.clear();
  }

  remove(eventType: EventTypeInfo): void {
    const index = this.eventTypeList.indexOf(eventType);
    if (index >= 0) {
      this.eventTypeList.splice(index, 1);
      this.lifecycleEventsListFormGroup.get('lifecycleEventsList').setValue(this.eventTypeList);
      this.notifyValueChanged();
      this.clear();
    }
  }

  displayEventTypeFn(eventType?: EventTypeInfo): string | undefined {
    return eventType ? eventType.name : undefined;
  }

  onFocus() {
    if (this.dirty) {
      this.lifecycleEventsListFormGroup.get('eventType').updateValueAndValidity({onlySelf: true, emitEvent: true});
      this.dirty = false;
    }
  }

  private notifyValueChanged() {
    this.propagateChange(this.eventTypeList.map(info => info.value));
  }

  private fetchEventTypes(searchText?: string): Observable<Array<EventTypeInfo>> {
    this.searchText = searchText;
    const selected = new Set(this.eventTypeList.map(info => info.value));
    let result = this.allEventTypeList.filter(info => !selected.has(info.value));
    if (searchText && searchText.length) {
      result = result.filter(info => info.name.toLowerCase().includes(searchText.toLowerCase()));
    }
    return of(result);
  }

  private clear(value: string = '') {
    this.eventTypeInput().nativeElement.value = value;
    this.lifecycleEventsListFormGroup.get('eventType').patchValue(value, {emitEvent: true});
    setTimeout(() => {
      this.eventTypeInput().nativeElement.blur();
      this.eventTypeInput().nativeElement.focus();
    }, 0);
  }
}
