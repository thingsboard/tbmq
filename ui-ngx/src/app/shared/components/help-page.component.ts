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
import { MatButton } from '@angular/material/button';
import { MatTooltip } from '@angular/material/tooltip';
import { MatIcon } from '@angular/material/icon';
import { TranslateModule } from '@ngx-translate/core';
import { Router } from "@angular/router";

@Component({
    selector: 'tb-help-page',
    templateUrl: './help-page.component.html',
    styleUrls: ['./help-page.component.scss'],
    imports: [MatButton, MatTooltip, MatIcon, TranslateModule]
})
export class HelpPageComponent {

  readonly page = input<string>();
  readonly label = input<string>('help.help-page');
  readonly tooltip = input<string>('help.goto-help-page');
  readonly icon = input<string>('help');

  constructor(private router: Router) {
  }

  navigate() {
    if (this.page().startsWith('http')) {
      window.open(this.page(), '_blank');
    } else {
      this.router.navigate([this.page()]);
    }
  }
}

