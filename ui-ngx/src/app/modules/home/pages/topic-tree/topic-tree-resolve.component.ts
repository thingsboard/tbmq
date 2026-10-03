///
/// ThingsBoard, Inc. ("COMPANY") CONFIDENTIAL
///
/// Copyright © 2016-2026 ThingsBoard, Inc. All Rights Reserved.
///
/// NOTICE: All information contained herein is, and remains
/// the property of ThingsBoard, Inc. and its suppliers,
/// if any.  The intellectual and technical concepts contained
/// herein are proprietary to ThingsBoard, Inc.
/// and its suppliers and may be covered by U.S. and Foreign Patents,
/// patents in process, and are protected by trade secret or copyright law.
///
/// Dissemination of this information or reproduction of this material is strictly forbidden
/// unless prior written permission is obtained from COMPANY.
///
/// Access to the source code contained herein is hereby forbidden to anyone except current COMPANY employees,
/// managers or contractors who have executed Confidentiality and Non-disclosure agreements
/// explicitly covering such access.
///
/// The copyright notice above does not evidence any actual or intended publication
/// or disclosure  of  this source code, which includes
/// information that is confidential and/or proprietary, and is a trade secret, of  COMPANY.
/// ANY REPRODUCTION, MODIFICATION, DISTRIBUTION, PUBLIC  PERFORMANCE,
/// OR PUBLIC DISPLAY OF OR THROUGH USE  OF THIS  SOURCE CODE  WITHOUT
/// THE EXPRESS WRITTEN CONSENT OF COMPANY IS STRICTLY PROHIBITED,
/// AND IN VIOLATION OF APPLICABLE LAWS AND INTERNATIONAL TREATIES.
/// THE RECEIPT OR POSSESSION OF THIS SOURCE CODE AND/OR RELATED INFORMATION
/// DOES NOT CONVEY OR IMPLY ANY RIGHTS TO REPRODUCE, DISCLOSE OR DISTRIBUTE ITS CONTENTS,
/// OR TO MANUFACTURE, USE, OR SELL ANYTHING THAT IT  MAY DESCRIBE, IN WHOLE OR IN PART.
///

import { ChangeDetectionStrategy, ChangeDetectorRef, Component, EventEmitter, Output, inject } from '@angular/core';
import { FormControl, FormsModule, ReactiveFormsModule, Validators } from '@angular/forms';
import { DecimalPipe, KeyValuePipe } from '@angular/common';
import { MatButton, MatIconButton } from '@angular/material/button';
import { MatFormField, MatError, MatLabel } from '@angular/material/form-field';
import { MatIcon } from '@angular/material/icon';
import { MatInput } from '@angular/material/input';
import { TranslateModule } from '@ngx-translate/core';
import { TopicTreeService } from '@core/http/topic-tree.service';
import { TopicResolve } from '@shared/models/topic-tree.models';

const CONCRETE_TOPIC = /^[^+#]+$/;

@Component({
  selector: 'tb-topic-tree-resolve',
  templateUrl: './topic-tree-resolve.component.html',
  styleUrls: ['./topic-tree-resolve.component.scss'],
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [FormsModule, ReactiveFormsModule, DecimalPipe, KeyValuePipe, MatButton, MatIconButton, MatFormField, MatLabel, MatError, MatIcon, MatInput, TranslateModule]
})
export class TopicTreeResolveComponent {

  @Output() showInTree = new EventEmitter<string>();
  @Output() openInSubscriptions = new EventEmitter<string>();

  readonly topic = new FormControl<string>('', [Validators.required, Validators.pattern(CONCRETE_TOPIC)]);
  result: TopicResolve | null = null;
  loading = false;

  private readonly service = inject(TopicTreeService);
  private readonly cd = inject(ChangeDetectorRef);

  resolve(): void {
    if (this.topic.invalid) {
      this.topic.markAsTouched();
      return;
    }
    this.loading = true;
    this.service.resolve(this.topic.value).subscribe({
      next: result => {
        this.result = result;
        this.loading = false;
        this.cd.markForCheck();
      },
      error: () => {
        this.loading = false;
        this.cd.markForCheck();
      }
    });
  }

  clear(): void {
    this.result = null;
    this.topic.reset('');
  }
}
