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

import { AfterViewInit, Component, input, model, signal } from '@angular/core';
import { HomePageTitle, homePageTitleConfig, HomePageTitleType } from '@shared/models/home-page.model';
import { Router } from '@angular/router';
import { MatIcon } from '@angular/material/icon';
import { MatTooltip } from '@angular/material/tooltip';
import { TranslateModule } from '@ngx-translate/core';
import { HelpLinks } from '@shared/models/constants';

@Component({
    selector: 'tb-card-title-button',
    styleUrls: ['card-title-button.component.scss'],
    templateUrl: './card-title-button.component.html',
    imports: [MatIcon, MatTooltip, TranslateModule]
})
export class CardTitleButtonComponent implements AfterViewInit {

  title = signal<HomePageTitle>(null);
  readonly cardType = model<HomePageTitleType>();
  readonly disabled = input(false);
  readonly homePageTitleResources = homePageTitleConfig;

  constructor(private router: Router) {
  }

  ngAfterViewInit() {
    this.title.set(this.homePageTitleResources.get(this.cardType()));
  }

  navigate() {
    this.router.navigate([this.title().link]);
  }

  gotoDocs() {
    const link = HelpLinks.linksMap[this.title().docsLink];
    window.open(link, '_blank');
  }
}
