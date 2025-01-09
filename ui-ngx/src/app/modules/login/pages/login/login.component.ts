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

import { Component } from '@angular/core';
import { Store } from '@ngrx/store';
import { AppState } from '@core/core.state';
import { PageComponent } from '@shared/components/page.component';
import { UntypedFormBuilder, FormsModule, ReactiveFormsModule } from '@angular/forms';
import { HttpErrorResponse } from '@angular/common/http';
import { Constants } from '@shared/models/constants';
import { Router, RouterLink } from '@angular/router';
import { AuthService } from '@core/http/auth.service';
import { FlexModule } from '@angular/flex-layout/flex';
import { MatCard, MatCardContent } from '@angular/material/card';
import { LogoComponent } from '@shared/components/logo.component';
import { NgIf, AsyncPipe } from '@angular/common';
import { MatProgressBar } from '@angular/material/progress-bar';
import { ToastDirective } from '@shared/components/toast.directive';
import { MatDivider } from '@angular/material/divider';
import { MatFormField, MatLabel, MatPrefix, MatError, MatSuffix } from '@angular/material/form-field';
import { TranslateModule } from '@ngx-translate/core';
import { MatInput } from '@angular/material/input';
import { MatIcon } from '@angular/material/icon';
import { TogglePasswordComponent } from '@shared/components/button/toggle-password.component';
import { MatButton } from '@angular/material/button';
import { FooterComponent } from '@shared/components/footer.component';

@Component({
    selector: 'tb-login',
    templateUrl: './login.component.html',
    styleUrls: ['./login.component.scss'],
    imports: [FlexModule, MatCard, MatCardContent, FormsModule, ReactiveFormsModule, LogoComponent, NgIf, MatProgressBar, ToastDirective, MatDivider, MatFormField, MatLabel, TranslateModule, MatInput, MatIcon, MatPrefix, MatError, TogglePasswordComponent, MatSuffix, MatButton, RouterLink, FooterComponent, AsyncPipe]
})
export class LoginComponent extends PageComponent {

  loginFormGroup = this.fb.group({
    username: '',
    password: ''
  });

  constructor(protected store: Store<AppState>,
              private authService: AuthService,
              public fb: UntypedFormBuilder,
              private router: Router) {
    super(store);
  }

  login(): void {
    if (this.loginFormGroup.valid) {
      this.authService.login(this.loginFormGroup.value).subscribe(
        () => {},
        (error: HttpErrorResponse) => {
          if (error && error.error && error.error.errorCode) {
            if (error.error.errorCode === Constants.serverErrorCode.credentialsExpired) {
              this.router.navigateByUrl(`login/resetExpiredPassword?resetToken=${error.error.resetToken}`);
            }
          }
        }
      );
    } else {
      Object.keys(this.loginFormGroup.controls).forEach(field => {
        const control = this.loginFormGroup.get(field);
        control.markAsTouched({onlySelf: true});
      });
    }
  }

}
