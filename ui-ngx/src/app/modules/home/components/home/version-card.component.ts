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

import { Component, OnInit } from '@angular/core';
import { HomePageTitleType } from '@shared/models/home-page.model';
import { HelpLinks } from '@shared/models/constants';
import { ConfigService } from '@core/http/config.service';
import { CardTitleButtonComponent } from '@shared/components/button/card-title-button.component';
import { TranslateModule } from '@ngx-translate/core';
import { NgTemplateOutlet } from '@angular/common';
import { MatButton } from '@angular/material/button';
import { MatTooltip } from '@angular/material/tooltip';
import { editionProductName, isNewerVersion, stripSnapshotSuffix } from '@core/utils/edition.utils';
import { environment as env } from '@env/environment';

@Component({
    selector: 'tb-home-version',
    templateUrl: './version-card.component.html',
    styleUrls: ['./version-card.component.scss'],
    imports: [CardTitleButtonComponent, TranslateModule, NgTemplateOutlet, MatButton, MatTooltip]
})
export class VersionCardComponent implements OnInit {

  cardType = HomePageTitleType.VERSION;
  contactUsLink = HelpLinks.linksMap.contactUs;
  upgradeInstructionsLink = HelpLinks.linksMap.upgradeInstructions;
  updatesAvailable: boolean;
  currentReleaseVersion: string;
  productNamePrefix = this.configService.brokerConfig?.edition === 'lite'
    ? `${editionProductName(env.appTitle, this.configService.brokerConfig.edition)} `
    : '';
  latestReleaseVersion: string;

  constructor(private configService: ConfigService ) {
  }

  ngOnInit(): void {
    this.configService.getSystemVersion().subscribe(
      res => {
        if (res) {
          this.currentReleaseVersion = stripSnapshotSuffix(res.version);
          this.latestReleaseVersion = res.newestVersion;
          this.updatesAvailable = isNewerVersion(res.newestVersion, res.version);
        }
      });
    }

  gotoDocs(link: string){
    window.open(link, '_blank');
  }

  gotoGithub(version: string){
    window.open(`https://github.com/thingsboard/tbmq/releases/tag/v${version}`, '_blank');
  }
}
