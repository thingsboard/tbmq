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
import { UntypedFormBuilder, UntypedFormGroup, Validators, FormsModule, ReactiveFormsModule } from '@angular/forms';
import { Subject } from 'rxjs';
import {
  AdminSettings,
  ConnectivitySettings,
  connectivitySettingsKey,
  WebSocketSettings,
  webSocketSettingsKey
} from '@shared/models/settings.models';
import { SettingsService } from '@core/http/settings.service';
import { takeUntil } from 'rxjs/operators';
import { isUndefined } from '@core/utils';
import { MqttJsClientService } from '@core/http/mqtt-js-client.service';
import { MatCard, MatCardHeader, MatCardTitle, MatCardContent } from '@angular/material/card';
import { TranslateModule } from '@ngx-translate/core';
import { NgTemplateOutlet, AsyncPipe } from '@angular/common';
import { MatSlideToggle } from '@angular/material/slide-toggle';
import { MatIcon } from '@angular/material/icon';
import { MatTooltip } from '@angular/material/tooltip';
import { MatFormField, MatLabel, MatSuffix } from '@angular/material/form-field';
import { MatInput } from '@angular/material/input';
import { MatButton } from '@angular/material/button';
import { ToggleSelectComponent } from '@shared/components/toggle-select.component';
import { ToggleOption } from '@shared/components/toggle-header.component';
import { HasConfirmForm } from '@core/guards/confirm-on-exit.guard';

@Component({
    selector: 'tb-general-settings',
    templateUrl: './general-settings.component.html',
    styleUrls: ['./general-settings.component.scss'],
    imports: [MatCard, MatCardHeader, MatCardTitle, TranslateModule, MatCardContent, FormsModule, ReactiveFormsModule, MatSlideToggle, MatIcon, MatTooltip, MatFormField, MatLabel, MatInput, MatSuffix, MatButton, ToggleSelectComponent, ToggleOption, NgTemplateOutlet, AsyncPipe]
})
export class GeneralSettingsComponent extends PageComponent implements OnDestroy, HasConfirmForm {

  generalSettingsForm: UntypedFormGroup;
  connectivitySettingsForm: UntypedFormGroup;
  protocol = 'mqtt';

  private connectivitySettings: AdminSettings<ConnectivitySettings>;
  private generalSettings: AdminSettings<WebSocketSettings>;
  private destroy$ = new Subject<void>();

  constructor(protected store: Store<AppState>,
              private settingsService: SettingsService,
              private mqttJsClientService: MqttJsClientService,
              public fb: UntypedFormBuilder) {
    super(store);
    this.buildConnectivitySettingsForm();
    this.buildWebSocketSettingsForm();
    this.getSettings();
  }

  ngOnDestroy() {
    this.destroy$.complete();
    super.ngOnDestroy();
  }

  private buildConnectivitySettingsForm() {
    this.connectivitySettingsForm = this.fb.group({
      ws: this.buildConnectivityInfoForm(),
      wss: this.buildConnectivityInfoForm(),
      mqtt: this.buildConnectivityInfoForm(),
      mqtts: this.buildConnectivityInfoForm(),
    });
  }

  private buildConnectivityInfoForm(): UntypedFormGroup {
    const formGroup = this.fb.group({
      enabled: [false, []],
      host: [{value: '', disabled: true}, [Validators.required]],
      port: [{value: null, disabled: true}, [Validators.min(1), Validators.max(65535), Validators.pattern('[0-9]*'), Validators.required]]
    });
    formGroup.get('enabled').valueChanges.pipe(
      takeUntil(this.destroy$)
    ).subscribe(value => {
      if (value) {
        formGroup.get('host').enable({emitEvent: false});
        formGroup.get('port').enable({emitEvent: false});
      } else {
        formGroup.get('host').disable({emitEvent: false});
        formGroup.get('port').disable({emitEvent: false});
      }
    });
    return formGroup;
  }

  private buildWebSocketSettingsForm() {
    this.generalSettingsForm = this.fb.group({
      isLoggingEnabled: [null, []],
      maxMessages: [null, [Validators.required]]
    });
  }

  saveGeneralSettings() {
    let generalSettings: AdminSettings<WebSocketSettings> = JSON.parse(JSON.stringify(this.generalSettings));
    const maxMessagesChanged = this.generalSettings.jsonValue.maxMessages !== this.generalSettingsForm.value.maxMessages;
    if (isUndefined(this.generalSettings)) {
      generalSettings = {
        key: webSocketSettingsKey,
        jsonValue: this.generalSettingsForm.value
      };
    }
    generalSettings.jsonValue = {...generalSettings.jsonValue, ...this.generalSettingsForm.value};
    this.settingsService.saveAdminSettings(generalSettings)
      .subscribe(settings => {
        this.processGeneralSettings(settings);
        if (maxMessagesChanged) {
          this.mqttJsClientService.clearAllMessages();
        }
      });
  }

  saveConnectivitySettings() {
    const settings = JSON.parse(JSON.stringify(this.connectivitySettings)) as AdminSettings<ConnectivitySettings>;
    const form = this.connectivitySettingsForm.value;
    for (const key of Object.keys(form)) {
      settings.jsonValue[key].enabled = form[key].enabled;
      if (form[key].enabled) {
        settings.jsonValue[key].host = this.connectivitySettingsForm.value[key].host;
        settings.jsonValue[key].port = this.connectivitySettingsForm.value[key].port.toString();
      }
    }
    this.settingsService.saveAdminSettings(settings)
      .subscribe(settings => {
        this.processConnectivitySettings(settings);
        this.settingsService.getConnectivitySettings().subscribe();
      });
  }

  discardGeneralSettings(): void {
    const generalSettings = this.generalSettings.jsonValue;
    this.generalSettingsForm.reset(generalSettings);
  }

  discardConnectivitySettings(): void {
    this.connectivitySettingsForm.reset(this.connectivitySettings.jsonValue);
  }

  private processConnectivitySettings(settings: AdminSettings<ConnectivitySettings>): void {
    this.connectivitySettings = settings;
    this.connectivitySettingsForm.reset(this.connectivitySettings.jsonValue);
  }

  private processGeneralSettings(settings: AdminSettings<WebSocketSettings>): void {
    this.generalSettings = settings;
    this.generalSettingsForm.reset(this.generalSettings.jsonValue);
  }

  private getSettings() {
    this.getConnectivitySettings();
    this.getWebSocketGeneralSettings();
  }

  private getConnectivitySettings() {
    this.settingsService.getAdminSettings<ConnectivitySettings>(connectivitySettingsKey).subscribe(settings => this.processConnectivitySettings(settings));
  }

  private getWebSocketGeneralSettings() {
    this.settingsService.getWebSocketSettings().subscribe(settings => this.processGeneralSettings(settings));
  }

  confirmForm(): UntypedFormGroup {
    if (this.generalSettingsForm.dirty) {
      return this.generalSettingsForm;
    }
    return this.connectivitySettingsForm;
  }
}
