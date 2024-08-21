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

import { Component, EventEmitter, Input, OnDestroy, OnInit, Output } from '@angular/core';
import { UntypedFormBuilder, UntypedFormGroup } from '@angular/forms';
import { RhOptions, SubscriptionOptions } from '@shared/models/ws-client.model';

@Component({
  selector: 'tb-show-subscription-options',
  templateUrl: './show-subscription-options-popover.component.html',
  styleUrls: []
})
export class ShowSubscriptionOptionsPopoverComponent implements OnInit, OnDestroy {

  @Input()
  onClose: () => void;

  @Output()
  subscriptionOptionsApplied = new EventEmitter<SubscriptionOptions>();

  @Input()
  data: SubscriptionOptions;

  subscriptionOptionsForm: UntypedFormGroup;
  rhOptions = RhOptions;

  constructor(private fb: UntypedFormBuilder) {}

  ngOnInit() {
    this.subscriptionOptionsForm = this.fb.group({
      retainAsPublish: [this.data ? this.data.retainAsPublish : null, []],
      retainHandling: [this.data ? this.data.retainHandling : null, []],
      noLocal: [this.data ? this.data.noLocal : null, []],
      subscriptionIdentifier: [this.data ? this.data.subscriptionIdentifier : null, []],
    });
  }

  ngOnDestroy() {
    this.onClose();
  }

  cancel() {
    this.onClose();
  }

  apply() {
    this.subscriptionOptionsApplied.emit(this.subscriptionOptionsForm.getRawValue());
  }
}
