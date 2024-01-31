///
/// Copyright © 2016-2023 The Thingsboard Authors
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

import { Component, ElementRef, EventEmitter, forwardRef, Input, NgZone, OnInit, Output, ViewChild } from '@angular/core';
import { ControlValueAccessor, NG_VALUE_ACCESSOR, UntypedFormBuilder, UntypedFormGroup } from '@angular/forms';
import { Observable, of } from 'rxjs';
import { PageLink } from '@shared/models/page/page-link';
import { Direction } from '@shared/models/page/sort-order';
import { catchError, debounceTime, distinctUntilChanged, map, share, switchMap, tap } from 'rxjs/operators';
import { Store } from '@ngrx/store';
import { AppState } from '@app/core/core.state';
import { TranslateService } from '@ngx-translate/core';
import { TruncatePipe } from '@shared//pipe/truncate.pipe';
import { ENTER } from '@angular/cdk/keycodes';
import { MatDialog } from '@angular/material/dialog';
import { MatAutocomplete } from '@angular/material/autocomplete';
import { emptyPageData } from '@shared/models/page/page-data';
import { SubscriptSizing } from '@angular/material/form-field';
import { coerceBoolean } from '@shared/decorators/coercion';
import { ClientCredentials, CredentialsType } from '@shared/models/credentials.model';
import { ClientCredentialsService } from '@core/http/client-credentials.service';
import { Connection } from '@shared/models/ws-client.model';
import { isDefinedAndNotNull } from '@core/utils';

@Component({
  selector: 'tb-client-credentials-autocomplete',
  templateUrl: './client-credentials-autocomplete.component.html',
  styleUrls: ['./client-credentials-autocomplete.component.scss'],
  providers: [{
    provide: NG_VALUE_ACCESSOR,
    useExisting: forwardRef(() => ClientCredentialsAutocompleteComponent),
    multi: true
  }]
})
export class ClientCredentialsAutocompleteComponent implements ControlValueAccessor, OnInit {

  selectCredentialsFormGroup: UntypedFormGroup;

  modelValue: ClientCredentials | null;

  @Input()
  entity: Connection;

  @Input()
  subscriptSizing: SubscriptSizing = 'fixed';

  @Input()
  selectDefaultProfile = false;

  @Input()
  selectFirstProfile = false;

  @Input()
  displayAllOnEmpty = false;

  @Input()
  editProfileEnabled = true;

  @Input()
  addNewProfile = true;

  @Input()
  showDetailsPageLink = false;

  @Input()
  @coerceBoolean()
  required = false;

  @Input()
  disabled: boolean;

  @Input()
  hint: string;

  @Output()
  clientCredentialsUpdated = new EventEmitter<ClientCredentials>();

  @Output()
  clientCredentialsChanged = new EventEmitter<ClientCredentials>();

  @ViewChild('clientCredentialsInput', {static: true})
  clientCredentialsInput: ElementRef;

  @ViewChild('clientCredentialsAutocomplete', {static: true})
  clientCredentialsAutocomplete: MatAutocomplete;

  filteredClientCredentials: Observable<Array<ClientCredentials>>;

  searchText = '';

  private dirty = false;

  private ignoreClosedPanel = false;

  private propagateChange = (v: any) => { };

  constructor(private store: Store<AppState>,
              public translate: TranslateService,
              public truncate: TruncatePipe,
              private clientCredentialsService: ClientCredentialsService,
              private fb: UntypedFormBuilder,
              private zone: NgZone,
              private dialog: MatDialog) {
    this.selectCredentialsFormGroup = this.fb.group({
      clientCredentials: [null]
    });
  }

  registerOnChange(fn: any): void {
    this.propagateChange = fn;
  }

  registerOnTouched(fn: any): void {
  }

  ngOnInit() {
    this.filteredClientCredentials = this.selectCredentialsFormGroup.get('clientCredentials').valueChanges
      .pipe(
        tap((value: ClientCredentials | string) => {
          let modelValue: ClientCredentials | null;
          if (typeof value === 'string' || !value) {
            modelValue = null;
          } else {
            modelValue = value;
          }
          if (!this.displayAllOnEmpty || modelValue) {
            this.updateView(modelValue);
          }
        }),
        map(value => {
          if (value) {
            if (typeof value === 'string') {
              return value;
            } else {
              if (this.displayAllOnEmpty) {
                return '';
              } else {
                return value.name;
              }
            }
          } else {
            return '';
          }
        }),
        debounceTime(150),
        distinctUntilChanged(),
        switchMap(name => this.fetchClientCredentials(name)),
        share()
      );
  }

  setDisabledState(isDisabled: boolean): void {
    this.disabled = isDisabled;
    if (this.disabled) {
      this.selectCredentialsFormGroup.disable({emitEvent: false});
    } else {
      this.selectCredentialsFormGroup.enable({emitEvent: false});
    }
  }

  writeValue(value: ClientCredentials | null): void {
    this.searchText = '';
    if (value != null) {
      this.clientCredentialsService.getClientCredentials(value.id).subscribe(
        (credentials) => {
          this.modelValue = credentials;
          this.selectCredentialsFormGroup.get('clientCredentials').patchValue(credentials, {emitEvent: false});
          this.clientCredentialsChanged.emit(credentials);
        }
      );
    } else if (isDefinedAndNotNull(this.entity?.clientCredentialsId)) {
      this.modelValue = null;
      this.clientCredentialsService.getClientCredentials(this.entity.clientCredentialsId).subscribe(
        (credentials) => {
          this.selectCredentialsFormGroup.get('clientCredentials').patchValue(credentials, {emitEvent: false});
          this.clientCredentialsChanged.emit(credentials);
        }
      );
    } else {
      this.modelValue = null;
      this.selectCredentialsFormGroup.get('clientCredentials').patchValue(null, {emitEvent: false});
    }
    this.dirty = true;
  }

  onFocus() {
    if (this.dirty) {
      this.selectCredentialsFormGroup.get('clientCredentials').updateValueAndValidity({onlySelf: true, emitEvent: true});
      this.dirty = false;
    }
  }

  onPanelClosed() {
    if (this.ignoreClosedPanel) {
      this.ignoreClosedPanel = false;
    } else {
      if (this.displayAllOnEmpty && !this.selectCredentialsFormGroup.get('clientCredentials').value) {
        this.zone.run(() => {
        }, 0);
      }
    }
  }

  updateView(value: ClientCredentials | null) {
    if (value?.id) {
      this.clientCredentialsService.getClientCredentials(value.id).subscribe(
        (credentials) => {
          this.propagateChange(credentials);
          this.clientCredentialsChanged.emit(credentials);
        }
      );
    } else {
      this.propagateChange(null);
      this.clientCredentialsChanged.emit(null);
    }
  }

  displayClientCredentialsFn(credentials?: ClientCredentials): string | undefined {
    return credentials ? credentials.name : undefined;
  }

  fetchClientCredentials(searchText?: string): Observable<Array<ClientCredentials>> {
    this.searchText = searchText;
    const pageLink = new PageLink(10, 0, searchText, {
      property: 'name',
      direction: Direction.ASC
    });
    return this.clientCredentialsService.getClientsCredentials(pageLink, {ignoreLoading: true}).pipe(
      catchError(() => of(emptyPageData<ClientCredentials>())),
      map(pageData => {
        let data = pageData.data;
        let basicCredentials = data.filter(el => el.credentialsType === CredentialsType.MQTT_BASIC);
        return basicCredentials;
      })
    );
  }

  clear() {
    this.ignoreClosedPanel = true;
    this.selectCredentialsFormGroup.get('clientCredentials').patchValue(null, {emitEvent: true});
    setTimeout(() => {
      this.clientCredentialsInput.nativeElement.blur();
      this.clientCredentialsInput.nativeElement.focus();
    }, 0);
  }

  textIsNotEmpty(text: string): boolean {
    return (text && text.length > 0);
  }

  clientCredentialsEnter($event: KeyboardEvent) {
    if (this.editProfileEnabled && $event.keyCode === ENTER) {
      $event.preventDefault();
      if (!this.modelValue) {
        this.createClientCredentials($event, this.searchText);
      }
    }
  }

  createClientCredentials($event: Event, credentialsName: string) {
    $event.preventDefault();
    const clientCredentials: ClientCredentials = {
      name: credentialsName
    } as ClientCredentials;
    if (this.addNewProfile) {
      this.openClientCredentialsDialog(clientCredentials, true);
    }
  }

  editClientCredentials($event: Event) {
    $event.preventDefault();
    this.clientCredentialsService.getClientCredentials(this.modelValue.id).subscribe(
      (clientCredentials) => {
        this.openClientCredentialsDialog(clientCredentials, false);
      }
    );
  }

  openClientCredentialsDialog(clientCredentials: ClientCredentials, isAdd: boolean) {
  }
}
