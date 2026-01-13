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

import { Component, ElementRef, forwardRef, NgZone, OnInit, input, model, viewChild } from '@angular/core';
import { ControlValueAccessor, NG_VALUE_ACCESSOR, UntypedFormBuilder, UntypedFormGroup, FormsModule, ReactiveFormsModule } from '@angular/forms';
import { Observable, of } from 'rxjs';
import { PageLink } from '@shared/models/page/page-link';
import { Direction } from '@shared/models/page/sort-order';
import { catchError, debounceTime, distinctUntilChanged, map, share, switchMap, tap } from 'rxjs/operators';
import { TranslateService, TranslateModule } from '@ngx-translate/core';
import { TruncatePipe } from '@shared//pipe/truncate.pipe';
import { ENTER } from '@angular/cdk/keycodes';
import { MatAutocomplete, MatAutocompleteTrigger } from '@angular/material/autocomplete';
import { emptyPageData } from '@shared/models/page/page-data';
import { SubscriptSizing, MatFormField, MatLabel, MatSuffix, MatHint } from '@angular/material/form-field';
import { ClientCredentials, CredentialsType, wsSystemCredentialsName } from '@shared/models/credentials.model';
import { ClientCredentialsService } from '@core/http/client-credentials.service';
import { WebSocketConnection } from '@shared/models/ws-client.model';
import {
  ClientCredentialsWizardDialogComponent
} from '@home/components/wizard/client-credentials-wizard-dialog.component';
import { AddEntityDialogData } from '@home/models/entity/entity-component.models';
import { MatDialog } from '@angular/material/dialog';
import { EntityTableConfig } from '@home/models/entity/entities-table-config.models';
import { EntityType, entityTypeResources, entityTypeTranslations } from '@shared/models/entity-type.models';
import { ClientCredentialsComponent } from '@home/pages/client-credentials/client-credentials.component';
import { ClientType } from '@shared/models/client.model';
import { ActionNotificationShow } from '@core/notification/notification.actions';
import { Store } from '@ngrx/store';
import { AppState } from '@core/core.state';
import { MatInput } from '@angular/material/input';
import { AsyncPipe } from '@angular/common';
import { MatIconButton, MatButton } from '@angular/material/button';
import { MatIcon } from '@angular/material/icon';
import { EditClientCredentialsButtonComponent } from '@shared/components/button/edit-client-credentials-button.component';
import { MatOption } from '@angular/material/core';
import { HighlightPipe } from '@shared/pipe/highlight.pipe';
import { MatTooltip } from '@angular/material/tooltip';

@Component({
    selector: 'tb-client-credentials-autocomplete',
    templateUrl: './client-credentials-autocomplete.component.html',
    styleUrls: ['./client-credentials-autocomplete.component.scss'],
    providers: [
      {
        provide: NG_VALUE_ACCESSOR,
        useExisting: forwardRef(() => ClientCredentialsAutocompleteComponent),
        multi: true
      },
      TruncatePipe
    ],
    imports: [MatFormField, FormsModule, ReactiveFormsModule, MatLabel, MatInput, MatAutocompleteTrigger, MatIconButton, MatSuffix, MatIcon, EditClientCredentialsButtonComponent, MatAutocomplete, MatOption, TranslateModule, MatHint, AsyncPipe, HighlightPipe, MatButton, MatTooltip]
})
export class ClientCredentialsAutocompleteComponent implements ControlValueAccessor, OnInit {

  disabled = model<boolean>();
  readonly entity = input<WebSocketConnection>();
  readonly subscriptSizing = input<SubscriptSizing>('fixed');
  readonly selectDefaultProfile = input(false);
  readonly selectFirstProfile = input(false);
  readonly displayAllOnEmpty = input(false);
  readonly editEnabled = input(false);
  readonly addNewCredentials = input(true);
  readonly showDetailsPageLink = input(false);
  readonly required = input(false);
  readonly hint = input<string>();

  readonly clientCredentialsInput = viewChild<ElementRef>('clientCredentialsInput');
  readonly clientCredentialsAutocomplete = viewChild<MatAutocomplete>('clientCredentialsAutocomplete');

  selectCredentialsFormGroup: UntypedFormGroup;
  filteredClientCredentials: Observable<Array<ClientCredentials>>;
  searchText = '';
  private modelValue: ClientCredentials | null;
  private dirty = false;
  private ignoreClosedPanel = false;
  private propagateChange = (v: any) => {
  };

  constructor(public translate: TranslateService,
              public truncate: TruncatePipe,
              private dialog: MatDialog,
              private store: Store<AppState>,
              private clientCredentialsService: ClientCredentialsService,
              private fb: UntypedFormBuilder,
              private zone: NgZone) {
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
          if (!this.displayAllOnEmpty() || modelValue) {
            this.updateView(modelValue?.id);
          }
        }),
        map(value => {
          if (value) {
            if (typeof value === 'string') {
              return value;
            } else {
              if (this.displayAllOnEmpty()) {
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
    this.disabled.set(isDisabled);
    if (this.disabled()) {
      this.selectCredentialsFormGroup.disable({emitEvent: false});
    } else {
      this.selectCredentialsFormGroup.enable({emitEvent: false});
    }
  }

  writeValue(credentialsId: string): void {
    this.searchText = '';
    this.dirty = true;
    this.updateView(credentialsId, true);
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
      if (this.displayAllOnEmpty() && !this.selectCredentialsFormGroup.get('clientCredentials').value) {
        this.zone.run(() => {
        }, 0);
      }
    }
  }

  updateView(credentialsId: string, useDefaultCredentials = false) {
    if (credentialsId) {
      this.clientCredentialsService.getClientCredentials(credentialsId, {ignoreErrors: true}).subscribe(
        (credentials) => {
          this.selectCredentialsFormGroup.get('clientCredentials').patchValue(credentials, {emitEvent: false});
          this.propagateChange(credentials);
        }
      );
    } else {
      if (useDefaultCredentials) {
        this.clientCredentialsService.getClientsCredentials(new PageLink(1, 0, wsSystemCredentialsName), {ignoreLoading: true, ignoreErrors: true}).subscribe(
          pageData => {
            const wsSystemCredentials = pageData.data.length ? pageData.data[0] : null;
            if (wsSystemCredentials) {
              this.selectCredentialsFormGroup.get('clientCredentials').patchValue(wsSystemCredentials, {emitEvent: true});
              this.propagateChange(wsSystemCredentials);
            }
          }
        );
      } else {
        this.propagateChange(null);
      }
    }
  }

  displayClientCredentialsFn(credentials?: ClientCredentials): string | undefined {
    return credentials ? credentials.name : undefined;
  }

  fetchClientCredentials(searchText?: string): Observable<Array<ClientCredentials>> {
    this.searchText = searchText;
    const pageLink = new PageLink(100, 0, searchText, {
      property: 'name',
      direction: Direction.ASC
    });
    return this.clientCredentialsService.getClientsCredentials(pageLink, {ignoreLoading: true}).pipe(
      catchError(() => of(emptyPageData<ClientCredentials>())),
      map(pageData => {
        const basicCredentials = pageData.data.filter(el => el.credentialsType === CredentialsType.MQTT_BASIC);
        return basicCredentials;
      })
    );
  }

  clear() {
    this.ignoreClosedPanel = true;
    this.selectCredentialsFormGroup.get('clientCredentials').patchValue(null, {emitEvent: true});
    setTimeout(() => {
      this.clientCredentialsInput().nativeElement.blur();
      this.clientCredentialsInput().nativeElement.focus();
    }, 0);
  }

  textIsNotEmpty(text: string): boolean {
    return (text && text.length > 0);
  }

  clientCredentialsEnter($event: KeyboardEvent) {
    if (this.editEnabled() && $event.keyCode === ENTER) {
      $event.preventDefault();
      if (!this.modelValue) {
        this.createClientCredentials($event, this.searchText);
      }
    }
  }

  createClientCredentials($event: Event, credentialsName: string) {
    if ($event) {
      $event.stopPropagation();
      $event.preventDefault();
    }
    const clientCredentials: ClientCredentials = {
      name: credentialsName
    } as ClientCredentials;
    if (this.addNewCredentials()) {
      this.addClientCredentials(clientCredentials);
    }
  }

  addClientCredentials(clientCredentials: ClientCredentials) {
    const config = new EntityTableConfig<ClientCredentials>();
    config.entityType = EntityType.MQTT_CLIENT_CREDENTIALS;
    config.entityComponent = ClientCredentialsComponent;
    config.entityTranslations = entityTypeTranslations.get(EntityType.MQTT_CLIENT_CREDENTIALS);
    config.entityResources = entityTypeResources.get(EntityType.MQTT_CLIENT_CREDENTIALS);
    config.addDialogStyle = {width: 'fit-content'};
    config.demoData = {
      name: clientCredentials.name,
      clientType: ClientType.DEVICE,
      credentialsType: CredentialsType.MQTT_BASIC,
      credentialsValue: JSON.stringify({
        userName: null,
        password: null,
        authRules: {
          pubAuthRulePatterns: ['.*'],
          subAuthRulePatterns: ['.*']
        }
      })
    };
    const $entity = this.dialog.open<ClientCredentialsWizardDialogComponent, AddEntityDialogData<ClientCredentials>,
      ClientCredentials>(ClientCredentialsWizardDialogComponent, {
      disableClose: true,
      panelClass: ['tb-dialog', 'tb-fullscreen-dialog'],
      autoFocus: false,
      data: {
        entitiesTableConfig: config
      }
    }).afterClosed();
    $entity.subscribe(
      (entity) => {
        if (entity) {
          this.writeValue(entity.id);
          this.store.dispatch(new ActionNotificationShow(
            {
              message: this.translate.instant('getting-started.credentials-added'),
              type: 'success',
              duration: 1500,
              verticalPosition: 'top',
              horizontalPosition: 'left'
            }
          ));
        }
      }
    );
  }
}
