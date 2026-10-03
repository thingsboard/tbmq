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

import { Component, EventEmitter, Input, Output } from '@angular/core';
import { MatIconButton } from '@angular/material/button';
import { MatIcon } from '@angular/material/icon';
import { MatTooltip } from '@angular/material/tooltip';
import { MatMenu, MatMenuItem, MatMenuTrigger } from '@angular/material/menu';
import { MatCheckbox } from '@angular/material/checkbox';
import { MatDivider } from '@angular/material/divider';
import { TranslateModule } from '@ngx-translate/core';
import { TopicTreeColumnId, topicTreeColumns } from '@shared/models/topic-tree.models';

@Component({
  selector: 'tb-topic-tree-columns-menu',
  templateUrl: './topic-tree-columns-menu.component.html',
  imports: [MatIconButton, MatIcon, MatTooltip, MatMenu, MatMenuItem, MatMenuTrigger, MatCheckbox, MatDivider, TranslateModule]
})
export class TopicTreeColumnsMenuComponent {
  @Input() selected: TopicTreeColumnId[] = [];
  @Output() selectedChange = new EventEmitter<TopicTreeColumnId[]>();

  readonly allColumns = topicTreeColumns;

  isSelected(id: TopicTreeColumnId): boolean {
    return this.selected.includes(id);
  }

  toggle(id: TopicTreeColumnId): void {
    const next = this.isSelected(id) ? this.selected.filter(c => c !== id) : [...this.selected, id];
    this.selectedChange.emit(this.allColumns.map(c => c.id).filter(c => next.includes(c)));
  }

  reset(): void {
    this.selectedChange.emit(this.allColumns.filter(c => c.default).map(c => c.id));
  }
}

