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
import { isDefinedAndNotNull } from '@core/utils';
import { takeUntil } from 'rxjs/operators';
import { IntegrationForm } from '@home/components/integration/configuration/integration-form';
import {
  Integration,
  MqttIntegration,
} from '@shared/models/integration.models';
import { MatFormField, MatLabel, MatSuffix } from '@angular/material/form-field';
import { MatInput } from '@angular/material/input';
import { NgTemplateOutlet } from '@angular/common';
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
  HttpTopicFiltersComponent
} from '@home/components/integration/http-topic-filters/http-topic-filters.component';
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

@Component({
  selector: 'tb-mqtt-integration-form',
  templateUrl: './mqtt-integration-form.component.html',
  styleUrls: ['./mqtt-integration-form.component.scss'],
  imports: [
    ReactiveFormsModule,
    MatFormField,
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
    HttpTopicFiltersComponent,
    MatIconButton,
    IntegrationCredentialsComponent,
    QosSelectComponent,
    MatSlideToggle,
    CopyButtonComponent
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

  private propagateChangePending = false;
  private propagateChange = (v: any) => { };

  get clientConfigurationFormGroup() {
    return this.mqttIntegrationConfigForm.get('clientConfiguration') as UntypedFormGroup;
  }

  constructor(private fb: UntypedFormBuilder) {
    super();
  }

  ngOnInit() {
    this.mqttIntegrationConfigForm = this.fb.group({
      topicFilters: [['tbmq/#'], Validators.required],
      clientConfiguration: this.fb.group({
        sendOnlyMsgPayload: [false, []],
        host: [null, [Validators.required]],
        port: [1883, [Validators.min(1), Validators.max(65535), Validators.pattern('[0-9]*'), Validators.required]],
        topicName: ['tbmq/messages', []],
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
    });
    this.initFormListeners();
  }

  writeValue(value: MqttIntegration) {
    if (isDefinedAndNotNull(value?.clientConfiguration)) {
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
      .subscribe((value) => {
        if (!this.disabled) {
          if (value) {
            this.clientConfigurationFormGroup.get('qos').disable({emitEvent: false});
          } else {
            this.clientConfigurationFormGroup.get('qos').enable({emitEvent: false});
          }
          this.clientConfigurationFormGroup.get('qos').updateValueAndValidity();
        }
      });

    this.clientConfigurationFormGroup.get('useMsgTopicName').valueChanges
      .pipe(takeUntil(this.destroy$))
      .subscribe((value) => {
        if (!this.disabled) {
          if (value) {
            this.clientConfigurationFormGroup.get('topicName').disable({emitEvent: false});
          } else {
            this.clientConfigurationFormGroup.get('topicName').enable({emitEvent: false});
            this.clientConfigurationFormGroup.get('topicName').setValidators(Validators.required);
          }
          this.clientConfigurationFormGroup.get('topicName').updateValueAndValidity();
        }
      });

    this.clientConfigurationFormGroup.get('useMsgRetain').valueChanges
      .pipe(takeUntil(this.destroy$))
      .subscribe((value) => {
        if (!this.disabled) {
          if (value) {
            this.clientConfigurationFormGroup.get('retained').disable({emitEvent: false});
          } else {
            this.clientConfigurationFormGroup.get('retained').enable({emitEvent: false});
          }
          this.clientConfigurationFormGroup.get('retained').updateValueAndValidity();
        }
      });

    setTimeout(() => {
      if (this.isNew) {
        this.clientConfigurationFormGroup.get('topicName').disable();
        this.clientConfigurationFormGroup.get('qos').disable();
        this.clientConfigurationFormGroup.get('retained').disable();
      }
    }, 0);
  }

  private updateView(value: MqttIntegration) {
    if (value.clientConfiguration.useMsgTopicName) {
      this.clientConfigurationFormGroup.get('topicName').disable({emitEvent: false});
      this.clientConfigurationFormGroup.get('topicName').updateValueAndValidity({emitEvent: false});
    }
    if (value.clientConfiguration.useMsgQoS) {
      this.clientConfigurationFormGroup.get('qos').disable({emitEvent: false});
      this.clientConfigurationFormGroup.get('qos').updateValueAndValidity({emitEvent: false});
    }
    if (value.clientConfiguration.useMsgRetain) {
      this.clientConfigurationFormGroup.get('retained').disable({emitEvent: false});
      this.clientConfigurationFormGroup.get('retained').updateValueAndValidity({emitEvent: false});
    }
  }
}
