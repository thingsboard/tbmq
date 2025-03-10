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

import { Component, input } from '@angular/core';
import { HelpLinks } from '@shared/models/constants';
import { MatIconButton } from '@angular/material/button';
import { MatTooltip } from '@angular/material/tooltip';
import { MatIcon } from '@angular/material/icon';
import { TranslateModule } from '@ngx-translate/core';

@Component({
    selector: '[tb-help]',
    templateUrl: './help.component.html',
    imports: [MatIconButton, MatTooltip, MatIcon, TranslateModule]
})
export class HelpComponent {

  readonly helpLinkId = input<string>(undefined, { alias: "tb-help" });

  gotoHelpPage(): void {
    let helpUrl = HelpLinks.linksMap[this.helpLinkId()];
    const helpLinkId = this.helpLinkId();
    if (!helpUrl && helpLinkId &&
      (helpLinkId.startsWith('http://') || helpLinkId.startsWith('https://'))) {
      helpUrl = helpLinkId;
    }
    if (helpUrl) {
      window.open(helpUrl, '_blank');
    }
  }

}
