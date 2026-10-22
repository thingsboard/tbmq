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

import { booleanAttribute, ChangeDetectionStrategy, Component, computed, inject, input } from '@angular/core';
import { toSignal } from '@angular/core/rxjs-interop';
import { MenuSection } from '@core/services/menu.models';
import { NavigationEnd, Router, RouterLink, RouterLinkActive } from '@angular/router';
import { MatAnchor } from '@angular/material/button';
import { TbIconComponent } from '@shared/components/icon.component';
import { MenuLinkComponent } from './menu-link.component';
import { NospacePipe } from '@shared/pipe/nospace.pipe';
import { TranslateModule } from '@ngx-translate/core';
import { TbPopoverDirective } from '@shared/components/popover.component';
import { filter, map } from 'rxjs/operators';

@Component({
    selector: 'tb-menu-toggle',
    templateUrl: './menu-toggle.component.html',
    styleUrls: ['./menu-toggle.component.scss'],
    changeDetection: ChangeDetectionStrategy.OnPush,
    imports: [MatAnchor, TbIconComponent, MenuLinkComponent, NospacePipe, TranslateModule, TbPopoverDirective, RouterLink, RouterLinkActive]
})
export class MenuToggleComponent {

  readonly section = input<MenuSection>();

  readonly collapsed = input(false, { transform: booleanAttribute });

  private readonly router = inject(Router);

  private readonly currentUrl = toSignal(this.router.events.pipe(
    filter(event => event instanceof NavigationEnd),
    map(() => this.router.url)
  ), { initialValue: this.router.url });

  readonly active = computed(() => this.currentUrl().startsWith(this.section().path));

  isOpened(): boolean {
    return !this.collapsed() && (this.section().opened || this.active());
  }

  sectionHeight(): string {
    return this.isOpened() ? this.section().pages.length * 40 + 'px' : '0px';
  }

  toggleSection(event: MouseEvent) {
    event.stopPropagation();
    if (this.collapsed()) {
      event.preventDefault();
    } else if (!this.active()) {
      this.section().opened = !this.section().opened;
    }
  }
}
