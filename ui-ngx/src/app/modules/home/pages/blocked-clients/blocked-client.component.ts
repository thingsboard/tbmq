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

import { ChangeDetectorRef, Component, Inject } from '@angular/core';
import { FormsModule, ReactiveFormsModule, UntypedFormBuilder, UntypedFormGroup, Validators } from '@angular/forms';
import { Store } from '@ngrx/store';
import { AppState } from '@core/core.state';
import { EntityComponent } from '@home/components/entity/entity.component';
import { EntityTableConfig } from '@home/models/entity/entities-table-config.models';
import { MatIcon } from '@angular/material/icon';
import { TranslateModule } from '@ngx-translate/core';
import { MatFormField, MatLabel, MatPrefix, MatSuffix } from '@angular/material/form-field';
import { MatInput } from '@angular/material/input';
import { AsyncPipe } from '@angular/common';
import { MatTooltip } from '@angular/material/tooltip';
import {
  BlockedClient,
  BlockedClientType,
  blockedClientTypeTranslationMap,
  blockedClientTypeValuePropertyMap,
  RegexMatchTarget,
  regexMatchTargetTranslationMap
} from '@shared/models/blocked-client.models';
import { MatOption } from '@angular/material/core';
import { MatSelect } from '@angular/material/select';
import { MatDatetimepickerModule, MatNativeDatetimeModule } from '@mat-datetimepicker/core';
import { MatSlideToggle } from '@angular/material/slide-toggle';

@Component({
    selector: 'tb-blocked-client',
    templateUrl: './blocked-client.component.html',
    styleUrls: ['./blocked-client.component.scss'],
    imports: [MatIcon, TranslateModule, FormsModule, ReactiveFormsModule, MatFormField, MatLabel, MatInput, AsyncPipe, MatSuffix, MatTooltip, MatOption, MatSelect, MatDatetimepickerModule, MatPrefix, MatNativeDatetimeModule, MatSlideToggle]
})
export class BlockedClientComponent extends EntityComponent<BlockedClient> {

  blockedClientTypes = Object.values(BlockedClientType);
  regexMatchTargets = Object.values(RegexMatchTarget);

  blockedClientTypeTranslationMap = blockedClientTypeTranslationMap;
  regexMatchTargetTranslationMap = regexMatchTargetTranslationMap;
  blockedClientTypeValuePropertyMap = blockedClientTypeValuePropertyMap;

  expirationDate = this.defaultExpirationDate();
  neverExpires = true;
  neverExpiresLabel: string;

  get isRegexType(): boolean {
    return this.entityForm.get('type').value === BlockedClientType.REGEX;
  }

  constructor(protected store: Store<AppState>,
              @Inject('entity') protected entityValue: BlockedClient,
              @Inject('entitiesTableConfig') protected entitiesTableConfigValue: EntityTableConfig<BlockedClient>,
              public fb: UntypedFormBuilder,
              protected cd: ChangeDetectorRef) {
    super(store, fb, entityValue, entitiesTableConfigValue, cd);
    this.onNeverExpiresChange();
  }

  buildForm(): UntypedFormGroup {
    const form = this.fb.group(
      {
        type: [BlockedClientType.CLIENT_ID, []],
        value: [null, [Validators.required]],
        regexMatchTarget: [RegexMatchTarget.BY_CLIENT_ID, []],
        description: [null],
        expirationTime: [null],
      }
    );
    form.get('type').valueChanges.subscribe(() => this.updateValidators());
    return form;
  }

  prepareFormValue(formValue: BlockedClient): any {
    const valueProperty = this.blockedClientTypeValuePropertyMap.get(formValue.type);
    formValue[valueProperty] = formValue.value;
    delete formValue.value;

    formValue.expirationTime = this.neverExpires ? 0 : this.expirationDate.getTime();
    return formValue;
  }

  updateForm() {}

  updateFormState() {
    if (this.entityForm) {
      if (this.isEditValue) {
        this.entityForm.enable({emitEvent: false});
      } else {
        this.entityForm.disable({emitEvent: false});
      }
      this.updateValidators();
    }
  }

  onExpirationDateChange() {
    if (this.expirationDate) {
      if (this.expirationDate.getTime() <= Date.now()) {
        this.expirationDate = this.defaultExpirationDate();
      }
    }
  }

  onNeverExpiresChange() {
    this.neverExpiresLabel = this.neverExpires ? 'blocked-client.expires-never' : 'blocked-client.expires';
  }

  private updateValidators() {
    const type = this.entityForm.get('type').value;
    switch (type) {
      case BlockedClientType.REGEX:
        this.entityForm.get('regexMatchTarget').enable({onlySelf: true});
        break;
      default:
        this.entityForm.get('regexMatchTarget').disable({onlySelf: true});
        break;
    }
    this.entityForm.updateValueAndValidity();
  }

  private defaultExpirationDate(): Date {
    const date = new Date();
    return  new Date(
      date.getFullYear(),
      date.getMonth()  + 1,
      date.getDate() - 1,
      date.getHours(),
      date.getMinutes(),
      date.getSeconds(),
      date.getMilliseconds()
    );
  }
}
