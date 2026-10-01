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
import { FormsModule, ReactiveFormsModule, UntypedFormBuilder, UntypedFormGroup, Validators } from '@angular/forms';
import { Store } from '@ngrx/store';
import { AppState } from '@core/core.state';
import { EntityComponent } from '@home/components/entity/entity.component';
import { EntityTableConfig } from '@home/models/entity/entities-table-config.models';
import { ANY_CHARACTERS, AuthorizationPolicy } from '@shared/models/credentials.model';
import { MatFormField, MatLabel } from '@angular/material/form-field';
import { MatInput } from '@angular/material/input';
import { TranslateModule } from '@ngx-translate/core';
import { TopicRulesChipListComponent } from '@shared/components/topic-rules-chip-list.component';

@Component({
  selector: 'tb-authorization-policy',
  templateUrl: './authorization-policy.component.html',
  imports: [FormsModule, ReactiveFormsModule, MatFormField, MatLabel, MatInput, TranslateModule,
    TopicRulesChipListComponent]
})
export class AuthorizationPolicyComponent extends EntityComponent<AuthorizationPolicy> {

  constructor(protected store: Store<AppState>,
              @Inject('entity') protected entityValue: AuthorizationPolicy,
              @Inject('entitiesTableConfig') protected entitiesTableConfigValue: EntityTableConfig<AuthorizationPolicy>,
              public fb: UntypedFormBuilder,
              protected cd: ChangeDetectorRef) {
    super(store, fb, entityValue, entitiesTableConfigValue, cd);
  }

  buildForm(entity: AuthorizationPolicy): UntypedFormGroup {
    return this.fb.group({
      name: [entity?.name, [Validators.required, Validators.maxLength(255)]],
      authorizationRules: this.fb.group({
        pubAuthRulePatterns: [entity?.authorizationRules?.pubAuthRulePatterns || [ANY_CHARACTERS]],
        subAuthRulePatterns: [entity?.authorizationRules?.subAuthRulePatterns || [ANY_CHARACTERS]]
      })
    });
  }

  updateForm(entity: AuthorizationPolicy) {
    this.entityForm.patchValue({
      name: entity.name,
      authorizationRules: entity.authorizationRules
    });
  }
}
