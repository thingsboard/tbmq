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

import { ChangeDetectorRef, Component, Inject } from '@angular/core';
import { UntypedFormBuilder, UntypedFormGroup, Validators, FormsModule, ReactiveFormsModule } from '@angular/forms';
import { Store } from '@ngrx/store';
import { AppState } from '@core/core.state';
import { EntityComponent } from '@home/components/entity/entity.component';
import { EntityTableConfig } from '@home/models/entity/entities-table-config.models';
import { User } from '@shared/models/user.model';
import { getCurrentAuthUser } from '@core/auth/auth.selectors';
import { MatButton } from '@angular/material/button';
import { MatIcon } from '@angular/material/icon';
import { TranslateModule } from '@ngx-translate/core';
import { CopyContentButtonComponent } from '@shared/components/button/copy-content-button.component';
import { MatFormField, MatLabel, MatSuffix } from '@angular/material/form-field';
import { MatInput } from '@angular/material/input';
import { AsyncPipe } from '@angular/common';
import { MatTooltip } from '@angular/material/tooltip';
import { AuthService } from '@core/http/auth.service';

@Component({
    selector: 'tb-user',
    templateUrl: './user.component.html',
    styleUrls: ['./user.component.scss'],
    imports: [MatButton, MatIcon, TranslateModule, CopyContentButtonComponent, FormsModule, ReactiveFormsModule, MatFormField, MatLabel, MatInput, AsyncPipe, MatSuffix, MatTooltip]
})
export class UserComponent extends EntityComponent<User> {

  currentUser = getCurrentAuthUser(this.store);
  loginAsUserEnabled$ = this.authService.loadIsUserTokenAccessEnabled(this.currentUser);

  private currentUserId = getCurrentAuthUser(this.store).userId;

  constructor(protected store: Store<AppState>,
              @Inject('entity') protected entityValue: User,
              @Inject('entitiesTableConfig') protected entitiesTableConfigValue: EntityTableConfig<User>,
              public fb: UntypedFormBuilder,
              public authService: AuthService,
              protected cd: ChangeDetectorRef) {
    super(store, fb, entityValue, entitiesTableConfigValue, cd);
  }

  hideDelete() {
    if (this.entitiesTableConfig) {
      return !this.entitiesTableConfig.deleteEnabled(this.entity) || this.currentUserId === this.entityValue?.id;
    } else {
      return false;
    }
  }

  buildForm(entity: User): UntypedFormGroup {
    const form = this.fb.group(
      {
        email: [entity ? entity.email : '', [Validators.required, Validators.email]],
        firstName: [entity ? entity.firstName : ''],
        lastName: [entity ? entity.lastName : ''],
        additionalInfo: this.fb.group(
          {
            description: [entity && entity.additionalInfo ? entity.additionalInfo.description : '']
          }
        )
      }
    );
    return form;
  }

  updateForm(entity: User) {
    this.entityForm.patchValue({email: entity.email});
    this.entityForm.patchValue({firstName: entity.firstName});
    this.entityForm.patchValue({lastName: entity.lastName});
    this.entityForm.patchValue({additionalInfo: {description: entity.additionalInfo ? entity.additionalInfo.description : ''}});
  }
}
