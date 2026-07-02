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

import { Component, forwardRef, input, OnInit } from '@angular/core';
import {
  ControlValueAccessor,
  UntypedFormBuilder,
  UntypedFormGroup,
  NG_VALIDATORS,
  NG_VALUE_ACCESSOR,
  ValidationErrors,
  Validator,
  Validators, ReactiveFormsModule
} from '@angular/forms';
import { filterTopics, isDefinedAndNotNull, notOnlyWhitespaceValidator } from '@core/utils';
import { map, takeUntil } from 'rxjs/operators';
import { IntegrationForm } from '@home/components/integration/configuration/integration-form';
import {
  atLeastOneFilterOrEvent,
  Integration,
  MqttIntegration,
} from '@shared/models/integration.models';
import { MatError, MatFormField, MatHint, MatLabel, MatSuffix } from '@angular/material/form-field';
import { MatInput } from '@angular/material/input';
import { AsyncPipe, NgTemplateOutlet } from '@angular/common';
import { TranslateModule } from '@ngx-translate/core';
import {
  MatExpansionPanel,
  MatExpansionPanelContent,
  MatExpansionPanelDescription,
  MatExpansionPanelHeader
} from '@angular/material/expansion';
import { MatOption, MatSelect } from '@angular/material/select';
import { MatIcon } from '@angular/material/icon';
import { MatTooltip } from '@angular/material/tooltip';
import {
  IntegrationTopicFiltersComponent
} from '@home/components/integration/integration-topic-filters/integration-topic-filters.component';
import {
  IntegrationLifecycleEventsComponent
} from '@home/components/integration/lifecycle-events/integration-lifecycle-events.component';
import { MatIconButton } from '@angular/material/button';
import { clientIdRandom } from '@shared/models/ws-client.model';
import {
  IntegrationCredentialsComponent
} from '@home/components/integration/integration-credentials/integration-credentials.component';
import { IntegrationCredentialType } from '@shared/models/integration.models';
import { MqttVersions } from '@shared/models/ws-client.model';
import { QoS } from '@shared/models/session.model';
import { QosSelectComponent } from '@shared/components/qos-select.component';
import { MatSlideToggle } from '@angular/material/slide-toggle';
import { CopyButtonComponent } from '@shared/components/button/copy-button.component';
import { HintTooltipIconComponent } from '@shared/components/hint-tooltip-icon.component';
import { MatAutocomplete, MatAutocompleteTrigger } from '@angular/material/autocomplete';
import { Observable } from 'rxjs';

@Component({
  selector: 'tb-mqtt-integration-form',
  templateUrl: './mqtt-integration-form.component.html',
  styleUrls: ['./mqtt-integration-form.component.scss'],
  imports: [
    ReactiveFormsModule,
    MatFormField,
    MatError,
    MatHint,
    MatInput,
    MatLabel,
    TranslateModule,
    MatExpansionPanel,
    MatExpansionPanelHeader,
    MatExpansionPanelDescription,
    MatExpansionPanelContent,
    NgTemplateOutlet,
    MatSelect,
    MatOption,
    MatSuffix,
    MatIcon,
    MatTooltip,
    IntegrationTopicFiltersComponent,
    IntegrationLifecycleEventsComponent,
    MatIconButton,
    IntegrationCredentialsComponent,
    QosSelectComponent,
    MatSlideToggle,
    CopyButtonComponent,
    HintTooltipIconComponent,
    AsyncPipe,
    MatAutocomplete,
    MatAutocompleteTrigger
  ],
  providers: [{
    provide: NG_VALUE_ACCESSOR,
    useExisting: forwardRef(() => MqttIntegrationFormComponent),
    multi: true
  },
  {
    provide: NG_VALIDATORS,
    useExisting: forwardRef(() => MqttIntegrationFormComponent),
    multi: true,
  }]
})
export class MqttIntegrationFormComponent extends IntegrationForm implements ControlValueAccessor, Validator, OnInit {

  integration = input<Integration>();
  isEdit = input<boolean>();

  mqttIntegrationConfigForm: UntypedFormGroup;
  isNew: boolean;
  IntegrationCredentialType = IntegrationCredentialType;
  mqttVersions = MqttVersions;
  filteredTopics: Observable<string[]>;

  private propagateChangePending = false;
  private propagateChange = (v: any) => { };

  get clientConfigurationFormGroup() {
    return this.mqttIntegrationConfigForm.get('clientConfiguration') as UntypedFormGroup;
  }

  get lifecycleEventsSelected(): boolean {
    return this.eventsEnabled();
  }

  constructor(private fb: UntypedFormBuilder) {
    super();
  }

  ngOnInit() {
    this.mqttIntegrationConfigForm = this.fb.group({
      topicFilters: [['tbmq/#']],
      lifecycleEventTypes: [[]],
      clientConfiguration: this.fb.group({
        sendOnlyMsgPayload: [false, []],
        host: [null, [Validators.required, notOnlyWhitespaceValidator]],
        port: [1883, [Validators.min(1), Validators.max(65535), Validators.pattern('[0-9]*'), Validators.required]],
        topicName: ['tbmq/messages', [Validators.required]],
        useMsgTopicName: [true, []],
        clientId: [clientIdRandom(), [Validators.required]],
        credentials: [{ type: IntegrationCredentialType.Anonymous }],
        ssl: [false, [Validators.required]],
        connectTimeoutSec: [10, [Validators.required]],
        reconnectPeriodSec: [5, [Validators.required]],
        mqttVersion: [4, []],
        qos: [QoS.AT_LEAST_ONCE, []],
        useMsgQoS: [true, []],
        retained: [false, []],
        useMsgRetain: [true, []],
        keepAliveSec: [60, [Validators.required]],
      })
    }, {validators: atLeastOneFilterOrEvent});
    this.initFormListeners();
  }

  writeValue(value: MqttIntegration) {
    if (isDefinedAndNotNull(value?.clientConfiguration?.host)) {
      this.isNew = false;
      this.mqttIntegrationConfigForm.reset(value, {emitEvent: false});
      this.updateView(value);
    } else {
      this.isNew = true;
      this.propagateChangePending = true;
    }
  }

  registerOnChange(fn: any): void {
    this.propagateChange = fn;
    if (this.propagateChangePending) {
      this.propagateChangePending = false;
      setTimeout(() => {
        this.updateModels(this.mqttIntegrationConfigForm.getRawValue());
      }, 0);
    }
  }

  registerOnTouched(fn: any) { }

  setDisabledState(isDisabled: boolean) {
    this.disabled = isDisabled;
    if (isDisabled) {
      this.mqttIntegrationConfigForm.disable({emitEvent: false});
    } else {
      this.mqttIntegrationConfigForm.enable({emitEvent: false});
    }
  }

  private updateModels(value) {
    if (this.isNew) {
      delete value.topicFilters;
      delete value.lifecycleEventTypes;
    }
    this.propagateChange(value);
  }

  validate(): ValidationErrors | null {
    return this.mqttIntegrationConfigForm.valid ? null : {
      mqttIntegrationConfigForm: {valid: false}
    };
  }

  generateClientId() {
    this.clientConfigurationFormGroup.patchValue({clientId: clientIdRandom()});
  }

  displayEnableSsl() {
    return this.clientConfigurationFormGroup.get('credentials').value?.type !== 'cert.PEM';
  }

  private initFormListeners() {
    this.mqttIntegrationConfigForm.valueChanges
      .pipe(takeUntil(this.destroy$))
      .subscribe(() => {
        this.updateModels(this.mqttIntegrationConfigForm.getRawValue());
      });

    this.clientConfigurationFormGroup.get('useMsgQoS').valueChanges
      .pipe(takeUntil(this.destroy$))
      .subscribe(() => this.updateQosState());

    this.clientConfigurationFormGroup.get('useMsgTopicName').valueChanges
      .pipe(takeUntil(this.destroy$))
      .subscribe(() => this.updateTopicNameState());

    this.clientConfigurationFormGroup.get('useMsgRetain').valueChanges
      .pipe(takeUntil(this.destroy$))
      .subscribe(() => this.updateRetainState());

    // Lifecycle events have no incoming message, so they are always delivered to the static topic/qos/retain.
    // Whenever any lifecycle event is selected, these fields must be enabled and set - even if the matching
    // "use message ..." option is on, since that option only governs message delivery.
    this.mqttIntegrationConfigForm.get('lifecycleEventTypes').valueChanges
      .pipe(takeUntil(this.destroy$))
      .subscribe(() => {
        this.updateTopicNameState();
        this.updateQosState();
        this.updateRetainState();
      });

    setTimeout(() => {
      if (this.isNew) {
        this.clientConfigurationFormGroup.get('topicName').disable();
        this.clientConfigurationFormGroup.get('qos').disable();
        this.clientConfigurationFormGroup.get('retained').disable();
      }
    }, 0);

    this.filteredTopics = this.clientConfigurationFormGroup.get('topicName').valueChanges.pipe(
      takeUntil(this.destroy$),
      map(value => filterTopics(value || ''))
    );
  }

  private updateView(_value: MqttIntegration) {
    this.updateTopicNameState();
    this.updateQosState();
    this.updateRetainState();
  }

  private eventsEnabled(): boolean {
    const types = this.mqttIntegrationConfigForm.get('lifecycleEventTypes')?.value;
    return Array.isArray(types) && types.length > 0;
  }

  // The static topic must be enabled and required when it is used for message delivery (dynamic topic off)
  // OR when lifecycle events are delivered (events always use the static topic).
  private updateTopicNameState() {
    if (this.disabled) {
      return;
    }
    const control = this.clientConfigurationFormGroup.get('topicName');
    if (!this.clientConfigurationFormGroup.get('useMsgTopicName').value || this.eventsEnabled()) {
      control.enable({emitEvent: false});
      control.setValidators(Validators.required);
    } else {
      control.disable({emitEvent: false});
      control.clearValidators();
    }
    control.updateValueAndValidity({emitEvent: false});
  }

  private updateQosState() {
    if (this.disabled) {
      return;
    }
    const control = this.clientConfigurationFormGroup.get('qos');
    if (!this.clientConfigurationFormGroup.get('useMsgQoS').value || this.eventsEnabled()) {
      control.enable({emitEvent: false});
    } else {
      control.disable({emitEvent: false});
    }
    control.updateValueAndValidity({emitEvent: false});
  }

  private updateRetainState() {
    if (this.disabled) {
      return;
    }
    const control = this.clientConfigurationFormGroup.get('retained');
    if (!this.clientConfigurationFormGroup.get('useMsgRetain').value || this.eventsEnabled()) {
      control.enable({emitEvent: false});
    } else {
      control.disable({emitEvent: false});
    }
    control.updateValueAndValidity({emitEvent: false});
  }
}
