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

import { ChangeDetectionStrategy, Component, Input } from '@angular/core';
import { MenuSection } from '@core/services/menu.models';
import { Router } from '@angular/router';
import { MatAnchor } from '@angular/material/button';
import { NgIf, NgClass, NgStyle, NgFor } from '@angular/common';
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
    standalone: true,
    imports: [MatAnchor, NgIf, TbIconComponent, NgClass, ExtendedModule, NgStyle, NgFor, MenuLinkComponent, NospacePipe, TranslateModule]
})
export class MenuToggleComponent {

  @Input() section: MenuSection;

  constructor(private router: Router) {
  }

  sectionHeight(): string {
    if (this.section.opened || (!this.section.opened && this.router.url.indexOf(this.section.path) > -1)) {
      return this.section.pages.length * 40 + 'px';
    } else {
      return '0px';
    }
  }

  toggleSection(event: MouseEvent) {
    event.stopPropagation();
    this.section.opened = !this.section.opened;
  }

  trackBySectionPages(index: number, section: MenuSection){
    return section.id;
  }
}
