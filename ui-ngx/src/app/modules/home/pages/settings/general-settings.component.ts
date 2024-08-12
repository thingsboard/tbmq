///
/// Copyright © 2016-2024 The Thingsboard Authors
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
import { UntypedFormBuilder, UntypedFormGroup, Validators } from '@angular/forms';
import { Subject } from 'rxjs';
import {
  AdminSettings,
  ConnectivitySettings,
  connectivitySettingsKey,
  WebsocketSettings,
  websocketSettingsKey
} from '@shared/models/settings.models';
import { SettingsService } from '@core/http/settings.service';
import { takeUntil } from 'rxjs/operators';
import { isUndefined } from '@core/utils';

@Component({
  selector: 'tb-general-settings',
  templateUrl: './general-settings.component.html',
  styleUrls: ['./general-settings.component.scss']
})
export class GeneralSettingsComponent extends PageComponent implements OnDestroy {

  generalSettingsForm: UntypedFormGroup;
  connectivitySettingsForm: UntypedFormGroup;
  protocol = 'mqtt';

  private connectivitySettings: AdminSettings<ConnectivitySettings>;
  private generalSettings: AdminSettings<WebsocketSettings>;
  private destroy$ = new Subject<void>();
  private defaultWebsocketSettings: WebsocketSettings = {
    isLoggingEnabled: false
  }

  constructor(protected store: Store<AppState>,
              private settingsService: SettingsService,
              public fb: UntypedFormBuilder) {
    super(store);
    this.buildConnectivitySettingsForm();
    this.buildWebsocketSettingsForm();
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

  private buildWebsocketSettingsForm() {
    this.generalSettingsForm = this.fb.group({
      isLoggingEnabled: [null, []]
    });
  }

  saveGeneralSettings() {
    if (isUndefined(this.generalSettings)) {
      this.generalSettings = {
        key: websocketSettingsKey,
        jsonValue: this.generalSettingsForm.value
      };
    }
    this.generalSettings.jsonValue = {...this.generalSettings.jsonValue, ...this.generalSettingsForm.value};
    this.settingsService.saveAdminSettings(this.generalSettings)
      .subscribe(settings => this.processGeneralSettings(settings));

  }

  saveConnectivitySettings() {
    this.connectivitySettings.jsonValue = {...this.connectivitySettings.jsonValue, ...this.connectivitySettingsForm.value};
    this.settingsService.saveAdminSettings(this.connectivitySettings)
      .subscribe(settings => this.processConnectivitySettings(settings));
  }

  discardGeneralSettings(): void {
    const generalSettings = this.generalSettings ? this.generalSettings.jsonValue : this.defaultWebsocketSettings;
    this.generalSettingsForm.reset(generalSettings);
  }

  discardConnectivitySettings(): void {
    this.connectivitySettingsForm.reset(this.connectivitySettings.jsonValue);
  }

  private processConnectivitySettings(settings: AdminSettings<ConnectivitySettings>): void {
    this.connectivitySettings = settings;
    this.connectivitySettingsForm.reset(this.connectivitySettings.jsonValue);
  }

  private processGeneralSettings(settings: AdminSettings<WebsocketSettings>): void {
    this.generalSettings = settings;
    this.generalSettingsForm.reset(this.generalSettings.jsonValue);
  }

  private getSettings() {
    this.getConnectivitySettings();
    this.getWebsocketGeneralSettings();
  }

  private getConnectivitySettings() {
    this.settingsService.getGeneralSettings<ConnectivitySettings>(connectivitySettingsKey)
      .subscribe(settings => this.processConnectivitySettings(settings));
  }

  private getWebsocketGeneralSettings() {
    this.settingsService.getGeneralSettings<WebsocketSettings>(websocketSettingsKey)
      .subscribe(
        settings => this.processGeneralSettings(settings),
        error => {
          if (error.status === 404) {
            this.saveDefaultWebsocketGeneralSettings();
          }
        }
      );
  }

  private saveDefaultWebsocketGeneralSettings() {
    const settings: AdminSettings<WebsocketSettings> = {
      key: websocketSettingsKey,
      jsonValue: this.defaultWebsocketSettings
    }
    this.settingsService.saveAdminSettings(settings).subscribe(() => this.getWebsocketGeneralSettings());
  }

}
