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

import { Component, OnDestroy, OnInit } from '@angular/core';
import { Store } from '@ngrx/store';
import { AppState } from '@core/core.state';
import { PageComponent } from '@shared/components/page.component';
import { UntypedFormBuilder, UntypedFormGroup, Validators, FormsModule, ReactiveFormsModule } from '@angular/forms';
import { AdminSettings, MailServerSettings, smtpPortPattern } from '@shared/models/settings.models';
import { MailServerService } from '@core/http/mail-server.service';
import { ActionNotificationShow } from '@core/notification/notification.actions';
import { TranslateService, TranslateModule } from '@ngx-translate/core';
import { HasConfirmForm } from '@core/guards/confirm-on-exit.guard';
import { isDefinedAndNotNull, isString } from '@core/utils';
import { Subject } from 'rxjs';
import { takeUntil } from 'rxjs/operators';
import { MatCard, MatCardHeader, MatCardTitle, MatCardContent } from '@angular/material/card';
import { HelpComponent } from '@shared/components/help.component';
import { AsyncPipe } from '@angular/common';
import { MatFormField, MatLabel, MatHint, MatSuffix } from '@angular/material/form-field';
import { MatInput } from '@angular/material/input';
import { MatExpansionPanel, MatExpansionPanelHeader, MatExpansionPanelTitle, MatExpansionPanelContent } from '@angular/material/expansion';
import { MatSelect } from '@angular/material/select';
import { MatOption } from '@angular/material/core';
import { MatSlideToggle } from '@angular/material/slide-toggle';
import { TogglePasswordComponent } from '@shared/components/button/toggle-password.component';
import { MatCheckbox } from '@angular/material/checkbox';
import { MatButton } from '@angular/material/button';
import { MatIcon } from '@angular/material/icon';
import { MatTooltip } from '@angular/material/tooltip';

@Component({
    selector: 'tb-mail-server',
    templateUrl: './mail-server.component.html',
    styleUrls: ['./mail-server.component.scss'],
    imports: [MatCard, MatCardHeader, MatCardTitle, TranslateModule, HelpComponent, MatCardContent, FormsModule, ReactiveFormsModule, MatFormField, MatLabel, MatInput, MatExpansionPanel, MatExpansionPanelHeader, MatExpansionPanelTitle, MatExpansionPanelContent, MatSelect, MatOption, MatHint, MatSlideToggle, TogglePasswordComponent, MatSuffix, MatCheckbox, MatButton, AsyncPipe, MatIcon, MatTooltip]
})
export class MailServerComponent extends PageComponent implements OnInit, OnDestroy, HasConfirmForm {

  mailSettings: UntypedFormGroup;
  adminSettings: AdminSettings<MailServerSettings>;
  smtpProtocols = ['smtp', 'smtps'];
  tlsVersions = ['TLSv1', 'TLSv1.1', 'TLSv1.2', 'TLSv1.3'];

  private destroy$ = new Subject<void>();

  constructor(protected store: Store<AppState>,
              private adminService: MailServerService,
              private translate: TranslateService,
              public fb: UntypedFormBuilder) {
    super(store);
  }

  ngOnInit() {
    this.buildMailServerSettingsForm();
    this.adminService.getUserSettings<MailServerSettings>('mail').subscribe(
      (adminSettings) => {
        this.adminSettings = adminSettings;
        if (this.adminSettings.jsonValue && isString(this.adminSettings.jsonValue.enableTls)) {
          this.adminSettings.jsonValue.enableTls = (this.adminSettings.jsonValue.enableTls as any) === 'true';
        }
        this.mailSettings.reset(this.adminSettings.jsonValue);
        this.enableProxyChanged();
      }
    );
  }

  ngOnDestroy() {
    this.destroy$.complete();
    super.ngOnDestroy();
  }

  buildMailServerSettingsForm() {
    this.mailSettings = this.fb.group({
      mailFrom: ['', [Validators.required]],
      smtpProtocol: ['smtp'],
      smtpHost: ['localhost', [Validators.required]],
      smtpPort: ['25', [Validators.required,
        Validators.pattern(smtpPortPattern),
        Validators.maxLength(5)]],
      timeout: ['10000', [Validators.required,
        Validators.pattern(/^[0-9]{1,6}$/),
        Validators.maxLength(6)]],
      enableTls: [false],
      tlsVersion: [],
      enableProxy: [false, []],
      proxyHost: ['', [Validators.required]],
      proxyPort: ['', [Validators.required, Validators.min(1), Validators.max(65535)]],
      proxyUser: [''],
      proxyPassword: [''],
      username: [''],
      changePassword: [false],
      password: ['']
    });
    this.registerDisableOnLoadFormControl(this.mailSettings.get('smtpProtocol'));
    this.registerDisableOnLoadFormControl(this.mailSettings.get('enableTls'));
    this.registerDisableOnLoadFormControl(this.mailSettings.get('enableProxy'));
    this.registerDisableOnLoadFormControl(this.mailSettings.get('changePassword'));
    this.mailSettings.get('enableProxy').valueChanges.pipe(
      takeUntil(this.destroy$)
    ).subscribe(() => {
      this.enableProxyChanged();
    });
    this.mailSettings.get('changePassword').valueChanges.pipe(
      takeUntil(this.destroy$)
    ).subscribe((value) => {
      this.enableMailPassword(value);
    });
  }

  enableProxyChanged(): void {
    const enableProxy: boolean = this.mailSettings.get('enableProxy').value;
    if (enableProxy) {
      this.mailSettings.get('proxyHost').enable();
      this.mailSettings.get('proxyPort').enable();
    } else {
      this.mailSettings.get('proxyHost').disable();
      this.mailSettings.get('proxyPort').disable();
    }
  }

  enableMailPassword(enable: boolean) {
    if (enable) {
      this.mailSettings.get('password').enable({emitEvent: false});
    } else {
      this.mailSettings.get('password').disable({emitEvent: false});
    }
  }

  sendTestMail(): void {
    this.adminSettings.jsonValue = {...this.adminSettings.jsonValue, ...this.mailSettingsFormValue};
    this.adminService.sendTestMail(this.adminSettings).subscribe(
      () => {
        this.store.dispatch(new ActionNotificationShow({
          message: this.translate.instant('admin.test-mail-sent'),
          type: 'success'
        }));
      }
    );
  }

  save(): void {
    this.adminSettings.jsonValue = {...this.adminSettings.jsonValue, ...this.mailSettingsFormValue};
    this.adminService.saveUserSettings(this.adminSettings).subscribe(
      (adminSettings) => {
        this.adminSettings = adminSettings;
        this.mailSettings.reset(this.adminSettings.jsonValue);
      }
    );
  }

  confirmForm(): UntypedFormGroup {
    return this.mailSettings;
  }

  private get mailSettingsFormValue(): MailServerSettings {
    const formValue = this.mailSettings.value;
    delete formValue.changePassword;
    if (!isDefinedAndNotNull(formValue.password)) {
      delete formValue.password;
    }
    return formValue;
  }
}
