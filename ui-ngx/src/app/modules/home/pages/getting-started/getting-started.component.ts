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

import { ChangeDetectionStrategy, Component, OnInit } from '@angular/core';
import { UntypedFormBuilder } from '@angular/forms';
import { Router } from '@angular/router';
import { gettingStartedActions, gettingStartedGuides, GettingStartedLink } from '@shared/models/getting-started.model';
import { ConfigService } from '@core/http/config.service';

@Component({
  selector: 'tb-getting-started',
  templateUrl: './getting-started.component.html',
  styleUrls: ['./getting-started.component.scss'],
  changeDetection: ChangeDetectionStrategy.OnPush
})
export class GettingStartedComponent implements OnInit {

  currentReleaseVersion: string;

  guides = gettingStartedGuides;
  actions = gettingStartedActions;

  constructor(private configService: ConfigService,
              private router: Router,
              public fb: UntypedFormBuilder) {
  }

  ngOnInit() {
    this.configService.getSystemVersion().subscribe(currentRelease => {
      if (currentRelease) {
        this.currentReleaseVersion = currentRelease.version.split('-')[0];
      }
    });
  }

  navigate(guide: GettingStartedLink) {
    this.router.navigateByUrl(guide.url);
  }

  navigateNewTab(guide: GettingStartedLink) {
    window.open(guide.url, '_blank');
  }
}
