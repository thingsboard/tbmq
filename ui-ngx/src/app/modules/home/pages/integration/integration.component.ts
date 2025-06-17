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

import {
  ChangeDetectorRef,
  Component,
  Inject,
} from '@angular/core';
import { Store } from '@ngrx/store';
import { AppState } from '@core/core.state';
import { EntityComponent } from '../../components/entity/entity.component';
import { ReactiveFormsModule, UntypedFormBuilder, UntypedFormGroup, Validators } from '@angular/forms';
import { ActionNotificationShow } from '@core/notification/notification.actions';
import { TranslateModule, TranslateService } from '@ngx-translate/core';
import { EntityTableConfig } from '@home/models/entity/entities-table-config.models';
import {
  Integration,
  IntegrationInfo,
  IntegrationType,
  integrationTypeInfoMap
} from '@shared/models/integration.models';
import { isDefined } from '@core/utils';
import { IntegrationService } from '@core/http/integration.service';
import { PageLink } from '@shared/models/page/page-link';
import { MatIcon } from '@angular/material/icon';
import { AsyncPipe } from '@angular/common';
import { ClipboardModule } from 'ngx-clipboard';
import { MatError, MatFormField, MatLabel } from '@angular/material/form-field';
import { MatSlideToggle } from '@angular/material/slide-toggle';
import { MatButton } from '@angular/material/button';
import { ToastDirective } from '@shared/components/toast.directive';
import { MatInput } from '@angular/material/input';
import { CdkTextareaAutosize } from '@angular/cdk/text-field';
import { IntegrationTypeSelectComponent } from '@home/components/integration/integration-type-select.component';
import {
  IntegrationConfigurationComponent
} from '@home/components/integration/configuration/integration-configuration.component';
import { KeyValMapComponent } from '@shared/components/key-val-map.component';

@Component({
  selector: 'tb-integration',
  templateUrl: './integration.component.html',
  styleUrls: ['./integration.component.scss'],
  imports: [
    MatIcon,
    TranslateModule,
    AsyncPipe,
    ClipboardModule,
    ReactiveFormsModule,
    MatFormField,
    MatSlideToggle,
    MatLabel,
    MatButton,
    ToastDirective,
    MatInput,
    CdkTextareaAutosize,
    IntegrationTypeSelectComponent,
    IntegrationConfigurationComponent,
    MatError,
    KeyValMapComponent
  ]
})
export class IntegrationComponent extends EntityComponent<Integration, PageLink, IntegrationInfo> {

  private integrationType: IntegrationType;

  constructor(protected store: Store<AppState>,
              protected translate: TranslateService,
              @Inject('entity') protected entityValue: Integration,
              @Inject('entitiesTableConfig') protected entitiesTableConfigValue: EntityTableConfig<Integration, PageLink, IntegrationInfo>,
              protected fb: UntypedFormBuilder,
              protected integrationService: IntegrationService,
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

  buildForm(entity: Integration): UntypedFormGroup {
    this.integrationType = entity ? entity.type : null;
    return this.fb.group(
      {
        name: [entity ? entity.name : '', [Validators.required, Validators.maxLength(255), Validators.pattern(/(?:.|\s)*\S(&:.|\s)*/)]],
        type: [{value: this.integrationType, disabled: true}, [Validators.required]],
        enabled: [isDefined(entity?.enabled) ? entity.enabled : true],
        configuration: this.fb.control([entity ? entity.configuration : null]),
        metadata: [entity && entity.configuration ? entity.configuration.metadata : {}],
        additionalInfo: this.fb.group(
          {
            description: [entity && entity.additionalInfo ? entity.additionalInfo.description : ''],
          }
        )
      }
    );
  }

  updateFormState() {
    super.updateFormState();
    this.entityForm.get('type').disable({ emitEvent: false });
  }

  private get allowCheckConnection(): boolean {
    if (integrationTypeInfoMap.has(this.integrationType)) {
      return integrationTypeInfoMap.get(this.integrationType).checkConnection || false;
    }
    return false;
  }

  get isCheckConnectionAvailable(): boolean {
    return this.allowCheckConnection;
  }

  updateForm(entity: Integration) {
    this.entityForm.patchValue({
      name: entity.name,
      type: entity.type,
      enabled: isDefined(entity.enabled) ? entity.enabled : true,
      metadata: entity.configuration ? entity.configuration.metadata : {},
      configuration: entity.configuration,
      additionalInfo: {
        description: entity.additionalInfo ? entity.additionalInfo.description : '' }
      },
      {emitEvent: false}
    );
    this.integrationType = entity.type;
  }

  prepareFormValue(formValue: any): any {
    if (!formValue.configuration) {
      formValue.configuration = {};
    }
    formValue.configuration.metadata = formValue.metadata || {};
    formValue.name = formValue.name ? formValue.name.trim() : formValue.name;
    delete formValue.metadata;
    return formValue;
  }

  onIntegrationIdCopied() {
    this.store.dispatch(new ActionNotificationShow(
      {
        message: this.translate.instant('integration.idCopiedMessage'),
        type: 'success',
        duration: 750,
        verticalPosition: 'bottom',
        horizontalPosition: 'right',
        target: 'integrationRoot'
      }));
  }

  onIntegrationCheck() {
    this.integrationService.checkIntegrationConnection(this.entityFormValue(), {ignoreErrors: true}).subscribe(() =>
    {
      this.store.dispatch(new ActionNotificationShow(
      {
        message: this.translate.instant('integration.check-success'),
        type: 'success',
        duration: 5000,
        verticalPosition: 'bottom',
        horizontalPosition: 'right',
        target: 'integrationRoot'
      }));
    },
    error => {
      this.store.dispatch(new ActionNotificationShow(
        {
          message: error.error.message,
          type: 'error',
          duration: 5000,
          verticalPosition: 'bottom',
          horizontalPosition: 'right',
          target: 'integrationRoot'
        }));
    });
  }

  hideRestart() {
    return !this.entity?.enabled;
  }
}
