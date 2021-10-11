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

import { ChangeDetectorRef, Component, Inject, Input } from '@angular/core';
import { FormBuilder, FormGroup } from '@angular/forms';
import { Store } from '@ngrx/store';
import { AppState } from '@core/core.state';
import { EntityComponent } from '@home/components/entity/entity.component';
import { EntityTableConfig } from '@home/models/entity/entities-table-config.models';
import { DatePipe } from '@angular/common';
import { TranslateService } from '@ngx-translate/core';
import { ClientType, clientTypeTranslationMap} from '@shared/models/mqtt-client.model';
import {
  ConnectionState,
  connectionStateColor,
  connectionStateTranslationMap, DetailedClientSessionInfo,
  TopicSubscription
} from '@shared/models/mqtt-session.model';
import { isNotNullOrUndefined } from 'codelyzer/util/isNotNullOrUndefined';

@Component({
  selector: 'tb-mqtt-clients',
  templateUrl: './mqtt-sessions.component.html'
})
export class MqttSessionsComponent extends EntityComponent<DetailedClientSessionInfo> {

  @Input() entityForm: FormGroup;

  sessionDetailsForm: FormGroup;

  mqttClientTypes = Object.values(ClientType);
  connectionStateTranslationMap = connectionStateTranslationMap;
  clientTypeTranslationMap = clientTypeTranslationMap;

  constructor(protected store: Store<AppState>,
              @Inject('entity') protected entityValue: DetailedClientSessionInfo,
              @Inject('entitiesTableConfig') protected entitiesTableConfigValue: EntityTableConfig<DetailedClientSessionInfo>,
              public fb: FormBuilder,
              protected cd: ChangeDetectorRef,
              private datePipe: DatePipe,
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

  buildForm(entity: DetailedClientSessionInfo): FormGroup {
    this.buildSessionDetailsForm(entity);
    return this.fb.group({
      clientId: [entity ? entity.clientId : null],
      clientType: [entity ? entity.clientType : null],
      nodeId: [entity ? entity.nodeId : null],
      keepAliveSeconds: [entity ? entity.keepAliveSeconds : null],
      connectedAt: [entity ? entity.connectedAt : null],
      connectionState: [entity ? entity.connectionState : null],
      persistent: [entity ? entity.persistent : null],
      disconnectedAt: [entity ? entity.disconnectedAt : null],
      subscriptions: [entity ? entity.subscriptions : null]
    });
  }

  updateForm(entity: DetailedClientSessionInfo) {
    this.entityForm.patchValue({clientId: entity.clientId}, {emitEvent: false} );
    this.entityForm.patchValue({clientType: entity.clientType}, {emitEvent: false} );
    this.entityForm.patchValue({nodeId: entity.nodeId}, {emitEvent: false} );
    this.entityForm.patchValue({keepAliveSeconds: entity.keepAliveSeconds}, {emitEvent: false} );
    this.entityForm.patchValue({connectedAt: entity.connectedAt}, {emitEvent: false} );
    this.entityForm.patchValue({connectionState: entity.connectionState}, {emitEvent: false} );
    this.entityForm.patchValue({persistent: entity.persistent}, {emitEvent: false} );
    this.entityForm.patchValue({disconnectedAt: entity.disconnectedAt}, {emitEvent: false} );
    this.entityForm.patchValue({subscriptions: entity.subscriptions}, {emitEvent: false} );
    this.sessionDetailsForm.patchValue({cleanSession: !entity.persistent}, {emitEvent: false} );
    this.sessionDetailsForm.patchValue({subscriptionsCount: entity.subscriptions.length}, {emitEvent: false} );
  }

  updateFormState() {
    super.updateFormState();
    this.entityForm.get('clientId').disable({emitEvent: false});
    this.entityForm.get('clientType').disable({emitEvent: false});
    this.entityForm.get('nodeId').disable({emitEvent: false});
    this.entityForm.get('keepAliveSeconds').disable({emitEvent: false});
    this.entityForm.get('connectedAt').disable({emitEvent: false});
    this.entityForm.get('connectionState').disable({emitEvent: false});
    this.entityForm.get('persistent').disable({emitEvent: false});
    this.entityForm.get('disconnectedAt').disable({emitEvent: false});
    this.sessionDetailsForm.get('cleanSession').disable({emitEvent: false});
    this.sessionDetailsForm.get('subscriptionsCount').disable({emitEvent: false});
  }

  isConnected(): boolean {
    return this.entityForm.get('connectionState').value === ConnectionState.CONNECTED;
  }

  getColor(): {color: string} {
    return {color: connectionStateColor.get(this.entityForm.get('connectionState').value)};
  }

  getLength() {
    if (this.entityForm) {
      const subscriptions: TopicSubscription[] = this.entityForm.get('subscriptions').value;
      return isNotNullOrUndefined(subscriptions) ? subscriptions.length : 0;
    } else {
      return;
    }

  }

  private buildSessionDetailsForm(entity: DetailedClientSessionInfo) {
    this.sessionDetailsForm = this.fb.group({
      cleanSession: [entity ? !entity.persistent : null],
      subscriptionsCount: [entity ? entity.subscriptions.length : null]
    });
  }

}
