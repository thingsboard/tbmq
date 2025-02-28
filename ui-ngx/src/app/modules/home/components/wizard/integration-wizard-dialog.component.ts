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

import { Component, Inject, OnDestroy, viewChild } from '@angular/core';
import { DialogComponent } from '@shared/components/dialog.component';
import {
  getIntegrationHelpLink,
  Integration,
  IntegrationType,
  integrationTypeInfoMap
} from '@shared/models/integration.models';
import { Store } from '@ngrx/store';
import { AppState } from '@core/core.state';
import { Router } from '@angular/router';
import { MAT_DIALOG_DATA, MatDialogActions, MatDialogContent, MatDialogRef } from '@angular/material/dialog';
import { AddEntityDialogData } from '@home/models/entity/entity-component.models';
import { MatStep, MatStepLabel, MatStepper, MatStepperIcon, StepperOrientation } from '@angular/material/stepper';
import { MediaBreakpoints } from '@shared/models/constants';
import { BreakpointObserver } from '@angular/cdk/layout';
import { map, takeUntil } from 'rxjs/operators';
import { Observable, Subject } from 'rxjs';
import { StepperSelectionEvent } from '@angular/cdk/stepper';
import { TranslateModule, TranslateService } from '@ngx-translate/core';
import { ReactiveFormsModule, UntypedFormBuilder, UntypedFormGroup, Validators } from '@angular/forms';
import { IntegrationService } from '@core/http/integration.service';
import { MatToolbar } from '@angular/material/toolbar';
import { HelpComponent } from '@shared/components/help.component';
import { MatButton, MatIconButton } from '@angular/material/button';
import { MatIcon } from '@angular/material/icon';
import { MatProgressBar } from '@angular/material/progress-bar';
import { AsyncPipe } from '@angular/common';
import { IntegrationTypeSelectComponent } from '@home/components/integration/integration-type-select.component';
import { MatError, MatFormField, MatLabel } from '@angular/material/form-field';
import { MatInput } from '@angular/material/input';
import { MatSlideToggle } from '@angular/material/slide-toggle';
import {
  IntegrationConfigurationComponent
} from '@home/components/integration/configuration/integration-configuration.component';
import { CdkTextareaAutosize } from '@angular/cdk/text-field';
import { MatProgressSpinner } from '@angular/material/progress-spinner';
import { MatDivider } from '@angular/material/divider';
import { ToastDirective } from '@shared/components/toast.directive';
import {
  HttpTopicFiltersComponent
} from '@home/components/integration/http-topic-filters/http-topic-filters.component';
import { KeyValMapComponent } from '@shared/components/key-val-map.component';

@Component({
  selector: 'tb-integration-wizard',
  templateUrl: './integration-wizard-dialog.component.html',
  styleUrls: ['./integration-wizard-dialog.component.scss'],
  imports: [
    MatToolbar,
    HelpComponent,
    MatIconButton,
    MatIcon,
    MatProgressBar,
    AsyncPipe,
    MatDialogContent,
    MatStepper,
    MatStepperIcon,
    MatStep,
    ReactiveFormsModule,
    MatStepLabel,
    TranslateModule,
    IntegrationTypeSelectComponent,
    MatFormField,
    MatInput,
    MatSlideToggle,
    IntegrationConfigurationComponent,
    CdkTextareaAutosize,
    MatProgressSpinner,
    MatDivider,
    MatDialogActions,
    MatButton,
    MatError,
    MatLabel,
    ToastDirective,
    HttpTopicFiltersComponent,
    KeyValMapComponent
  ]
})
export class IntegrationWizardDialogComponent extends
  DialogComponent<IntegrationWizardDialogComponent, Integration> implements OnDestroy {

  readonly addIntegrationWizardStepper = viewChild<MatStepper>('addIntegrationWizardStepper');

  selectedIndex = 0;
  showCheckConnection = false;
  integrationType = '';
  showCheckSuccess = false;
  checkErrMsg = '';

  stepperOrientation: Observable<StepperOrientation>;

  integrationWizardForm: UntypedFormGroup;
  integrationTopicFilterForm: UntypedFormGroup;
  integrationConfigurationForm: UntypedFormGroup;

  private checkConnectionAllow = false;
  private destroy$ = new Subject<void>();

  constructor(protected store: Store<AppState>,
              protected router: Router,
              @Inject(MAT_DIALOG_DATA) public data: AddEntityDialogData<Integration>,
              public dialogRef: MatDialogRef<IntegrationWizardDialogComponent, Integration>,
              private breakpointObserver: BreakpointObserver,
              private integrationService: IntegrationService,
              private translate: TranslateService,
              private fb: UntypedFormBuilder) {
    super(store, router, dialogRef);

    this.stepperOrientation = this.breakpointObserver.observe(MediaBreakpoints['gt-sm'])
      .pipe(map(({matches}) => matches ? 'horizontal' : 'vertical'));

    this.integrationWizardForm = this.fb.group({
      name: ['', [Validators.required, Validators.maxLength(255), Validators.pattern(/(?:.|\s)*\S(&:.|\s)*/)]],
      type: [null, [Validators.required]],
      enabled: [true],
    });

    this.integrationWizardForm.get('type').valueChanges.pipe(
      takeUntil(this.destroy$)
    ).subscribe((value: IntegrationType) => {
      if (integrationTypeInfoMap.has(value)) {
        this.integrationType = this.translate.instant(integrationTypeInfoMap.get(value).name);
        this.integrationWizardForm.get('name').patchValue( this.translate.instant('integration.integration-name', {
          integrationType: this.translate.instant(integrationTypeInfoMap.get(value).name)
        }), {emitEvent: false});
        this.checkConnectionAllow = integrationTypeInfoMap.get(value).checkConnection || false;
      } else {
        this.integrationWizardForm.get('name').patchValue('', {emitEvent: false});
        this.integrationType = '';
      }
      this.integrationConfigurationForm.get('configuration').setValue(null);
    });

    this.integrationConfigurationForm = this.fb.group({
      configuration: [{}, Validators.required],
      metadata: [{}],
      additionalInfo: this.fb.group(
        {
          description: ['']
        }
      )
    });

    this.integrationTopicFilterForm = this.fb.group({
      topicFilters: [['tbmq/#'], Validators.required]
    });
  }

  ngOnDestroy() {
    super.ngOnDestroy();
    this.destroy$.next();
    this.destroy$.complete();
  }

  add(): void {
    if (this.allValid()) {
      const integrationData = this.integrationWizardForm.getRawValue();
      const integrationTopicFilter = this.integrationTopicFilterForm.getRawValue();
      const integrationConfig = this.integrationConfigurationForm.getRawValue();
      integrationConfig.configuration = {
        ...integrationConfig.configuration,
        ...integrationTopicFilter,
        metadata: integrationConfig.metadata,
      };
      delete integrationConfig.metadata;
      const integration = {
        ...integrationData,
        ...integrationConfig
      } as Integration;
      this.integrationService.saveIntegration(integration).subscribe(
        (device) => {
          this.dialogRef.close(device);
        }
      );
    }
  }

  get helpLinkId(): string {
    return getIntegrationHelpLink(this.integrationWizardForm.value);
  }

  private getIntegrationData(): Integration {
    const integrationData: Integration = {
      configuration: {
        metadata: this.integrationConfigurationForm.value.metadata,
        ...this.integrationConfigurationForm.value.configuration,
        ...this.integrationTopicFilterForm.getRawValue()
      },
      name: this.integrationWizardForm.value.name.trim(),
      type: this.integrationWizardForm.value.type,
      enabled: this.integrationWizardForm.value.enabled,
    };
    if (this.integrationConfigurationForm.value.additionalInfo.description) {
      integrationData.additionalInfo = {
        description: this.integrationConfigurationForm.value.additionalInfo.description
      };
    }
    return integrationData;
  }

  get maxStep(): number {
    return 2;
  }

  cancel(): void {
    this.dialogRef.close(null);
  }

  changeStep($event: StepperSelectionEvent) {
    this.selectedIndex = $event.selectedIndex;
    if (this.isConnectionStep) {
      this.showCheckConnection = false;
    }
  }

  nextStep() {
    if (this.selectedIndex >= this.maxStep) {
      this.add();
    } else {
      this.addIntegrationWizardStepper().next();
    }
  }

  nextStepLabel(): string {
    if (this.selectedIndex >= this.maxStep) {
      return 'action.add';
    }
    return 'action.next';
  }

  backStep() {
    this.addIntegrationWizardStepper().previous();
  }

  onIntegrationCheck(): void {
    if (this.allValid()) {
      this.showCheckConnection = true;
      this.showCheckSuccess = false;
      this.checkErrMsg = '';
      setTimeout(() => {
        this.addIntegrationWizardStepper().next();
      }, 0);
      const integrationData = this.getIntegrationData();
      this.integrationService.checkIntegrationConnection(integrationData, {
        ignoreErrors: true,
        ignoreLoading: true
      }).subscribe(() => {
        this.showCheckSuccess = true;
      }, (error) => {
        this.checkErrMsg = error.error.message;
      });
    }
  }

  get isCheckConnectionAvailable(): boolean {
    return this.checkConnectionAllow && this.isConnectionStep;
  }

  private get isConnectionStep(): boolean {
    return this.selectedIndex === this.maxStep;
  }

  private allValid(): boolean {
    if (this.addIntegrationWizardStepper().steps.find((item, index) => {
      if (item.stepControl?.invalid) {
        item.interacted = true;
        this.addIntegrationWizardStepper().selectedIndex = index;
        return true;
      } else {
        return false;
      }
    } )) {
      return false;
    } else {
      return true;
    }
  }
}
