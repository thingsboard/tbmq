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

import {
  AfterViewInit,
  Component,
  computed,
  ElementRef,
  NgZone,
  OnDestroy,
  OnInit,
  Renderer2,
  signal,
  viewChild
} from '@angular/core';
import { Store } from '@ngrx/store';

import { BreakpointObserver, BreakpointState } from '@angular/cdk/layout';
import { PageComponent } from '@shared/components/page.component';
import { AppState } from '@core/core.state';
import { MediaBreakpoints } from '@shared/models/constants';
import screenfull from 'screenfull';
import { MatSidenav, MatSidenavContainer, MatSidenavContent } from '@angular/material/sidenav';
import { takeUntil } from 'rxjs/operators';
import { Subject } from 'rxjs';
import { ActiveComponentService } from '@core/services/active-component.service';
import { MatToolbar } from '@angular/material/toolbar';
import { SideMenuComponent } from './menu/side-menu.component';
import { GettingStartedMenuLinkComponent } from './pages/getting-started/getting-started-menu-link.component';
import { MatIconButton } from '@angular/material/button';
import { MatIcon } from '@angular/material/icon';
import { BreadcrumbComponent } from '@shared/components/breadcrumb.component';
import { AsyncPipe } from '@angular/common';
import { UserMenuComponent } from '@shared/components/user-menu.component';
import { MatProgressBar } from '@angular/material/progress-bar';
import { ToastDirective } from '@shared/components/toast.directive';
import { RouterOutlet } from '@angular/router';
import { GithubBadgeComponent } from '@home/components/github-badge/github-badge.component';
import { LogoComponent } from '@shared/components/logo.component';
import { DialogService } from '@core/services/dialog.service';
import { GotoMenuComponent } from './menu/goto-menu.component';
import { TbIconComponent } from '@shared/components/icon.component';
import { LocalStorageService } from '@core/local-storage/local-storage.service';

const MENU_COLLAPSED_KEY = 'MENU_COLLAPSED';

@Component({
    selector: 'tb-home',
    templateUrl: './home.component.html',
    styleUrls: ['./home.component.scss'],
    imports: [MatSidenavContainer, MatSidenav, MatToolbar, SideMenuComponent, GettingStartedMenuLinkComponent, MatSidenavContent, MatIconButton, MatIcon, BreadcrumbComponent, UserMenuComponent, GithubBadgeComponent, MatProgressBar, ToastDirective, RouterOutlet, AsyncPipe, LogoComponent, GotoMenuComponent, TbIconComponent]
})
export class HomeComponent extends PageComponent implements OnInit, AfterViewInit, OnDestroy {

  activeComponent: any;

  sidenavDesktop = signal(true);
  sidenavMode = computed<'over' | 'side'>(() => this.sidenavDesktop() ? 'side' : 'over');
  sidenavOpened = computed(() => this.sidenavDesktop());
  sidenavCollapsed = signal(this.localStorageService.getItem(MENU_COLLAPSED_KEY) === true);
  menuCollapsed = computed(() => this.sidenavDesktop() && this.sidenavCollapsed());

  readonly sidenav = viewChild<MatSidenav>('sidenav');
  readonly navHeader = viewChild.required<ElementRef<HTMLElement>>('navHeader');
  readonly sidebarScroll = viewChild.required<ElementRef<HTMLElement>>('sidebarScroll');
  readonly sideMenu = viewChild.required('sideMenu', { read: ElementRef<HTMLElement> });
  readonly sidebarBottom = viewChild.required<ElementRef<HTMLElement>>('sidebarBottom');

  fullscreenEnabled = screenfull.isEnabled;

  private scrollShadowWatcher: ResizeObserver;
  private sidebarScrollUnlisten: () => void;

  private destroy$ = new Subject<void>();

  constructor(
    protected store: Store<AppState>,
    private activeComponentService: ActiveComponentService,
    private renderer: Renderer2,
    private ngZone: NgZone,
    private localStorageService: LocalStorageService,
    public breakpointObserver: BreakpointObserver,
    private dialogService: DialogService,
  ) {
    super(store);
  }

  ngOnInit() {
    this.sidenavDesktop.set(this.breakpointObserver.isMatched(MediaBreakpoints['gt-sm']));
    this.breakpointObserver
      .observe(MediaBreakpoints['gt-sm'])
      .pipe(takeUntil(this.destroy$))
      .subscribe((state: BreakpointState) => this.sidenavDesktop.set(state.matches));
    this.toggleFullscreenOnF11();
    this.dialogService.gettingStarted();
  }

  ngAfterViewInit() {
    this.ngZone.runOutsideAngular(() => {
      this.scrollShadowWatcher = new ResizeObserver(() => this.updateScrollShadows());
      this.scrollShadowWatcher.observe(this.sideMenu().nativeElement);
      this.scrollShadowWatcher.observe(this.sidebarScroll().nativeElement);
      this.sidebarScrollUnlisten = this.renderer.listen(this.sidebarScroll().nativeElement, 'scroll', () => this.updateScrollShadows());
    });
  }

  ngOnDestroy() {
    this.scrollShadowWatcher?.disconnect();
    this.sidebarScrollUnlisten?.();
    this.destroy$.next();
    this.destroy$.complete();
  }

  sidenavClicked() {
    if (this.sidenavMode() === 'over') {
      this.sidenav().toggle();
    }
  }

  toggleSidenav() {
    this.sidenavCollapsed.update(collapsed => !collapsed);
    this.localStorageService.setItem(MENU_COLLAPSED_KEY, this.sidenavCollapsed());
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

  private updateScrollShadows() {
    const sidebarScrollElm = this.sidebarScroll().nativeElement;
    if (sidebarScrollElm.scrollTop > 0) {
      this.renderer.addClass(this.navHeader().nativeElement, 'tb-scrolled');
    } else {
      this.renderer.removeClass(this.navHeader().nativeElement, 'tb-scrolled');
    }
    const moreBelow = sidebarScrollElm.scrollTop + sidebarScrollElm.clientHeight < sidebarScrollElm.scrollHeight - 1;
    if (moreBelow) {
      this.renderer.addClass(this.sidebarBottom().nativeElement, 'tb-scrolled');
    } else {
      this.renderer.removeClass(this.sidebarBottom().nativeElement, 'tb-scrolled');
    }
  }

  private updateActiveComponent(activeComponent: any) {
    this.activeComponent = activeComponent;
  }
}
