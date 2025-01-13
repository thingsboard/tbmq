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

import { ChangeDetectorRef, Component, EventEmitter, Output, input, booleanAttribute, model } from '@angular/core';
import { ClipboardService } from 'ngx-clipboard';
import { TooltipPosition, MatTooltip } from '@angular/material/tooltip';
import { TranslateService } from '@ngx-translate/core';
import { ThemePalette } from '@angular/material/core';
import { MatIconButton } from '@angular/material/button';
import { NgClass, NgStyle } from '@angular/common';
import { ExtendedModule } from '@angular/flex-layout/extended';
import { MatIcon } from '@angular/material/icon';

@Component({
    selector: 'tb-copy-button',
    styleUrls: ['copy-button.component.scss'],
    templateUrl: './copy-button.component.html',
    imports: [MatIconButton, NgClass, ExtendedModule, MatTooltip, MatIcon, NgStyle],
    standalone: true
})
export class CopyButtonComponent {

  readonly copyText = model<string>();
  readonly disabled = input(false, {transform: booleanAttribute});
  readonly mdiIcon = input<string>();
  readonly icon = input<string>('content_copy');
  readonly tooltipText = input<string>(this.translate.instant('action.copy'));
  readonly tooltipPosition = input<TooltipPosition>('above');
  readonly style = input<{[key: string]: any;}>({});
  readonly color = input<ThemePalette>();
  readonly buttonClass = input<{[key: string]: any;}>({});

  copied = false;
  private timer;

  @Output()
  successCopied = new EventEmitter<string>();

  constructor(private clipboardService: ClipboardService,
              private translate: TranslateService,
              private cd: ChangeDetectorRef) {
  }

  copy($event: Event | string): void {
    if (typeof $event === 'object') {
      $event.stopPropagation();
    } else if ($event?.length) {
      this.copyText.set($event);
    }
    if (this.timer) {
      clearTimeout(this.timer);
    }
    const copyText = this.copyText();
    this.clipboardService.copy(copyText);
    this.successCopied.emit(copyText);
    this.copied = true;
    this.timer = setTimeout(() => {
      this.copied = false;
      this.cd.detectChanges();
    }, 1500);
  }

  get matTooltipText(): string {
    return this.copied ? this.translate.instant('action.on-copied') : this.tooltipText();
  }

  get matTooltipPosition(): TooltipPosition {
    return this.copied ? 'below' : this.tooltipPosition();
  }

}
