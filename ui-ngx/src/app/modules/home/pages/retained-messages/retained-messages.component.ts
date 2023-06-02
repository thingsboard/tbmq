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

import { ChangeDetectorRef, Component, Inject } from '@angular/core';
import { FormBuilder, FormGroup } from '@angular/forms';
import { Store } from '@ngrx/store';
import { AppState } from '@core/core.state';
import { EntityComponent } from '@home/components/entity/entity.component';
import { EntityTableConfig } from '@home/models/entity/entities-table-config.models';
import { RetainedMessage } from '@shared/models/retained-message.model';

@Component({
  selector: 'tb-mqtt-retained-messages',
  templateUrl: './retained-messages.component.html',
  styleUrls: ['./retained-messages.component.scss']
})
export class RetainedMessagesComponent extends EntityComponent<RetainedMessage> {

  constructor(protected store: Store<AppState>,
              @Inject('entity') protected entityValue: RetainedMessage,
              @Inject('entitiesTableConfig') protected entitiesTableConfigValue: EntityTableConfig<RetainedMessage>,
              public fb: FormBuilder,
              protected cd: ChangeDetectorRef) {
    super(store, fb, entityValue, entitiesTableConfigValue, cd);
  }

  buildForm(entity: RetainedMessage): FormGroup {
    const form = this.fb.group(
      {
        topic: [entity ? entity.topic : ''],
        qos: [entity ? entity.qos : ''],
      }
    );
    return form;
  }

  updateForm(entity: RetainedMessage) {
    this.entityForm.patchValue({topic: entity.topic});
    this.entityForm.patchValue({qos: entity.qos});
  }
}
