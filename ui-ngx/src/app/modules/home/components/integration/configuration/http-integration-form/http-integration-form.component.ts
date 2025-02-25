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
import { baseUrl, isDefinedAndNotNull } from '@core/utils';
import { takeUntil } from 'rxjs/operators';
import { HttpIntegration, HttpRequestType, Integration } from '@shared/models/integration.models';
import { privateNetworkAddressValidator } from '@home/components/integration/integration.models';
import { Store } from '@ngrx/store';
import { AppState } from '@core/core.state';
import { TranslateModule, TranslateService } from '@ngx-translate/core';
import { IntegrationForm } from '@home/components/integration/configuration/integration-form';
import { IntegrationCredentialType } from '@shared/models/integration.models';
import { MatError, MatFormField, MatHint, MatLabel } from '@angular/material/form-field';
import { CopyButtonComponent } from '@shared/components/button/copy-button.component';
import { MatOption, MatSelect } from '@angular/material/select';
import {
  HttpTopicFiltersComponent
} from '@home/components/integration/http-topic-filters/http-topic-filters.component';
import {
  IntegrationCredentialsComponent
} from '@home/components/integration/integration-credentials/integration-credentials.component';
import { HeaderFilterMapComponent } from '@shared/components/header-filter-map.component';
import {
  MatExpansionPanel,
  MatExpansionPanelContent,
  MatExpansionPanelDescription,
  MatExpansionPanelHeader
} from '@angular/material/expansion';
import { NgTemplateOutlet } from '@angular/common';
import { MatInput } from '@angular/material/input';
import { MatSlideToggle } from "@angular/material/slide-toggle";
import { ContentType, contentTypesMap } from "@shared/models/constants";
import { MatIcon } from "@angular/material/icon";
import { MatTooltip } from "@angular/material/tooltip";

@Component({
  selector: 'tb-http-integration-form',
  templateUrl: './http-integration-form.component.html',
  styleUrls: ['./http-integration-form.component.scss'],
  imports: [
    ReactiveFormsModule,
    MatFormField,
    CopyButtonComponent,
    MatSelect,
    MatOption,
    HttpTopicFiltersComponent,
    IntegrationCredentialsComponent,
    TranslateModule,
    HeaderFilterMapComponent,
    MatExpansionPanel,
    MatExpansionPanelDescription,
    MatExpansionPanelContent,
    MatExpansionPanelHeader,
    NgTemplateOutlet,
    MatInput,
    MatError,
    MatLabel,
    MatHint,
    MatSlideToggle,
    MatIcon,
    MatTooltip
  ],
  providers: [{
    provide: NG_VALUE_ACCESSOR,
    useExisting: forwardRef(() => HttpIntegrationFormComponent),
    multi: true
  },
  {
    provide: NG_VALIDATORS,
    useExisting: forwardRef(() => HttpIntegrationFormComponent),
    multi: true,
  }]
})
export class HttpIntegrationFormComponent extends IntegrationForm implements ControlValueAccessor, Validator, OnInit {

  integration = input<Integration>();
  isEdit = input<boolean>();

  baseHttpIntegrationConfigForm: UntypedFormGroup;
  httpRequestTypes = Object.keys(HttpRequestType);
  IntegrationCredentialType = IntegrationCredentialType;
  isNew: boolean;
  contentTypes = Object.keys(ContentType);
  contentTypeTranslation = (value: string) => contentTypesMap.get(value as ContentType).name;
  isBinaryContentType = true;

  readonly MemoryBufferSizeInKbLimit = 25000;
  private propagateChangePending = false;
  private propagateChange = (v: any) => { };

  get clientConfigurationFormGroup() {
    return this.baseHttpIntegrationConfigForm.get('clientConfiguration') as UntypedFormGroup;
  }

  constructor(protected fb: UntypedFormBuilder,
              protected store: Store<AppState>,
              protected translate: TranslateService) {
    super();
  }

  ngOnInit() {
    const restEndpointUrlValidators = [Validators.required];
    if (!this.allowLocalNetwork) {
      restEndpointUrlValidators.push(privateNetworkAddressValidator);
    }
    this.baseHttpIntegrationConfigForm = this.fb.group({
      topicFilters: [['#'], Validators.required],
      clientConfiguration: this.fb.group({
        restEndpointUrl: [baseUrl(), restEndpointUrlValidators],
        requestMethod: [HttpRequestType.POST],
        headers: [{'Content-Type': 'application/json'}, Validators.required],
        credentials: [{ type: IntegrationCredentialType.Anonymous }],
        readTimeoutMs: [0, []],
        maxParallelRequestsCount: [0, []],
        maxInMemoryBufferSizeInKb: [256, [Validators.min(1), Validators.max(this.MemoryBufferSizeInKbLimit)]],
        payloadContentType: [ContentType.BINARY, []],
        sendBinaryOnParseFailure: [true, []],
      })
    });
    this.clientConfigurationFormGroup.get('restEndpointUrl').valueChanges.pipe(
      takeUntil(this.destroy$)
    ).subscribe();
    this.baseHttpIntegrationConfigForm.valueChanges.pipe(
      takeUntil(this.destroy$)
    ).subscribe(() => {
      this.updateModels(this.baseHttpIntegrationConfigForm.getRawValue());
    });
    this.clientConfigurationFormGroup.get('payloadContentType')
      .valueChanges.subscribe(value => this.isBinaryContentType = value === ContentType.BINARY);
  }

  writeValue(value: HttpIntegration) {
    if (isDefinedAndNotNull(value)) {
      this.isNew = false;
      this.baseHttpIntegrationConfigForm.reset(value, {emitEvent: false});
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
        this.updateModels(this.baseHttpIntegrationConfigForm.getRawValue());
      }, 0);
    }
  }

  registerOnTouched(fn: any) { }

  setDisabledState(isDisabled: boolean) {
    this.disabled = isDisabled;
    if (isDisabled) {
      this.baseHttpIntegrationConfigForm.disable({emitEvent: false});
    } else {
      this.baseHttpIntegrationConfigForm.enable({emitEvent: false});
    }
  }

  private updateModels(value) {
    if (this.isNew) {
      delete value.topicFilters;
    }
    this.propagateChange(value);
  }

  validate(): ValidationErrors | null {
    return this.baseHttpIntegrationConfigForm.valid ? null : {
      baseHttpIntegrationConfigForm: {valid: false}
    };
  }

  updatedValidationPrivateNetwork() {
    if (this.allowLocalNetwork) {
      this.clientConfigurationFormGroup?.get('restEndpointUrl').removeValidators(privateNetworkAddressValidator);
    } else {
      this.clientConfigurationFormGroup?.get('restEndpointUrl').addValidators(privateNetworkAddressValidator);
    }
    this.clientConfigurationFormGroup?.get('restEndpointUrl').updateValueAndValidity({emitEvent: false});
  }
}
