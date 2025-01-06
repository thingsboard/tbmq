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

import { Component } from '@angular/core';
import { MenuService } from '@core/services/menu.service';
import { HomePageTitleType } from '@shared/models/home-page.model';
import { CardTitleButtonComponent } from '@shared/components/button/card-title-button.component';
import { NgFor, AsyncPipe } from '@angular/common';
import { TbIconComponent } from '@shared/components/icon.component';
import { TranslateModule } from '@ngx-translate/core';

@Component({
    selector: 'tb-quick-links',
    templateUrl: './quick-links.component.html',
    styleUrls: ['./quick-links.component.scss'],
    standalone: true,
    imports: [CardTitleButtonComponent, NgFor, TbIconComponent, AsyncPipe, TranslateModule]
})
export class QuickLinksComponent {

  quickLinks$ = this.menuService.quickLinks();
  cardType = HomePageTitleType.QUICK_LINKS;

  constructor(private menuService: MenuService) {
  }

  navigate(path: string) {
    let location: string;
    if (path === 'rest-api') {
      location = window.location.origin + '/swagger-ui.html';
    } else {
      location = 'https://thingsboard.io/docs/mqtt-broker/' + path;
    }
    window.open(location, '_blank');
  }
}
