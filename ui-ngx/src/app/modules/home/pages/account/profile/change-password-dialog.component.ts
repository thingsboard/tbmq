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

import { Component, Inject, NgZone, OnInit } from '@angular/core';
import { MAT_DIALOG_DATA, MatDialogRef, MatDialogClose, MatDialogContent, MatDialogActions } from '@angular/material/dialog';
import { Store } from '@ngrx/store';
import { AppState } from '@core/core.state';
import { AbstractControl, FormGroupDirective, UntypedFormBuilder, UntypedFormGroup, ValidationErrors, ValidatorFn, Validators, FormsModule, ReactiveFormsModule } from '@angular/forms';
import { ActionNotificationShow } from '@core/notification/notification.actions';
import { DialogComponent } from '@shared/components/dialog.component';
import { Router } from '@angular/router';
import { AuthService } from '@core/http/auth.service';
import { DEFAULT_PASSWORD } from '@core/auth/auth.models';
import { UserPasswordPolicy } from '@shared/models/settings.models';
import { isEqual } from '@core/utils';
import { MatToolbar } from '@angular/material/toolbar';
import { TranslateModule } from '@ngx-translate/core';
import { MatIconButton, MatButton } from '@angular/material/button';
import { MatIcon } from '@angular/material/icon';
import { AsyncPipe } from '@angular/common';
import { MatProgressBar } from '@angular/material/progress-bar';
import { ToastDirective } from '@shared/components/toast.directive';
import { MatFormField, MatLabel, MatPrefix, MatSuffix, MatHint } from '@angular/material/form-field';
import { MatInput } from '@angular/material/input';
import { TogglePasswordComponent } from '@shared/components/button/toggle-password.component';
import { MatDivider } from '@angular/material/divider';
import { TbIconComponent } from '@shared/components/icon.component';
import { MatSlideToggle } from '@angular/material/slide-toggle';
import { MatTooltip } from '@angular/material/tooltip';

@Component({
    selector: 'tb-change-password-dialog',
    templateUrl: './change-password-dialog.component.html',
    styleUrls: ['./change-password-dialog.component.scss'],
    imports: [FormsModule, ReactiveFormsModule, MatToolbar, TranslateModule, MatIconButton, MatDialogClose, MatIcon, MatProgressBar, MatDialogContent, ToastDirective, MatFormField, MatLabel, MatInput, MatPrefix, TogglePasswordComponent, MatSuffix, MatHint, MatDivider, TbIconComponent, MatDialogActions, MatSlideToggle, MatButton, AsyncPipe, MatTooltip]
})
export class ChangePasswordDialogComponent extends DialogComponent<ChangePasswordDialogComponent> implements OnInit {

  changePassword: UntypedFormGroup;
  passwordPolicy: UserPasswordPolicy;
  notShowAgain: boolean;

  constructor(protected store: Store<AppState>,
              protected router: Router,
              private authService: AuthService,
              public dialogRef: MatDialogRef<ChangePasswordDialogComponent>,
              private zone: NgZone,
              @Inject(MAT_DIALOG_DATA) public data: any,
              public fb: UntypedFormBuilder) {
    super(store, router, dialogRef);
  }

  ngOnInit(): void {
    this.buildChangePasswordForm();
    if (this.data?.changeDefaultPassword) {
      this.changePassword.patchValue({ currentPassword: DEFAULT_PASSWORD });
    }
    this.loadPasswordPolicy();
  }

  buildChangePasswordForm() {
    this.changePassword = this.fb.group({
      currentPassword: [''],
      newPassword: ['', Validators.required],
      newPassword2: ['', this.samePasswordValidation(false, 'newPassword')]
    });
  }

  onChangePassword(form: FormGroupDirective): void {
    if (this.changePassword.valid) {
      this.authService.changePassword(this.changePassword.get('currentPassword').value,
        this.changePassword.get('newPassword').value, {ignoreErrors: true}).subscribe(() => {
          this.discardChanges(form);
          this.dialogRef.close(true);
        },
        (error) => {
          if (error.status === 400 && error.error.message === 'Current password doesn\'t match!') {
            this.changePassword.get('currentPassword').setErrors({differencePassword: true});
          } else if (error.status === 400 && error.error.message.startsWith('Password must')) {
            this.loadPasswordPolicy();
          } else if (error.status === 400 && error.error.message.startsWith('Password was already used')) {
            this.changePassword.get('newPassword').setErrors({alreadyUsed: error.error.message});
          } else {
            this.store.dispatch(new ActionNotificationShow({
              message: error.error.message,
              type: 'error',
              target: 'changePassword'
            }));
          }
        });
    } else {
      this.changePassword.markAllAsTouched();
    }
  }

  onSkip(): void {
    if (this.notShowAgain) {
      localStorage.setItem('notDisplayChangeDefaultPassword', 'true');
    }
    this.dialogRef.close(true);
    const url = this.authService.defaultUrl(this.data?.isAuthenticated, this.data?.authState);
    this.zone.run(() => {
      this.router.navigateByUrl(url);
    });
  }

  discardChanges(form: FormGroupDirective, event?: MouseEvent) {
    if (event) {
      event.stopPropagation();
    }
    form.resetForm({
      currentPassword: '',
      newPassword: '',
      newPassword2: ''
    });
  }

  private loadPasswordPolicy() {
    this.authService.getUserPasswordPolicy().subscribe(policy => {
      this.passwordPolicy = policy;
      this.changePassword.get('newPassword').setValidators([
        this.passwordStrengthValidator(),
        this.samePasswordValidation(true, 'currentPassword'),
        Validators.required
      ]);
      this.changePassword.get('newPassword').updateValueAndValidity({emitEvent: false});
    });
  }

  private passwordStrengthValidator(): ValidatorFn {
    return (control: AbstractControl): ValidationErrors | null => {
      const value: string = control.value;
      const errors: any = {};

      if (this.passwordPolicy.minimumUppercaseLetters > 0 &&
        !new RegExp(`(?:.*?[A-Z]){${this.passwordPolicy.minimumUppercaseLetters}}`).test(value)) {
        errors.notUpperCase = true;
      }

      if (this.passwordPolicy.minimumLowercaseLetters > 0 &&
        !new RegExp(`(?:.*?[a-z]){${this.passwordPolicy.minimumLowercaseLetters}}`).test(value)) {
        errors.notLowerCase = true;
      }

      if (this.passwordPolicy.minimumDigits > 0
        && !new RegExp(`(?:.*?\\d){${this.passwordPolicy.minimumDigits}}`).test(value)) {
        errors.notNumeric = true;
      }

      if (this.passwordPolicy.minimumSpecialCharacters > 0 &&
        !new RegExp(`(?:.*?[\\W_]){${this.passwordPolicy.minimumSpecialCharacters}}`).test(value)) {
        errors.notSpecial = true;
      }

      if (!this.passwordPolicy.allowWhitespaces && /\s/.test(value)) {
        errors.hasWhitespaces = true;
      }

      if (this.passwordPolicy.minimumLength > 0 && value.length < this.passwordPolicy.minimumLength) {
        errors.minLength = true;
      }

      if (!value.length || this.passwordPolicy.maximumLength > 0 && value.length > this.passwordPolicy.maximumLength) {
        errors.maxLength = true;
      }

      return isEqual(errors, {}) ? null : errors;
    };
  }

  private samePasswordValidation(isSame: boolean, key: string): ValidatorFn {
    return (control: AbstractControl): ValidationErrors | null => {
      const value: string = control.value;
      const keyValue = control.parent?.value[key];

      if (isSame) {
        return value === keyValue ? {samePassword: true} : null;
      }
      return value !== keyValue ? {differencePassword: true} : null;
    };
  }
}
