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

import {
  Component,
  ElementRef,
  forwardRef,
  Inject,
  InjectionToken,
  Input, OnChanges,
  OnDestroy,
  OnInit,
  Optional, SimpleChanges,
  TemplateRef,
  ViewChild,
  ViewContainerRef
} from '@angular/core';
import { ControlValueAccessor, NG_VALUE_ACCESSOR, UntypedFormBuilder, UntypedFormGroup, FormsModule, ReactiveFormsModule } from '@angular/forms';
import { coerceBoolean } from '@shared/decorators/coercion';
import { Overlay, OverlayConfig, OverlayRef } from '@angular/cdk/overlay';
import { TemplatePortal } from '@angular/cdk/portal';
import { deepClone, isNumber } from '@core/utils';
import { TranslateService, TranslateModule } from '@ngx-translate/core';
import { EntityType } from '@shared/models/entity-type.models';
import { fromEvent, Subject, Subscription } from 'rxjs';
import { QoS, QosTranslation } from '@shared/models/session.model';
import { POSITION_MAP } from '@app/shared/models/overlay.models';
import { MessageFilterConfig, MessageFilterDefaultConfig, WebSocketConnection } from '@shared/models/ws-client.model';
import { NgIf, NgTemplateOutlet, NgFor } from '@angular/common';
import { MatButton } from '@angular/material/button';
import { MatIcon } from '@angular/material/icon';
import { FlexModule } from '@angular/flex-layout/flex';
import { MatFormField } from '@angular/material/form-field';
import { MatInput } from '@angular/material/input';
import { MatChipListbox, MatChipOption } from '@angular/material/chips';
import { MatTooltip } from '@angular/material/tooltip';
import { takeUntil } from 'rxjs/operators';

export const MESSAGE_FILTER_CONFIG_DATA = new InjectionToken<any>('MessageFilterConfigData');

export interface MessageFilterConfigData {
  panelMode: boolean;
  filterConfig: MessageFilterConfig;
  initialFilterConfig?: MessageFilterConfig;
}

// @dynamic
@Component({
    selector: 'tb-message-filter-config',
    templateUrl: './message-filter-config.component.html',
    styleUrls: ['./message-filter-config.component.scss'],
    providers: [
        {
            provide: NG_VALUE_ACCESSOR,
            useExisting: forwardRef(() => MessageFilterConfigComponent),
            multi: true
        }
    ],
    standalone: true,
    imports: [NgIf, NgTemplateOutlet, MatButton, MatIcon, FormsModule, FlexModule, ReactiveFormsModule, TranslateModule, MatFormField, MatInput, MatChipListbox, NgFor, MatChipOption, MatTooltip]
})
export class MessageFilterConfigComponent implements OnInit, OnDestroy, ControlValueAccessor, OnChanges {

  @ViewChild('messageFilterPanel')
  filterPanel: TemplateRef<any>;

  @Input() disabled: boolean;

  @coerceBoolean()
  @Input()
  buttonMode = true;

  @coerceBoolean()
  @Input()
  propagatedFilter = true;

  @Input()
  connectionChanged: WebSocketConnection;

  initialFilterConfig: MessageFilterConfig = MessageFilterDefaultConfig;

  qosTypes = Object.values(QoS).filter(v => isNumber(v));
  qosTranslation = QosTranslation;
  retainedOptions = [true, false];
  panelMode = false;
  buttonDisplayValue = this.translate.instant('mqtt-client-credentials.filter-title');
  filterConfigForm: UntypedFormGroup;
  filterOverlayRef: OverlayRef;
  panelResult: MessageFilterConfig = null;
  entityType = EntityType;

  private filterConfig: MessageFilterConfig;
  private resizeWindows: Subscription;
  private destroy$ = new Subject<void>();
  private propagateChange = (_: any) => {};

  constructor(@Optional() @Inject(MESSAGE_FILTER_CONFIG_DATA)
              private data: MessageFilterConfigData | undefined,
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
      this.filterConfig = this.data.filterConfig;
      this.initialFilterConfig = this.data.initialFilterConfig;
      if (this.panelMode && !this.initialFilterConfig) {
        this.initialFilterConfig = deepClone(this.filterConfig);
      }
    }
    this.filterConfigForm = this.fb.group({
      topic: [null, []],
      qosList: [null, []],
      retainList: [null, []]
    });
    this.filterConfigForm.valueChanges
      .pipe(takeUntil(this.destroy$))
      .subscribe(() => {
        if (!this.buttonMode) {
          this.configUpdated(this.filterConfigForm.value);
        }
      });
    if (this.panelMode) {
      this.updateConfigForm(this.filterConfig);
    }
  }

  ngOnDestroy(): void {
    this.destroy$.next();
    this.destroy$.complete();
  }

  ngOnChanges(changes: SimpleChanges): void {
    for (const propName of Object.keys(changes)) {
      const change = changes[propName];
      if (!change.firstChange && change.currentValue !== change.previousValue) {
        if (propName === 'connectionChanged' && change.currentValue) {
          this.onChangeConnection();
        }
      }
    }
  }

  registerOnChange(fn: any): void {
    this.propagateChange = fn;
  }

  registerOnTouched(fn: any): void {
  }

  setDisabledState(isDisabled: boolean): void {
    this.disabled = isDisabled;
    if (this.disabled) {
      this.filterConfigForm.disable({emitEvent: false});
    } else {
      this.filterConfigForm.enable({emitEvent: false});
    }
  }

  writeValue(filterConfig?: MessageFilterConfig): void {
    this.filterConfig = filterConfig;
    if (!this.initialFilterConfig && filterConfig) {
      this.initialFilterConfig = deepClone(filterConfig);
    }
    this.updateButtonDisplayValue();
    this.updateConfigForm(filterConfig);
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

    this.filterOverlayRef = this.overlay.create(config);
    this.filterOverlayRef.backdropClick().subscribe(() => {
      this.filterOverlayRef.dispose();
    });
    this.filterOverlayRef.attach(new TemplatePortal(this.filterPanel,
      this.viewContainerRef));
    this.resizeWindows = fromEvent(window, 'resize').subscribe(() => {
      this.filterOverlayRef.updatePosition();
    });
  }

  reset() {
    this.updateConfigForm(this.initialFilterConfig);
    this.updateButtonDisplayValue();
    this.filterConfigForm.markAsDirty();
    this.filterConfigForm.updateValueAndValidity();
  }

  cancel() {
    this.updateConfigForm(this.filterConfig);
    if (this.overlayRef) {
      this.overlayRef.dispose();
    } else {
      this.resizeWindows.unsubscribe();
      this.filterOverlayRef.dispose();
    }
    this.filterConfigForm.markAsPristine();
  }

  update() {
    this.configUpdated(this.filterConfigForm.value);
    this.filterConfigForm.markAsPristine();
    if (this.panelMode) {
      this.panelResult = this.filterConfig;
    }
    if (this.overlayRef) {
      this.overlayRef.dispose();
    } else {
      this.resizeWindows.unsubscribe();
      this.filterOverlayRef.dispose();
    }
  }

  private updateConfigForm(filterConfig?: MessageFilterConfig) {
    this.filterConfigForm.patchValue({
      topic: filterConfig?.topic,
      qosList: filterConfig?.qosList,
      retainList: filterConfig?.retainList
    }, {emitEvent: false});
  }

  private configUpdated(formValue: any) {
    this.filterConfig = this.filterConfigFormValue(formValue);
    this.updateButtonDisplayValue();
    this.propagateChange(this.filterConfig);
  }

  private filterConfigFormValue(formValue: any): MessageFilterConfig {
    return {
      topic: formValue.topic,
      qosList: formValue.qosList,
      retainList: formValue.retainList
    };
  }

  private updateButtonDisplayValue() {
      if (this.buttonMode) {
        const filterTextParts: string[] = [];
        if (this.filterConfig?.qosList?.length) {
          filterTextParts.push(`${this.filterConfig.qosList.join(', ')}`);
        }
        if (this.filterConfig?.retainList?.length) {
          filterTextParts.push(`${this.filterConfig.retainList.join(', ')}`);
        }
        if (this.filterConfig?.topic?.length) {
          filterTextParts.push(`${this.filterConfig?.topic}`);
        }
        if (!filterTextParts.length) {
          this.buttonDisplayValue = this.translate.instant('mqtt-client-credentials.filter-title');
        } else {
          this.buttonDisplayValue = this.translate.instant('mqtt-client-credentials.filter-title') + `: ${filterTextParts.join('; ')}`;
        }
      }
    }

  private onChangeConnection() {
    this.updateConfigForm(this.initialFilterConfig);
    this.filterConfigForm.markAsPristine();
    this.filterConfig = MessageFilterDefaultConfig;
    this.updateButtonDisplayValue();
  }
}
