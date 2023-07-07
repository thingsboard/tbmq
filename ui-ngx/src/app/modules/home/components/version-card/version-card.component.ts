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

import { Component, OnInit } from '@angular/core';
import { HomePageTitleType } from '@shared/models/home-page.model';
import { SystemVersionInfo } from '@shared/models/config.model';
import { ConfigService } from '@core/http/config.service';
import { forkJoin } from 'rxjs';

@Component({
  selector: 'tb-version-card',
  templateUrl: './version-card.component.html',
  styleUrls: ['./version-card.component.scss']
})
export class VersionCardComponent implements OnInit {

  cardType = HomePageTitleType.VERSION;
  versionData: SystemVersionInfo;
  updatesAvailable = false;

  constructor(private configService: ConfigService ) {
  }

  ngOnInit(): void {
    forkJoin([this.configService.getSystemVersion(), this.configService.getGithubSystemVersion()])
      .subscribe(
        res => {
          if (res) {
            const currentRelease = res[0];
            const latestRelease = res[1];
            const currentReleaseVersion = currentRelease.version;
            const latestReleaseVersion = latestRelease.tag_name.slice(1);
            this.versionData = currentRelease;
            this.versionData.latestLink = latestRelease.html_url;
            this.versionData.latestVersion = latestRelease.name;
            this.updatesAvailable = latestReleaseVersion !== currentReleaseVersion;
          }
        });
    }
}
