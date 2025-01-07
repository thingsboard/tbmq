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

import { ChangeDetectionStrategy, Component } from '@angular/core';
import { MenuService } from '@core/services/menu.service';
import { MenuSection } from '@core/services/menu.models';
import { FlexModule } from '@angular/flex-layout/flex';
import { NgFor, NgSwitch, NgSwitchCase, AsyncPipe } from '@angular/common';
import { MenuLinkComponent } from './menu-link.component';
import { MenuToggleComponent } from './menu-toggle.component';

@Component({
    selector: 'tb-side-menu',
    templateUrl: './side-menu.component.html',
    styleUrls: ['./side-menu.component.scss'],
    changeDetection: ChangeDetectionStrategy.OnPush,
    standalone: true,
    imports: [FlexModule, NgFor, NgSwitch, NgSwitchCase, MenuLinkComponent, MenuToggleComponent, AsyncPipe]
})
export class SideMenuComponent {

  menuSections$ = this.menuService.menuSections();

  constructor(private menuService: MenuService) {
  }

  trackByMenuSection(index: number, section: MenuSection){
    return section.id;
  }
}
