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

import { Component, OnDestroy } from '@angular/core';
import { Store } from '@ngrx/store';
import { AppState } from '@core/core.state';
import { PageComponent } from '@shared/components/page.component';
import { AbstractControl, UntypedFormBuilder, UntypedFormGroup, ValidationErrors, ValidatorFn, Validators, FormsModule, ReactiveFormsModule } from '@angular/forms';
import { Subject } from 'rxjs';
import {
  AdminSettings,
  mqttAuthorizationSettingsKey,
  MqttAuthSettings,
  SecuritySettings
} from '@shared/models/settings.models';
import { SettingsService } from '@core/http/settings.service';
import { MatCard, MatCardHeader, MatCardTitle, MatCardContent } from '@angular/material/card';
import { TranslateModule } from '@ngx-translate/core';
import { AsyncPipe } from '@angular/common';
import { MatFormField, MatLabel, MatHint, MatSuffix } from '@angular/material/form-field';
import { MatInput } from '@angular/material/input';
import { MatCheckbox } from '@angular/material/checkbox';
import { HintTooltipIconComponent } from '@shared/components/hint-tooltip-icon.component';
import { MatButton } from '@angular/material/button';
import { HasConfirmForm } from '@core/guards/confirm-on-exit.guard';
import { MatIcon } from '@angular/material/icon';
import { MatTooltip } from '@angular/material/tooltip';
import { MatSlideToggle } from '@angular/material/slide-toggle';
import { takeUntil } from 'rxjs/operators';
import { HelpComponent } from '@shared/components/help.component';

@Component({
    selector: 'tb-security-settings',
    templateUrl: './security-settings.component.html',
    styleUrls: ['./security-settings.component.scss'],
  imports: [MatCard, MatCardHeader, MatCardTitle, TranslateModule, MatCardContent, FormsModule, ReactiveFormsModule, MatFormField, MatLabel, MatInput, MatHint, MatCheckbox, HintTooltipIconComponent, MatButton, AsyncPipe, MatIcon, MatSuffix, MatTooltip, MatSlideToggle, HelpComponent]
})
export class SecuritySettingsComponent extends PageComponent implements OnDestroy, HasConfirmForm {

  securitySettingsForm: UntypedFormGroup;
  mqttAuthSettingsForm: UntypedFormGroup;

  authStrategyLabel: string;
  authStrategyTooltip: string;

  private securitySettings: SecuritySettings;
  private mqttAuthSettings: AdminSettings<MqttAuthSettings>;
  private destroy$ = new Subject<void>();

  constructor(protected store: Store<AppState>,
              private settingsService: SettingsService,
              public fb: UntypedFormBuilder) {
    super(store);
    this.buildSecuritySettingsForm();
    this.buildMqttAuthSettingsForm();
    this.getSettings();
  }

  ngOnDestroy() {
    this.destroy$.complete();
    super.ngOnDestroy();
  }

  saveSecuritySettings() {
    this.securitySettings = {...this.securitySettings, ...this.securitySettingsForm.value};
    this.settingsService.saveSecuritySettings(this.securitySettings).subscribe(
      securitySettings => this.processSecuritySettings(securitySettings)
    );
  }

  discardSecuritySettings(): void {
    this.securitySettingsForm.reset(this.securitySettings);
  }

  discardMqttAuthSettings(): void {
    this.mqttAuthSettingsForm.reset(this.mqttAuthSettings.jsonValue);
  }

  confirmForm(): UntypedFormGroup {
    if (this.mqttAuthSettingsForm.dirty) {
      return this.mqttAuthSettingsForm;
    }
    return this.securitySettingsForm;
  }

  saveMqttAuthSettings() {
    const mqttAuthSettings: AdminSettings<MqttAuthSettings> = JSON.parse(JSON.stringify(this.mqttAuthSettings));
    mqttAuthSettings.jsonValue = {...mqttAuthSettings.jsonValue, ...this.mqttAuthSettingsForm.value};
    this.settingsService.saveAdminSettings(mqttAuthSettings).subscribe(
      settings => this.processMqttAuthSettings(settings)
    );
  }

  private getSettings() {
    this.getMqttAuthSettings();
    this.getSecuritySettings();
  }

  private getMqttAuthSettings() {
    this.settingsService.getAdminSettings<MqttAuthSettings>(mqttAuthorizationSettingsKey)
      .subscribe(settings => this.processMqttAuthSettings(settings));
  }

  private getSecuritySettings() {
    this.settingsService.getSecuritySettings().subscribe(settings => this.processSecuritySettings(settings));
  }

  private buildSecuritySettingsForm() {
    this.securitySettingsForm = this.fb.group({
      passwordPolicy: this.fb.group(
        {
          minimumLength: [null, [Validators.required, Validators.min(6), Validators.max(50)]],
          maximumLength: [null, [Validators.min(6), this.maxPasswordValidation()]],
          minimumUppercaseLetters: [null, Validators.min(0)],
          minimumLowercaseLetters: [null, Validators.min(0)],
          minimumDigits: [null, Validators.min(0)],
          minimumSpecialCharacters: [null, Validators.min(0)],
          passwordExpirationPeriodDays: [null, Validators.min(0)],
          passwordReuseFrequencyDays: [null, Validators.min(0)],
          allowWhitespaces: [null],
          forceUserToResetPasswordIfNotValid: [null]
        }
      )
    });
  }

  private buildMqttAuthSettingsForm() {
    this.mqttAuthSettingsForm = this.fb.group({
      useListenerBasedProviderOnly: [null, []],
      jwtFirst: [null, []],
    });
    this.mqttAuthSettingsForm.get('useListenerBasedProviderOnly').valueChanges
      .pipe(takeUntil(this.destroy$))
      .subscribe(value => {
        this.authStrategyLabel = value ? 'admin.auth-strategy-single' : 'admin.auth-strategy-both';
        this.authStrategyTooltip = value ? 'admin.auth-strategy-single-hint' : 'admin.auth-strategy-both-hint';
      })
  }

  private processSecuritySettings(settings: SecuritySettings): void {
    this.securitySettings = settings;
    this.securitySettingsForm.reset(this.securitySettings);
  }

  private maxPasswordValidation(): ValidatorFn {
    return (control: AbstractControl): ValidationErrors | null => {
      const value: string = control.value;
      if (value) {
        if (value < control.parent.value?.minimumLength) {
          return {lessMin: true};
        }
      }
      return null;
    };
  }

  private processMqttAuthSettings(settings: AdminSettings<MqttAuthSettings>): void {
    this.mqttAuthSettings = settings;
    this.mqttAuthSettingsForm.reset(this.mqttAuthSettings.jsonValue);
  }

}
