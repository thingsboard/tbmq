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

import { Component, ElementRef, EventEmitter, Input, OnDestroy, Output, TemplateRef, ViewChild, ViewContainerRef, inject } from '@angular/core';
import { FormBuilder, ReactiveFormsModule, Validators } from '@angular/forms';
import { Overlay, OverlayConfig, OverlayRef } from '@angular/cdk/overlay';
import { TemplatePortal } from '@angular/cdk/portal';
import { fromEvent, Subscription } from 'rxjs';
import { MatButton } from '@angular/material/button';
import { MatIcon } from '@angular/material/icon';
import { MatTooltip } from '@angular/material/tooltip';
import { MatChipListbox, MatChipOption } from '@angular/material/chips';
import { MatError, MatFormField } from '@angular/material/form-field';
import { MatInput } from '@angular/material/input';
import { MatCheckbox } from '@angular/material/checkbox';
import { MatSelect } from '@angular/material/select';
import { MatOption } from '@angular/material/core';
import { TranslateModule, TranslateService } from '@ngx-translate/core';
import { POSITION_MAP } from '@app/shared/models/overlay.models';
import { NumericOperation, numericOperationTranslationMap } from '@shared/models/query/query.models';
import {
  isTopicTreeFilterEmpty,
  TopicTreeFilter,
  topicTreeHasTranslations,
  topicTreeHasValues
} from '@shared/models/topic-tree.models';

const CONCRETE_TOPIC = /^[^+#]+$/;

@Component({
  selector: 'tb-topic-tree-filter',
  templateUrl: './topic-tree-filter.component.html',
  styleUrls: ['./topic-tree-filter.component.scss'],
  imports: [ReactiveFormsModule, MatButton, MatIcon, MatTooltip, MatChipListbox, MatChipOption, MatFormField, MatError, MatInput,
    MatCheckbox, MatSelect, MatOption, TranslateModule]
})
export class TopicTreeFilterComponent implements OnDestroy {

  @ViewChild('panel') panel: TemplateRef<unknown>;
  @Input() set filter(filter: TopicTreeFilter) {
    this.current = filter ?? {};
    this.updateLabel();
  }
  @Output() filterChange = new EventEmitter<TopicTreeFilter>();

  readonly hasValues = topicTreeHasValues;
  readonly hasTranslations = topicTreeHasTranslations;
  readonly numericOperations = Object.values(NumericOperation);
  readonly numericOperationTranslations = numericOperationTranslationMap;

  label = '';
  tooltip: string | null = null;

  private readonly fb = inject(FormBuilder);
  readonly form = this.fb.group({
    topic: [''],
    topicPrefix: [false],
    matchesTopic: ['', Validators.pattern(CONCRETE_TOPIC)],
    has: [[] as string[]],
    wildcardOnly: [false],
    subscriptionOperation: [NumericOperation.EQUAL as string],
    subscriptions: [null as number | null, Validators.min(0)]
  });

  private current: TopicTreeFilter = {};
  private overlayRef: OverlayRef;
  private resize: Subscription;
  private readonly overlay = inject(Overlay);
  private readonly host = inject(ElementRef);
  private readonly viewContainerRef = inject(ViewContainerRef);
  private readonly translate = inject(TranslateService);

  ngOnDestroy(): void {
    this.close();
  }

  open(event: Event): void {
    event.stopPropagation();
    this.patchForm(this.current);
    const config = new OverlayConfig({
      panelClass: 'tb-filter-panel',
      backdropClass: 'cdk-overlay-transparent-backdrop',
      hasBackdrop: true,
      maxHeight: '80vh',
      height: 'min-content',
      minWidth: ''
    });
    config.positionStrategy = this.overlay.position().flexibleConnectedTo(this.host).withPositions([POSITION_MAP.bottomLeft]);
    this.overlayRef = this.overlay.create(config);
    this.overlayRef.backdropClick().subscribe(() => this.close());
    this.overlayRef.attach(new TemplatePortal(this.panel, this.viewContainerRef));
    this.resize = fromEvent(window, 'resize').subscribe(() => this.overlayRef?.updatePosition());
  }

  apply(): void {
    if (this.form.invalid) {
      return;
    }
    this.filterChange.emit(this.fromForm());
    this.close();
  }

  reset(): void {
    this.patchForm({});
    this.form.markAsDirty();
  }

  close(): void {
    this.resize?.unsubscribe();
    this.overlayRef?.dispose();
    this.overlayRef = null;
  }

  private patchForm(filter: TopicTreeFilter): void {
    this.form.reset({
      topic: filter.topic ?? '',
      topicPrefix: !!filter.topicPrefix,
      matchesTopic: filter.matchesTopic ?? '',
      has: filter.has ?? [],
      wildcardOnly: !!filter.wildcardOnly,
      subscriptionOperation: filter.subscriptionOperation ?? NumericOperation.EQUAL,
      subscriptions: filter.subscriptions ?? null
    });
  }

  private fromForm(): TopicTreeFilter {
    const v = this.form.getRawValue();
    const filter: TopicTreeFilter = {
      topic: v.topic?.trim() || undefined,
      topicPrefix: !!v.topicPrefix,
      matchesTopic: v.matchesTopic?.trim() || undefined,
      has: v.has as TopicTreeFilter['has'],
      wildcardOnly: !!v.wildcardOnly,
      subscriptions: v.subscriptions ?? undefined,
      subscriptionOperation: v.subscriptionOperation as NumericOperation
    };
    return isTopicTreeFilterEmpty(filter) ? {} : filter;
  }

  private updateLabel(): void {
    const t = (key: string) => this.translate.instant(key);
    const f = this.current;
    const parts: string[] = [];
    if (f.topic) {
      parts.push(f.topicPrefix ? `${f.topic}/#` : f.topic);
    }
    if (f.matchesTopic) {
      parts.push(`→ ${f.matchesTopic}`);
    }
    f.has?.forEach(h => parts.push(t(topicTreeHasTranslations.get(h))));
    if (f.wildcardOnly) {
      parts.push(t('topic-tree.filter-panel.wildcards'));
    }
    if ((f.subscriptions ?? null) !== null) {
      parts.push(`${t('topic-tree.column.subscriptions')} ${t(numericOperationTranslationMap.get(f.subscriptionOperation ?? NumericOperation.EQUAL))} ${f.subscriptions}`);
    }
    const title = t('topic-tree.filter-panel.title');
    this.label = parts.length ? `${title}: ${parts.join('; ')}` : title;
    this.tooltip = parts.length ? parts.join('; ') : null;
  }
}
