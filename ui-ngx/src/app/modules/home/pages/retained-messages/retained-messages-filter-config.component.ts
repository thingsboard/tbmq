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

import {
  Component,
  ElementRef,
  forwardRef,
  Inject,
  InjectionToken,
  Input,
  OnDestroy,
  OnInit,
  Optional,
  TemplateRef,
  ViewChild,
  ViewContainerRef
} from '@angular/core';
import { ControlValueAccessor, NG_VALUE_ACCESSOR, UntypedFormBuilder, UntypedFormGroup, FormsModule, ReactiveFormsModule } from '@angular/forms';
import { coerceBoolean } from '@shared/decorators/coercion';
import { Overlay, OverlayConfig, OverlayRef } from '@angular/cdk/overlay';
import { TemplatePortal } from '@angular/cdk/portal';

import { TranslateService, TranslateModule } from '@ngx-translate/core';
import { deepClone, isNumber } from '@core/utils';
import { EntityType } from '@shared/models/entity-type.models';
import { fromEvent, Subject, Subscription } from 'rxjs';
import { POSITION_MAP } from '@app/shared/models/overlay.models';
import {
  RetainedMessagesFilterConfig,
  retainedMessagesFilterConfigEquals
} from '@shared/models/retained-message.model';
import { NgIf, NgTemplateOutlet, NgFor } from '@angular/common';
import { MatButton } from '@angular/material/button';
import { MatTooltip } from '@angular/material/tooltip';
import { MatIcon } from '@angular/material/icon';
import { FlexModule } from '@angular/flex-layout/flex';
import { MatFormField } from '@angular/material/form-field';
import { MatInput } from '@angular/material/input';
import { MatChipListbox, MatChipOption } from '@angular/material/chips';
import { QoS, QosTranslation } from '@shared/models/session.model';
import { takeUntil } from 'rxjs/operators';

export const RETAINED_MESSAGE_FILTER_CONFIG_DATA = new InjectionToken<any>('RetainedMessagesFilterConfigData');

export interface RetainedMessagesFilterConfigData {
  panelMode: boolean;
  retainedMessagesFilterConfig: RetainedMessagesFilterConfig;
  initialRetainedMessagesFilterConfig?: RetainedMessagesFilterConfig;
}

// @dynamic
@Component({
    selector: 'tb-retained-messages-filter-config',
    templateUrl: './retained-messages-filter-config.component.html',
    styleUrls: ['./retained-messages-filter-config.component.scss'],
    providers: [
        {
            provide: NG_VALUE_ACCESSOR,
            useExisting: forwardRef(() => RetainedMessagesFilterConfigComponent),
            multi: true
        }
    ],
    standalone: true,
    imports: [NgIf, NgTemplateOutlet, MatButton, MatTooltip, MatIcon, FormsModule, FlexModule, ReactiveFormsModule, TranslateModule, MatFormField, MatInput, MatChipListbox, NgFor, MatChipOption]
})
export class RetainedMessagesFilterConfigComponent implements OnInit, OnDestroy, ControlValueAccessor {

  @ViewChild('retainedMessagesPanel')
  retainedMessagesFilterPanel: TemplateRef<any>;

  @Input() disabled: boolean;

  @coerceBoolean()
  @Input()
  buttonMode = true;

  @coerceBoolean()
  @Input()
  propagatedFilter = true;

  @Input()
  initialRetainedMessagesFilterConfig: RetainedMessagesFilterConfig;

  qosTypes = Object.values(QoS).filter(v => isNumber(v));
  qosTranslation = QosTranslation;
  panelMode = false;
  buttonDisplayValue = this.translate.instant('retained-message.filter-title');
  buttonDisplayTooltip: string;
  retainedMessagesFilterConfigForm: UntypedFormGroup;
  retainedMessagesFilterOverlayRef: OverlayRef;
  panelResult: RetainedMessagesFilterConfig = null;
  entityType = EntityType;

  private retainedMessagesFilterConfig: RetainedMessagesFilterConfig;
  private resizeWindows: Subscription;
  private destroy$ = new Subject<void>();
  private propagateChange = (_: any) => {};

  constructor(@Optional() @Inject(RETAINED_MESSAGE_FILTER_CONFIG_DATA)
              private data: RetainedMessagesFilterConfigData | undefined,
              @Optional() private overlayRef: OverlayRef,
              private fb: UntypedFormBuilder,
              private translate: TranslateService,
              private overlay: Overlay,
              private nativeElement: ElementRef,
              private viewContainerRef: ViewContainerRef) {
  }

  ngOnInit(): void {
    if (this.data) {
      this.panelMode = this.data.panelMode;
      this.retainedMessagesFilterConfig = this.data.retainedMessagesFilterConfig;
      this.initialRetainedMessagesFilterConfig = this.data.initialRetainedMessagesFilterConfig;
      if (this.panelMode && !this.initialRetainedMessagesFilterConfig) {
        this.initialRetainedMessagesFilterConfig = deepClone(this.retainedMessagesFilterConfig);
      }
    }
    this.retainedMessagesFilterConfigForm = this.fb.group({
      topicName: [null, []],
      payload: [null, []],
      qosList: [null, []],
    });
    this.retainedMessagesFilterConfigForm.valueChanges
      .pipe(takeUntil(this.destroy$))
      .subscribe(() => {
        if (!this.buttonMode) {
          this.retainedMessagesConfigUpdated(this.retainedMessagesFilterConfigForm.value);
        }
      });
    if (this.panelMode) {
      this.updateRetainedMessagesConfigForm(this.retainedMessagesFilterConfig);
    }
    this.initialRetainedMessagesFilterConfig = this.retainedMessagesFilterConfigForm.getRawValue();
  }

  ngOnDestroy() {
    this.destroy$.next();
    this.destroy$.complete();
  }

  registerOnChange(fn: any): void {
    this.propagateChange = fn;
  }

  registerOnTouched(fn: any): void {
  }

  setDisabledState(isDisabled: boolean): void {
    this.disabled = isDisabled;
    if (this.disabled) {
      this.retainedMessagesFilterConfigForm.disable({emitEvent: false});
    } else {
      this.retainedMessagesFilterConfigForm.enable({emitEvent: false});
    }
  }

  writeValue(retainedMessagesFilterConfig?: RetainedMessagesFilterConfig): void {
    this.retainedMessagesFilterConfig = retainedMessagesFilterConfig;
    if (!this.initialRetainedMessagesFilterConfig && retainedMessagesFilterConfig) {
      this.initialRetainedMessagesFilterConfig = deepClone(retainedMessagesFilterConfig);
    }
    this.updateButtonDisplayValue();
    this.updateRetainedMessagesConfigForm(retainedMessagesFilterConfig);
  }

  toggleFilterPanel($event: Event) {
    if ($event) {
      $event.stopPropagation();
    }
    const config = new OverlayConfig({
      panelClass: 'tb-filter-panel',
      backdropClass: 'cdk-overlay-transparent-backdrop',
      hasBackdrop: true,
      maxHeight: '80vh',
      height: 'min-content',
      minWidth: ''
    });
    config.hasBackdrop = true;
    config.positionStrategy = this.overlay.position()
      .flexibleConnectedTo(this.nativeElement)
      .withPositions([POSITION_MAP.bottomLeft]);

    this.retainedMessagesFilterOverlayRef = this.overlay.create(config);
    this.retainedMessagesFilterOverlayRef.backdropClick().subscribe(() => {
      this.retainedMessagesFilterOverlayRef.dispose();
    });
    this.retainedMessagesFilterOverlayRef.attach(new TemplatePortal(this.retainedMessagesFilterPanel,
      this.viewContainerRef));
    this.resizeWindows = fromEvent(window, 'resize').subscribe(() => {
      this.retainedMessagesFilterOverlayRef.updatePosition();
    });
  }

  cancel() {
    this.updateRetainedMessagesConfigForm(this.retainedMessagesFilterConfig);
    if (this.overlayRef) {
      this.overlayRef.dispose();
    } else {
      this.resizeWindows.unsubscribe();
      this.retainedMessagesFilterOverlayRef.dispose();
    }
  }

  update() {
    this.retainedMessagesConfigUpdated(this.retainedMessagesFilterConfigForm.value);
    this.retainedMessagesFilterConfigForm.markAsPristine();
    if (this.panelMode) {
      this.panelResult = this.retainedMessagesFilterConfig;
    }
    if (this.overlayRef) {
      this.overlayRef.dispose();
    } else {
      this.resizeWindows.unsubscribe();
      this.retainedMessagesFilterOverlayRef.dispose();
    }
  }

  reset() {
    if (this.initialRetainedMessagesFilterConfig) {
      if (this.buttonMode || this.panelMode) {
        const retainedMessagesFilterConfig = this.retainedMessagesFilterConfigFromFormValue(this.retainedMessagesFilterConfigForm.value);
        if (!retainedMessagesFilterConfigEquals(retainedMessagesFilterConfig, this.initialRetainedMessagesFilterConfig)) {
          this.updateRetainedMessagesConfigForm(this.initialRetainedMessagesFilterConfig);
          this.retainedMessagesFilterConfigForm.markAsDirty();
        }
      } else {
        if (!retainedMessagesFilterConfigEquals(this.retainedMessagesFilterConfig, this.initialRetainedMessagesFilterConfig)) {
          this.retainedMessagesFilterConfig = this.initialRetainedMessagesFilterConfig;
          this.updateButtonDisplayValue();
          this.updateRetainedMessagesConfigForm(this.retainedMessagesFilterConfig);
          this.propagateChange(this.retainedMessagesFilterConfig);
        }
      }
    }
  }

  private updateRetainedMessagesConfigForm(retainedMessagesFilterConfig?: RetainedMessagesFilterConfig) {
    this.retainedMessagesFilterConfigForm.patchValue({
      topicName: retainedMessagesFilterConfig?.topicName,
      payload: retainedMessagesFilterConfig?.payload,
      qosList: retainedMessagesFilterConfig?.qosList,
    }, {emitEvent: false});
  }

  private retainedMessagesConfigUpdated(formValue: any) {
    this.retainedMessagesFilterConfig = this.retainedMessagesFilterConfigFromFormValue(formValue);
    this.updateButtonDisplayValue();
    this.propagateChange(this.retainedMessagesFilterConfig);
  }

  private retainedMessagesFilterConfigFromFormValue(formValue: any): RetainedMessagesFilterConfig {
    return {
      topicName: formValue.topicName,
      payload: formValue.payload,
      qosList: formValue.qosList,
    };
  }

  private updateButtonDisplayValue() {
    if (this.buttonMode) {
      const filterTextParts: string[] = [];
      const filterTooltipParts: string[] = [];
      if (this.retainedMessagesFilterConfig?.topicName?.length) {
        const topicName = this.retainedMessagesFilterConfig.topicName;
        filterTextParts.push(topicName);
        filterTooltipParts.push(`${this.translate.instant('retained-message.topic')}: ${topicName}`);
      }
      if (this.retainedMessagesFilterConfig?.payload?.length) {
        const payload = this.retainedMessagesFilterConfig.payload;
        filterTextParts.push(payload);
        filterTooltipParts.push(`${this.translate.instant('retained-message.payload')}: ${payload}`);
      }
      if (this.retainedMessagesFilterConfig?.qosList?.length) {
        const qosList = `${this.translate.instant('retained-message.qos')}:${this.retainedMessagesFilterConfig.qosList.join(', ')}`;
        filterTextParts.push(qosList);
        filterTooltipParts.push(qosList);
      }
      if (!filterTextParts.length) {
        this.buttonDisplayValue = this.translate.instant('retained-message.filter-title');
        this.buttonDisplayTooltip = null;
      } else {
        this.buttonDisplayValue = this.translate.instant('retained-message.filter-title') + `: ${filterTextParts.join('; ')}`;
        this.buttonDisplayTooltip = filterTooltipParts.join('; ');
      }
    }
  }

}
