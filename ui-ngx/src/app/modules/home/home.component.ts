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

import { Component, ElementRef, Inject, OnDestroy, OnInit, ViewChild } from '@angular/core';
import { Store } from '@ngrx/store';

import { BreakpointObserver, BreakpointState } from '@angular/cdk/layout';
import { PageComponent } from '@shared/components/page.component';
import { AppState } from '@core/core.state';
import { MediaBreakpoints } from '@shared/models/constants';
import screenfull from 'screenfull';
import { MatSidenav, MatSidenavContainer, MatSidenavContent } from '@angular/material/sidenav';
import { WINDOW } from '@core/services/window.service';
import { UntypedFormBuilder } from '@angular/forms';
import { takeUntil } from 'rxjs/operators';
import { Subject } from 'rxjs';
import { ActiveComponentService } from '@core/services/active-component.service';
import { MatToolbar } from '@angular/material/toolbar';
import { FlexModule } from '@angular/flex-layout/flex';
import { SideMenuComponent } from './menu/side-menu.component';
import { GettingStartedMenuLinkComponent } from './pages/getting-started/getting-started-menu-link.component';
import { MatIconButton } from '@angular/material/button';
import { ExtendedModule } from '@angular/flex-layout/extended';
import { MatIcon } from '@angular/material/icon';
import { BreadcrumbComponent } from '@shared/components/breadcrumb.component';
import { NgIf, AsyncPipe } from '@angular/common';
import { UserMenuComponent } from '@shared/components/user-menu.component';
import { MatProgressBar } from '@angular/material/progress-bar';
import { ToastDirective } from '@shared/components/toast.directive';
import { RouterOutlet } from '@angular/router';

@Component({
    selector: 'tb-home',
    templateUrl: './home.component.html',
    styleUrls: ['./home.component.scss'],
    imports: [MatSidenavContainer, MatSidenav, MatToolbar, FlexModule, SideMenuComponent, GettingStartedMenuLinkComponent, MatSidenavContent, MatIconButton, ExtendedModule, MatIcon, BreadcrumbComponent, NgIf, UserMenuComponent, MatProgressBar, ToastDirective, RouterOutlet, AsyncPipe]
})
export class HomeComponent extends PageComponent implements OnInit, OnDestroy {

  activeComponent: any;

  sidenavMode: 'over' | 'push' | 'side' = 'side';
  sidenavOpened = true;

  logo = 'assets/mqtt_logo_white.svg';

  @ViewChild('sidenav')
  sidenav: MatSidenav;

  @ViewChild('searchInput') searchInputField: ElementRef;

  fullscreenEnabled = screenfull.isEnabled;

  private destroy$ = new Subject<void>();

  constructor(protected store: Store<AppState>,
              @Inject(WINDOW) private window: Window,
              private activeComponentService: ActiveComponentService,
              private fb: UntypedFormBuilder,
              public breakpointObserver: BreakpointObserver) {
    super(store);
  }

  ngOnInit() {
    const isGtSm = this.breakpointObserver.isMatched(MediaBreakpoints['gt-sm']);
    this.sidenavMode = isGtSm ? 'side' : 'over';
    this.sidenavOpened = isGtSm;

    this.breakpointObserver
      .observe(MediaBreakpoints['gt-sm'])
      .pipe(takeUntil(this.destroy$))
      .subscribe((state: BreakpointState) => {
          if (state.matches) {
            this.sidenavMode = 'side';
            this.sidenavOpened = true;
          } else {
            this.sidenavMode = 'over';
            this.sidenavOpened = false;
          }
        }
      );
    this.toggleFullscreenOnF11();
  }

  ngOnDestroy() {
    this.destroy$.next();
    this.destroy$.complete();
  }

  sidenavClicked() {
    if (this.sidenavMode === 'over') {
      this.sidenav.toggle();
    }
  }

  toggleFullscreen() {
    if (screenfull.isEnabled) {
      screenfull.toggle();
    }
  }

  isFullscreen() {
    return screenfull.isFullscreen;
  }

  activeComponentChanged(activeComponent: any) {
    this.activeComponentService.setCurrentActiveComponent(activeComponent);
    if (!this.activeComponent) {
      setTimeout(() => {
        this.updateActiveComponent(activeComponent);
      }, 0);
    } else {
      this.updateActiveComponent(activeComponent);
    }
  }

  private toggleFullscreenOnF11() {
    $(document).on('keydown',
      (event) => {
        if (event.key === 'F11') {
          event.preventDefault();
          this.toggleFullscreen();
        }
      });
  }

  private updateActiveComponent(activeComponent: any) {
    this.activeComponent = activeComponent;
  }
}
