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

import { AfterViewInit, Component, Input } from '@angular/core';
import { HomePageTitle, homePageTitleConfig, HomePageTitleType } from '@shared/models/home-page.model';
import { Router } from '@angular/router';
import { FlexModule } from '@angular/flex-layout/flex';
import { NgClass, NgIf } from '@angular/common';
import { ExtendedModule } from '@angular/flex-layout/extended';
import { MatIcon } from '@angular/material/icon';
import { MatTooltip } from '@angular/material/tooltip';
import { TranslateModule } from '@ngx-translate/core';

@Component({
    selector: 'tb-card-title-button',
    styleUrls: ['card-title-button.component.scss'],
    templateUrl: './card-title-button.component.html',
    standalone: true,
    imports: [FlexModule, NgClass, ExtendedModule, NgIf, MatIcon, MatTooltip, TranslateModule]
})
export class CardTitleButtonComponent implements AfterViewInit {

  @Input()
  cardType: HomePageTitleType;

  @Input()
  disabled = false;

  homePageTitleResources = homePageTitleConfig;

  title: HomePageTitle;

  constructor(private router: Router) {
  }

  ngAfterViewInit() {
    this.title = this.homePageTitleResources.get(this.cardType);
  }

  navigate() {
    this.router.navigate([this.title.link]);
  }

  gotoDocs(){
    window.open(`https://thingsboard.io/docs/mqtt-broker/${this.title.docsLink}`, '_blank');
  }
}
