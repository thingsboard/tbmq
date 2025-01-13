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

import { ChangeDetectionStrategy, Component, input } from '@angular/core';
import { MenuSection } from '@core/services/menu.models';
import { Router } from '@angular/router';
import { MatAnchor } from '@angular/material/button';
import { NgClass, NgStyle } from '@angular/common';
import { TbIconComponent } from '@shared/components/icon.component';
import { ExtendedModule } from '@angular/flex-layout/extended';
import { MenuLinkComponent } from './menu-link.component';
import { NospacePipe } from '@shared/pipe/nospace.pipe';
import { TranslateModule } from '@ngx-translate/core';

@Component({
    selector: 'tb-menu-toggle',
    templateUrl: './menu-toggle.component.html',
    styleUrls: ['./menu-toggle.component.scss'],
    changeDetection: ChangeDetectionStrategy.OnPush,
    imports: [MatAnchor, TbIconComponent, NgClass, ExtendedModule, NgStyle, MenuLinkComponent, NospacePipe, TranslateModule]
})
export class MenuToggleComponent {

  readonly section = input<MenuSection>();

  constructor(private router: Router) {
  }

  sectionHeight(): string {
    const section = this.section();
    if (section.opened || (!section.opened && this.router.url.indexOf(section.path) > -1)) {
      return section.pages.length * 40 + 'px';
    } else {
      return '0px';
    }
  }

  toggleSection(event: MouseEvent) {
    event.stopPropagation();
    this.section().opened = !this.section().opened;
  }

  trackBySectionPages(index: number, section: MenuSection){
    return section.id;
  }
}
