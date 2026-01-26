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

import { ChangeDetectionStrategy, ChangeDetectorRef, Component, OnInit, AfterViewInit } from '@angular/core';
import { UntypedFormBuilder } from '@angular/forms';
import { Router } from '@angular/router';
import {
  gettingStartedActions,
  gettingStartedDocs,
  gettingStartedFeatures,
  gettingStartedGuides,
  GettingStartedLink
} from '@shared/models/getting-started.model';
import { ConfigService } from '@core/http/config.service';
import { animatedScroll } from '@core/utils';
import { MatCard, MatCardHeader, MatCardTitle, MatCardContent } from '@angular/material/card';
import { TranslateModule } from '@ngx-translate/core';

import { MatButton } from '@angular/material/button';
import { docsPath, helpBaseUrl } from '@shared/models/constants';

@Component({
    selector: 'tb-getting-started',
    templateUrl: './getting-started.component.html',
    styleUrls: ['./getting-started.component.scss'],
    changeDetection: ChangeDetectionStrategy.OnPush,
    imports: [MatCard, MatCardHeader, MatCardTitle, TranslateModule, MatCardContent, MatButton]
})
export class GettingStartedComponent implements OnInit, AfterViewInit {

  currentReleaseVersion: string;

  guides = gettingStartedGuides;
  docs = gettingStartedDocs;
  actions = gettingStartedActions;
  features = gettingStartedFeatures;
  readMore = helpBaseUrl + docsPath;

  constructor(private configService: ConfigService,
              private router: Router,
              private cd: ChangeDetectorRef,
              public fb: UntypedFormBuilder) {
  }

  ngOnInit() {
    this.configService.getSystemVersion().subscribe(currentRelease => {
      if (currentRelease) {
        this.currentReleaseVersion = currentRelease.version.split('-')[0];
        this.cd.detectChanges();
      }
    });
  }

  ngAfterViewInit() {
    const tbMainContentElement = document.querySelector('.tb-main-content') as HTMLElement;
    if (tbMainContentElement) {
      animatedScroll(tbMainContentElement, 0, 0);
    }
  }

  navigate(guide: GettingStartedLink) {
    this.router.navigateByUrl(guide.url);
  }

  navigateNewTab(guide: GettingStartedLink) {
    window.open(guide.url, '_blank');
  }
}
