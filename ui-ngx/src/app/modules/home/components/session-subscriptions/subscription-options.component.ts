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

import { Component, EventEmitter, Input, Output, Renderer2, ViewContainerRef } from '@angular/core';
import { WebSocketSubscriptionConfiguration } from '@shared/models/ws-client.model';
import { MatButton } from '@angular/material/button';
import { TbPopoverService } from '@shared/components/popover.service';
import {
  ShowSubscriptionOptionsPopoverComponent
} from '@home/components/session-subscriptions/show-subscription-options-popover.component';
import { AbstractControl } from '@angular/forms';

@Component({
  selector: 'tb-subscription-options',
  templateUrl: './subscription-options.component.html',
  styleUrls: []
})
export class SubscriptionOptionsComponent {

  @Input()
  subscriptionOptions: AbstractControl;

  @Output()
  subscriptionOptionsValue = new EventEmitter<WebSocketSubscriptionConfiguration>();

  constructor(private renderer: Renderer2,
              private popoverService: TbPopoverService,
              private viewContainerRef: ViewContainerRef) {
  }

  toggleSubscriptionOptionsPopover($event: Event, button: MatButton) {
    if ($event) {
      $event.stopPropagation();
    }
    const trigger = button._elementRef.nativeElement;
    if (this.popoverService.hasPopover(trigger)) {
      this.popoverService.hidePopover(trigger);
    } else {
      const showNotificationPopover = this.popoverService.displayPopover(trigger, this.renderer,
        this.viewContainerRef, ShowSubscriptionOptionsPopoverComponent, 'left', true, null,
        {
          onClose: () => {
            showNotificationPopover.hide();
          },
          data: this.subscriptionOptions.getRawValue(),
        },
        {maxHeight: '90vh', height: '100%', padding: '10px'},
        {width: '560px', minWidth: '100%', maxWidth: '100%'},
        {height: '100%', flexDirection: 'column', boxSizing: 'border-box', display: 'flex'}, false);

      showNotificationPopover.tbComponentRef.instance.subscriptionOptionsApplied.subscribe((value) => {
        this.subscriptionOptionsValue.emit(value);
        showNotificationPopover.hide();
      });
    }
  }
}
