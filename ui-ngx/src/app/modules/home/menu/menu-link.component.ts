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

import { ChangeDetectionStrategy, Component, Input } from '@angular/core';
import { MenuSection } from '@core/services/menu.models';
import { MatAnchor } from '@angular/material/button';
import { RouterLinkActive, RouterLink } from '@angular/router';
import { NgIf } from '@angular/common';
import { TbIconComponent } from '@shared/components/icon.component';
import { TranslateModule } from '@ngx-translate/core';

@Component({
    selector: 'tb-menu-link',
    templateUrl: './menu-link.component.html',
    styleUrls: ['./menu-link.component.scss'],
    changeDetection: ChangeDetectionStrategy.OnPush,
    standalone: true,
    imports: [MatAnchor, RouterLinkActive, RouterLink, NgIf, TbIconComponent, TranslateModule]
})
export class MenuLinkComponent {

  @Input() section: MenuSection;

  constructor() {
  }

}
