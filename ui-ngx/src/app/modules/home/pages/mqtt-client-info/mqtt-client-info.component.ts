///
/// Copyright © 2016-2020 The Thingsboard Authors
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
import { FormBuilder, FormGroup, Validators } from '@angular/forms';
import { Store } from '@ngrx/store';
import { AppState } from '@core/core.state';
import { Client, ClientType, clientTypeTranslationMap } from '@shared/models/mqtt.models';
import { EntityComponent } from '@home/components/entity/entity.component';
import { EntityTableConfig } from '@home/models/entity/entities-table-config.models';

@Component({
  selector: 'tb-mqtt-client-info',
  templateUrl: './mqtt-client-info.component.html',
  styleUrls: ['./mqtt-client-info.component.scss']
})
export class MqttClientInfoComponent extends EntityComponent<Client> {

  mqttClientTypes = Object.values(ClientType);

  defaultMqttClientType = ClientType.DEVICE;

  mqttClientTypeTranslationMap = clientTypeTranslationMap;

  constructor(protected store: Store<AppState>,
              @Inject('entity') protected entityValue: Client,
              @Inject('entitiesTableConfig') protected entitiesTableConfigValue: EntityTableConfig<Client>,
              public fb: FormBuilder,
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

  buildForm(entity: Client): FormGroup {
    return this.fb.group(
      {
        clientId: [entity ? entity.clientId : '', [Validators.required]],
        type: [entity ? entity.type : '', [Validators.required]],
        name: [entity ? entity.name : '']
      }
    );
  }

  updateForm(entity: Client) {
    this.entityForm.patchValue({
      clientId: entity.clientId,
      name: entity.name,
      type: entity.type
    });
  }

}
