///
/// Copyright © 2016-2023 The Thingsboard Authors
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

import { AfterViewInit, ChangeDetectorRef, Component, Input } from '@angular/core';
import { TranslateService } from '@ngx-translate/core';
import { HomePageTitle, homePageTitleConfig, HomePageTitleType } from '@shared/models/home-page.model';
import { Router } from '@angular/router';

@Component({
  selector: 'tb-card-title-button',
  styleUrls: ['card-title-button.component.scss'],
  templateUrl: './card-title-button.component.html'
})
export class CardTitleButtonComponent implements AfterViewInit {

  @Input()
  type: HomePageTitleType;

  @Input()
  disabled = false;

  homePageTitleResources = homePageTitleConfig;

  title: HomePageTitle;

  constructor(private router: Router,
              private translate: TranslateService,
              private cd: ChangeDetectorRef) {
  }

  ngAfterViewInit() {
    this.title = this.homePageTitleResources.get(this.type);
  }

  navigate() {
    this.router.navigate([this.title.link]);
  }

}
