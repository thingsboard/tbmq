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

import { ChangeDetectorRef, Component, EventEmitter, Inject, Output } from '@angular/core';
import { FormBuilder, FormGroup, Validators } from '@angular/forms';
import { Store } from '@ngrx/store';
import { AppState } from '@core/core.state';
import { EntityComponent } from '@home/components/entity/entity.component';
import { EntityTableConfig } from '@home/models/entity/entities-table-config.models';
import {
  credentialsTypeNames,
  MqttClientCredentials,
  MqttCredentialsType
} from '@shared/models/client-crenetials.model';
import { ClientType, clientTypeTranslationMap } from '@shared/models/client.model';
import { ActionNotificationShow } from '@core/notification/notification.actions';
import { TranslateService } from '@ngx-translate/core';

@Component({
  selector: 'tb-mqtt-client-credentials',
  templateUrl: './mqtt-client-credentials.component.html',
  styleUrls: ['./mqtt-client-credentials.component.scss']
})
export class MqttClientCredentialsComponent extends EntityComponent<MqttClientCredentials> {

  @Output()
  changePasswordCloseDialog = new EventEmitter<MqttClientCredentials>();

  credentialsType = MqttCredentialsType;
  credentialsTypes = Object.values(MqttCredentialsType);
  credentialsTypeTranslationMap = credentialsTypeNames;
  clientTypes = Object.values(ClientType);

  ClientType = ClientType;
  clientTypeTranslationMap = clientTypeTranslationMap;

  constructor(protected store: Store<AppState>,
              @Inject('entity') protected entityValue: MqttClientCredentials,
              @Inject('entitiesTableConfig') protected entitiesTableConfigValue: EntityTableConfig<MqttClientCredentials>,
              public fb: FormBuilder,
              protected cd: ChangeDetectorRef,
              private translate: TranslateService) {
    super(store, fb, entityValue, entitiesTableConfigValue, cd);
  }

  hideDelete() {
    if (this.entitiesTableConfig) {
      return !this.entitiesTableConfig.deleteEnabled(this.entity);
    } else {
      return false;
    }
  }

  buildForm(entity: MqttClientCredentials): FormGroup {
    const form = this.fb.group(
      {
        name: [entity ? entity.name : null, [Validators.required]],
        clientType: [entity ? entity.clientType : null, [Validators.required]],
        credentialsType: [entity ? entity.credentialsType : null, [Validators.required]],
        credentialsValue: [entity ? entity.credentialsValue : null]
      }
    );
    form.get('clientType').setValue(ClientType.DEVICE, {emitEvent: true});
    form.get('credentialsType').setValue(MqttCredentialsType.MQTT_BASIC, {emitEvent: true});
    form.get('credentialsType').valueChanges.subscribe(() => {
      form.patchValue({credentialsValue: null});
    });
    return form;
  }

  updateForm(entity: MqttClientCredentials) {
    this.entityForm.patchValue({name: entity.name});
    this.entityForm.patchValue({credentialsType: entity.credentialsType});
    this.entityForm.patchValue({credentialsValue: entity.credentialsValue});
    this.entityForm.patchValue({clientType: entity.clientType});
  }

  onChangePasswordCloseDialog($event: MqttClientCredentials) {
    this.updateForm($event);
  }

  onIdCopied() {
    this.store.dispatch(new ActionNotificationShow(
      {
        message: this.translate.instant('mqtt-client-credentials.id-copied-message'),
        type: 'success',
        duration: 750,
        verticalPosition: 'bottom',
        horizontalPosition: 'right'
      }));
  }

  onClientCredentialsCopied() {
    this.store.dispatch(new ActionNotificationShow(
      {
        message: this.translate.instant('mqtt-client-credentials.client-credentials-id-copied-message'),
        type: 'success',
        duration: 750,
        verticalPosition: 'bottom',
        horizontalPosition: 'right'
      }));
  }
}
