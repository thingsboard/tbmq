///
/// Copyright © 2016-2026 The Thingsboard Authors
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

import 'hammerjs';

import { Component, NgZone } from '@angular/core';

import { environment as env } from '@env/environment';

import { TranslateService } from '@ngx-translate/core';
import { select, Store } from '@ngrx/store';
import { AppState } from '@core/core.state';
import { LocalStorageService } from '@core/local-storage/local-storage.service';
import { DomSanitizer } from '@angular/platform-browser';
import { MatIconRegistry } from '@angular/material/icon';
import { combineLatest } from 'rxjs';
import { getCurrentAuthState, selectIsAuthenticated, selectIsUserLoaded } from '@core/auth/auth.selectors';
import { distinctUntilChanged, filter, map, skip } from 'rxjs/operators';
import { AuthService } from '@core/http/auth.service';
import { ChangePasswordDialogComponent } from '@home/pages/account/profile/change-password-dialog.component';
import { MatDialog } from '@angular/material/dialog';
import { Router, RouterOutlet } from '@angular/router';
import { svgIcons, svgIconsUrl } from '@shared/models/icon.models';
import { AuthState } from '@core/auth/auth.models';
import { GettingStartedGuideDialogComponent } from '@home/pages/getting-started/getting-started-guide-dialog.component';

@Component({
    selector: 'tb-root',
    templateUrl: './app.component.html',
    styleUrls: ['./app.component.scss'],
    imports: [RouterOutlet]
})
export class AppComponent {

  constructor(private store: Store<AppState>,
              private storageService: LocalStorageService,
              private translate: TranslateService,
              private matIconRegistry: MatIconRegistry,
              private domSanitizer: DomSanitizer,
              private authService: AuthService,
              private dialog: MatDialog,
              private zone: NgZone,
              private router: Router) {

    console.log(`TBMQ Version: ${env.tbmqVersion}`);

    this.matIconRegistry.addSvgIconResolver((name, namespace) => {
      if (namespace === 'mdi') {
        return this.domSanitizer.bypassSecurityTrustResourceUrl(`./assets/mdi/${name}.svg`);
      } else {
        return null;
      }
    });

    for (const svgIcon of Object.keys(svgIcons)) {
      this.matIconRegistry.addSvgIconLiteral(
          svgIcon,
          this.domSanitizer.bypassSecurityTrustHtml(
              svgIcons[svgIcon]
          )
      );
    }

    for (const svgIcon of Object.keys(svgIconsUrl)) {
      this.matIconRegistry.addSvgIcon(svgIcon, this.domSanitizer.bypassSecurityTrustResourceUrl(svgIconsUrl[svgIcon]));
    }

    this.storageService.testLocalStorage();

    this.setupTranslate();
    this.setupAuth();
  }

  setupTranslate() {
    if (!env.production) {
      console.log(`Supported Langs: ${env.supportedLangs}`);
    }
    this.translate.addLangs(env.supportedLangs);
    if (!env.production) {
      console.log(`Default Lang: ${env.defaultLang}`);
    }
    this.translate.setDefaultLang(env.defaultLang);
  }

  setupAuth() {
    combineLatest([
      this.store.pipe(select(selectIsAuthenticated)),
      this.store.pipe(select(selectIsUserLoaded))]
    ).pipe(
      map(results => ({isAuthenticated: results[0], isUserLoaded: results[1]})),
      distinctUntilChanged(),
      filter((data) => data.isUserLoaded),
      skip(1),
    ).subscribe((data) => {
      this.gotoDefaultPlace(data.isAuthenticated);
    });
    this.authService.reloadUser();
  }

  onActivateComponent($event: any) {
    const loadingElement = $('div#tb-loading-spinner');
    if (loadingElement.length) {
      loadingElement.remove();
    }
  }

  private gotoDefaultPlace(isAuthenticated: boolean) {
    const authState = getCurrentAuthState(this.store);
    if (this.userHasDefaultPassword(authState) && !localStorage.getItem('notDisplayChangeDefaultPassword')) {
      this.dialog.open(ChangePasswordDialogComponent, {
        disableClose: true,
        panelClass: ['tb-dialog', 'tb-fullscreen-dialog'],
        hasBackdrop: true,
        backdropClass: ['tb-fullscreen-backdrop'],
        data: {
          changeDefaultPassword: true,
          isAuthenticated,
          authState
        }
      });
    } else {
      const url = this.authService.defaultUrl(isAuthenticated, authState);
      this.zone.run(() => {
        this.router.navigateByUrl(url);
        this.gettingStartedGuide(isAuthenticated);
      });
    }
  }

  private userHasDefaultPassword(authState: AuthState): boolean {
    return authState?.userDetails?.additionalInfo?.isPasswordChanged === false;
  }

  private gettingStartedGuide(isAuthenticated: boolean) {
    if (isAuthenticated && !localStorage.getItem('notDisplayGettingStartedGuide')) {
      this.dialog.open<GettingStartedGuideDialogComponent>(
        GettingStartedGuideDialogComponent, {
          disableClose: true,
          panelClass: ['tb-dialog', 'tb-fullscreen-dialog']
        })
        .afterClosed()
        .subscribe();
    }
  }

}
