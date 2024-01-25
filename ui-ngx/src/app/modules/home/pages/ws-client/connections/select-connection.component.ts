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

import { ChangeDetectionStrategy, Component, Renderer2, ViewContainerRef } from '@angular/core';
import { MatButton } from '@angular/material/button';
import { TbPopoverService } from '@shared/components/popover.service';
import { ShowSelectConnectionPopoverComponent } from '@home/pages/ws-client/connections/show-select-connection-popover.component';

@Component({
  selector: 'tb-select-connection',
  templateUrl: './select-connection.component.html',
  changeDetection: ChangeDetectionStrategy.OnPush
})
export class SelectConnectionComponent {

  constructor(private popoverService: TbPopoverService,
              private renderer: Renderer2,
              private viewContainerRef: ViewContainerRef) {
  }

  showConnections($event: Event, createVersionButton: MatButton) {
    if ($event) {
      $event.stopPropagation();
    }
    const trigger = createVersionButton._elementRef.nativeElement;
    if (this.popoverService.hasPopover(trigger)) {
      this.popoverService.hidePopover(trigger);
    } else {
      const showNotificationPopover = this.popoverService.displayPopover(trigger, this.renderer,
        this.viewContainerRef, ShowSelectConnectionPopoverComponent, 'bottom', true, null,
        {
          onClose: () => {
            showNotificationPopover.hide();
          }
        },
        {maxHeight: '90vh', height: '324px', padding: '10px'},
        {width: '500px', minWidth: '100%', maxWidth: '100%'},
        {height: '100%', flexDirection: 'column', boxSizing: 'border-box', display: 'flex', margin: '0 -16px'}, false);
      showNotificationPopover.tbComponentRef.instance.popoverComponent = showNotificationPopover;
    }
  }
}
