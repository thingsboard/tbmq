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
import {
  ClientCredentials,
  CredentialsType,
  CredentialsTypes,
  credentialsTypeTranslationMap
} from '@shared/models/credentials.model';
import { ClientType, clientTypeTranslationMap } from '@shared/models/client.model';
import { MatFormField, MatLabel } from '@angular/material/form-field';
import { isDefinedAndNotNull } from '@core/utils';
import { MatButton } from '@angular/material/button';
import { MatIcon } from '@angular/material/icon';
import { TranslateModule } from '@ngx-translate/core';
import { CopyContentButtonComponent } from '@shared/components/button/copy-content-button.component';
import { MatInput } from '@angular/material/input';
import { AsyncPipe } from '@angular/common';
import { MatSelect } from '@angular/material/select';
import { MatOption } from '@angular/material/core';
import { MqttCredentialsBasicComponent } from '@home/components/client-credentials-templates/basic/basic.component';
import { MqttCredentialsSslComponent } from '@home/components/client-credentials-templates/ssl/ssl.component';
import { MqttCredentialsScramComponent } from '@home/components/client-credentials-templates/scram/scram.component';
import { MatTooltip } from '@angular/material/tooltip';

@Component({
    selector: 'tb-client-credentials',
    templateUrl: './client-credentials.component.html',
    styleUrls: ['./client-credentials.component.scss'],
    imports: [MatButton, MatIcon, TranslateModule, CopyContentButtonComponent, FormsModule, ReactiveFormsModule, MatFormField, MatLabel, MatInput, MatSelect, MatOption, MqttCredentialsBasicComponent, MqttCredentialsSslComponent, MqttCredentialsScramComponent, AsyncPipe, MatTooltip]
})
export class ClientCredentialsComponent extends EntityComponent<ClientCredentials> {

  credentialsType = CredentialsType;
  credentialsTypes = CredentialsTypes;
  credentialsTypeTranslationMap = credentialsTypeTranslationMap;
  clientTypes = Object.values(ClientType);

  ClientType = ClientType;
  clientTypeTranslationMap = clientTypeTranslationMap;

  constructor(protected store: Store<AppState>,
              @Inject('entity') protected entityValue: ClientCredentials,
              @Inject('entitiesTableConfig') protected entitiesTableConfigValue: EntityTableConfig<ClientCredentials>,
              public fb: UntypedFormBuilder,
              protected cd: ChangeDetectorRef) {
    super(store, fb, entityValue, entitiesTableConfigValue, cd);
  }

  hideDelete() {
    if (this.entitiesTableConfig) {
      return !this.entitiesTableConfig.deleteEnabled(this.entity);
    } else {
      return false;
    }
  }

  hideChangePassword() {
    if (this.entitiesTableConfig) {
      return this.entity?.credentialsType !== CredentialsType.MQTT_BASIC;
    } else {
      return false;
    }
  }

  buildForm(entity: ClientCredentials): UntypedFormGroup {
    const form = this.fb.group(
      {
        name: [entity ? entity.name : null, [Validators.required]],
        clientType: [entity ? entity.clientType : null, [Validators.required]],
        credentialsType: [entity ? entity.credentialsType : null, [Validators.required]],
        credentialsValue: [entity ? entity.credentialsValue : null, []],
        additionalInfo: this.fb.group(
          {
            description: [entity && entity.additionalInfo ? entity.additionalInfo.description : '']
          }
        )
      }
    );
    form.patchValue({
      clientType: ClientType.DEVICE,
      credentialsType: CredentialsType.MQTT_BASIC
    });
    form.get('credentialsType').valueChanges.subscribe(() => {
      form.patchValue({credentialsValue: null});
    });
    if (isDefinedAndNotNull(this.entitiesTableConfigValue.demoData)) {
      for (const [key, value] of Object.entries(this.entitiesTableConfigValue.demoData)) {
        form.patchValue({
          [key]: value
        });
      }
    }
    return form;
  }

  updateForm(entity: ClientCredentials) {
    this.entityForm.patchValue({name: entity.name});
    this.entityForm.patchValue({credentialsType: entity.credentialsType});
    this.entityForm.patchValue({credentialsValue: entity.credentialsValue});
    this.entityForm.patchValue({clientType: entity.clientType});
    this.entityForm.patchValue({additionalInfo: {description: entity.additionalInfo ? entity.additionalInfo.description : ''}});
  }

  showConnectivityDialog() {
    return !this.isEdit && this.entity?.credentialsType === CredentialsType.MQTT_BASIC;
  }
}

export class BasicClientCredentials {
  credentialsType = CredentialsType.MQTT_BASIC;
  clientType = ClientType.DEVICE;
  name: string;
  credentialsValue: any = {};

  constructor(name: string,
              clientId: string,
              username: string) {
    this.name = name;
    this.credentialsValue = JSON.stringify({
      clientId,
      userName: username,
      password: null,
      authRules: {
        pubAuthRulePatterns: ['.*'],
        subAuthRulePatterns: ['.*']
      }
    });
  }
}
