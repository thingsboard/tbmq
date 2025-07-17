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

import { Component, OnDestroy, Renderer2 } from '@angular/core';
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
import { MatButton, MatIconButton } from '@angular/material/button';
import { HasConfirmForm } from '@core/guards/confirm-on-exit.guard';
import { MatIcon } from '@angular/material/icon';
import { MatTooltip } from '@angular/material/tooltip';
import { HelpComponent } from '@shared/components/help.component';
import { MatChipGrid, MatChipInput, MatChipRow } from '@angular/material/chips';
import {
  DndDraggableDirective,
  DndDragImageRefDirective,
  DndDropEvent,
  DndDropzoneDirective,
  DndHandleDirective, DndPlaceholderRefDirective
} from 'ngx-drag-drop';
import { guid, isUndefined } from '@core/utils';
import { moveItemInArray } from '@angular/cdk/drag-drop';
import { Router } from '@angular/router';
import { MqttAuthProviderType, mqttAuthProviderTypeTranslationMap } from '@shared/models/mqtt-auth-provider.model';

@Component({
    selector: 'tb-security-settings',
    templateUrl: './security-settings.component.html',
    styleUrls: ['./security-settings.component.scss'],
    imports: [MatCard, MatCardHeader, MatCardTitle, TranslateModule, MatCardContent, FormsModule, ReactiveFormsModule, MatFormField, MatLabel, MatInput, MatHint, MatCheckbox, HintTooltipIconComponent, MatButton, AsyncPipe, MatIcon, MatSuffix, MatTooltip, HelpComponent, DndDropzoneDirective, DndDraggableDirective, MatChipGrid, MatChipRow, DndDragImageRefDirective, DndHandleDirective, DndPlaceholderRefDirective, MatChipInput, MatIconButton]
})
export class SecuritySettingsComponent extends PageComponent implements OnDestroy, HasConfirmForm {

  securitySettingsForm: UntypedFormGroup;
  mqttAuthSettingsForm: UntypedFormGroup;

  dndId = guid();
  dragIndex: number;
  dragDisabled = false;
  mqttAuthProviderTypeMap = mqttAuthProviderTypeTranslationMap;
  priorities: MqttAuthProviderType[];

  private securitySettings: SecuritySettings;
  private mqttAuthSettings: AdminSettings<MqttAuthSettings>;
  private destroy$ = new Subject<void>();

  constructor(protected store: Store<AppState>,
              private settingsService: SettingsService,
              private renderer: Renderer2,
              private router: Router,
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

  gotoAccountSecurity() {
    this.router.navigate(['account', 'security']);
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
      priorities: [null, []]
    });
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
    this.priorities = settings.jsonValue.priorities;
    this.mqttAuthSettingsForm.reset(this.mqttAuthSettings.jsonValue);
  }

  // TODO move as separate component
  chipDragStart(index: number, chipRow: MatChipRow, placeholderChipRow: Element) {
    this.renderer.setStyle(placeholderChipRow, 'width', chipRow._elementRef.nativeElement.offsetWidth + 'px');
    this.dragIndex = index;
  }

  chipDragEnd() {
    this.dragIndex = -1;
  }

  onChipDrop(event: DndDropEvent) {
    let index = event.index;
    const prioritiesCopy = [...this.priorities];
    if (isUndefined(index)) {
      index = this.priorities.length;
    }
    moveItemInArray(prioritiesCopy, this.dragIndex, index);
    this.mqttAuthSettingsForm.get('priorities').setValue(prioritiesCopy);
    this.priorities = prioritiesCopy;
    this.dragIndex = -1;
  }

  goToProviders() {
    this.router.navigate(['authentication', 'providers']);
  }
}
