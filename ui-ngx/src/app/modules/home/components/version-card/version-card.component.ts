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

import { Component, OnInit } from '@angular/core';
import { HomePageTitleType } from '@shared/models/home-page.model';
import { ConfigService } from '@core/http/config.service';
import { forkJoin } from 'rxjs';

@Component({
  selector: 'tb-version-card',
  templateUrl: './version-card.component.html',
  styleUrls: ['./version-card.component.scss']
})
export class VersionCardComponent implements OnInit {

  cardType = HomePageTitleType.VERSION;
  updatesAvailable: boolean;
  currentReleaseVersion: string;
  latestReleaseVersion: string;

  constructor(private configService: ConfigService ) {
  }

  ngOnInit(): void {
    this.configService.getSystemVersion().subscribe(
      res => {
        if (res) {
          this.currentReleaseVersion = res.version.split('-')[0];
          this.latestReleaseVersion = res.newestVersion;
          this.updatesAvailable = this.latestReleaseVersion !== this.currentReleaseVersion;
        }
      });
    }

  gotoDocs(page: string){
    window.open(`https://thingsboard.io/${page}`, '_blank');
  }

  gotoGithub(version: string){
    window.open(`https://github.com/thingsboard/tbmq/releases/tag/v${version}`, '_blank');
  }
}
