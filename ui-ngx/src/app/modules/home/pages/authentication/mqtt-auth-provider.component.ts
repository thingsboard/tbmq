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
import { UntypedFormBuilder, UntypedFormGroup, Validators, FormsModule, ReactiveFormsModule } from '@angular/forms';
import { Store } from '@ngrx/store';
import { AppState } from '@core/core.state';
import { EntityComponent } from '@home/components/entity/entity.component';
import { EntityTableConfig } from '@home/models/entity/entities-table-config.models';
import { MatFormField, MatLabel } from '@angular/material/form-field';
import { TranslateModule, TranslateService } from '@ngx-translate/core';
import { AsyncPipe } from '@angular/common';
import {
  MqttAuthProvider,
  MqttAuthProviderType,
  mqttAuthProviderTypeTranslationMap
} from '@shared/models/mqtt-auth-provider.model';
import { MatSlideToggle } from '@angular/material/slide-toggle';
import {
  MqttAuthenticationProviderConfigurationComponent
} from '@home/components/authentication/configuration/mqtt-authentication-provider-configuration.component';
import { MatOption } from '@angular/material/core';
import { MatSelect } from '@angular/material/select';

@Component({
    selector: 'tb-mqtt-auth-provider',
    templateUrl: './mqtt-auth-provider.component.html',
    styleUrls: ['./mqtt-auth-provider.component.scss'],
    imports: [TranslateModule, FormsModule, ReactiveFormsModule, MatFormField, MatLabel, AsyncPipe, MatSlideToggle, MqttAuthenticationProviderConfigurationComponent, MatOption, MatSelect]
})
export class MqttAuthProviderComponent extends EntityComponent<MqttAuthProvider> {

  authProviderTypes = Object.values(MqttAuthProviderType);
  mqttAuthProviderTypeMap = mqttAuthProviderTypeTranslationMap;

  constructor(protected store: Store<AppState>,
              @Inject('entity') protected entityValue: MqttAuthProvider,
              @Inject('entitiesTableConfig') protected entitiesTableConfigValue: EntityTableConfig<MqttAuthProvider>,
              public fb: UntypedFormBuilder,
              private translate: TranslateService,
              protected cd: ChangeDetectorRef) {
    super(store, fb, entityValue, entitiesTableConfigValue, cd);
  }

  buildForm(entity: MqttAuthProvider): UntypedFormGroup {
    return this.fb.group(
      {
        enabled: [entity ? entity.enabled : null],
        type: [entity ? entity.type : null],
        description: [entity ? entity.description : null],
        configuration: [entity ? entity.configuration : null, [Validators.required]],
      }
    );
  }

  updateFormState() {
    super.updateFormState();
    this.entityForm.get('type').disable({ emitEvent: false });
  }

  updateForm(entity: MqttAuthProvider) {
    this.entityForm.patchValue({enabled: entity.enabled});
    this.entityForm.patchValue({type: entity.type});
    this.entityForm.patchValue({description: entity.description});
    this.entityForm.patchValue({configuration: entity.configuration});
    this.entityForm.updateValueAndValidity();
  }
}
