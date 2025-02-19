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
  OnDestroy,
  OnInit,
  Optional,
  TemplateRef,
  ViewContainerRef,
  input, booleanAttribute, model, Input,
  viewChild
} from '@angular/core';
import { ControlValueAccessor, NG_VALUE_ACCESSOR, UntypedFormBuilder, UntypedFormGroup, FormsModule, ReactiveFormsModule } from '@angular/forms';
import { coerceBoolean } from '@shared/decorators/coercion';
import { Overlay, OverlayConfig, OverlayRef } from '@angular/cdk/overlay';
import { TemplatePortal } from '@angular/cdk/portal';

import { TranslateService, TranslateModule } from '@ngx-translate/core';
import { deepClone } from '@core/utils';
import { EntityType } from '@shared/models/entity-type.models';
import { fromEvent, Subject, Subscription } from 'rxjs';
import { POSITION_MAP } from '@app/shared/models/overlay.models';
import { ClientType, clientTypeIcon, clientTypeTranslationMap } from '@shared/models/client.model';
import { SharedSubscriptionFilterConfig, sharedSubscriptionFilterConfigEquals } from '@shared/models/shared-subscription.model';
import { NgTemplateOutlet } from '@angular/common';
import { MatButton } from '@angular/material/button';
import { MatTooltip } from '@angular/material/tooltip';
import { MatIcon } from '@angular/material/icon';
import { MatFormField } from '@angular/material/form-field';
import { MatInput } from '@angular/material/input';
import { takeUntil } from 'rxjs/operators';

export const SHARED_SUBSCRIPTION_FILTER_CONFIG_DATA = new InjectionToken<any>('SharedSubscriptionFilterConfigData');

export interface SharedSubscriptionFilterConfigData {
  panelMode: boolean;
  sharedSubscriptionFilterConfig: SharedSubscriptionFilterConfig;
  initialSharedSubscriptionFilterConfig?: SharedSubscriptionFilterConfig;
}

// @dynamic
@Component({
    selector: 'tb-shared-subscription-groups-filter-config',
    templateUrl: './shared-subscription-groups-filter-config.component.html',
    styleUrls: ['./shared-subscription-groups-filter-config.component.scss'],
    providers: [
        {
            provide: NG_VALUE_ACCESSOR,
            useExisting: forwardRef(() => SharedSubscriptionGroupsFilterConfigComponent),
            multi: true
        }
    ],
    imports: [NgTemplateOutlet, MatButton, MatTooltip, MatIcon, FormsModule, ReactiveFormsModule, TranslateModule, MatFormField, MatInput]
})
export class SharedSubscriptionGroupsFilterConfigComponent implements OnInit, OnDestroy, ControlValueAccessor {

  readonly sharedSubscriptionFilterPanel = viewChild<TemplateRef<any>>('sharedSubscriptionFilterPanel');

  @Input()
  initialSharedSubscriptionFilterConfig: SharedSubscriptionFilterConfig;

  disabled = model<boolean>();
  readonly buttonMode = input(true, {transform: booleanAttribute});
  readonly propagatedFilter = input(true, {transform: booleanAttribute});

  ClientType = ClientType;
  clientTypes = [ClientType.APPLICATION, ClientType.DEVICE];
  clientTypeTranslationMap = clientTypeTranslationMap;
  clientTypeIcon = clientTypeIcon;
  cleanStartList = [true, false];
  panelMode = false;
  buttonDisplayValue = this.translate.instant('mqtt-client-session.filter-title');
  buttonDisplayTooltip: string;
  sharedSubscriptionFilterConfigForm: UntypedFormGroup;
  sharedSubscriptionFilterOverlayRef: OverlayRef;
  panelResult: SharedSubscriptionFilterConfig = null;
  entityType = EntityType;

  private sharedSubscriptionFilterConfig: SharedSubscriptionFilterConfig;
  private resizeWindows: Subscription;
  private destroy$ = new Subject<void>();
  private propagateChange = (_: any) => {};

  constructor(@Optional() @Inject(SHARED_SUBSCRIPTION_FILTER_CONFIG_DATA)
              private data: SharedSubscriptionFilterConfigData | undefined,
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
      this.sharedSubscriptionFilterConfig = this.data.sharedSubscriptionFilterConfig;
      this.initialSharedSubscriptionFilterConfig = this.data.initialSharedSubscriptionFilterConfig;
      if (this.panelMode && !this.initialSharedSubscriptionFilterConfig) {
        this.initialSharedSubscriptionFilterConfig = deepClone(this.sharedSubscriptionFilterConfig);
      }
    }
    this.sharedSubscriptionFilterConfigForm = this.fb.group({
      shareNameSearch: [null, []],
      topicFilter: [null, []],
      clientIdSearch: [null, []]
    });
    this.sharedSubscriptionFilterConfigForm.valueChanges
      .pipe(takeUntil(this.destroy$))
      .subscribe(() => {
        if (!this.buttonMode()) {
          this.sharedSubscriptionConfigUpdated(this.sharedSubscriptionFilterConfigForm.value);
        }
      });
    if (this.panelMode) {
      this.updateSharedSubscriptionConfigForm(this.sharedSubscriptionFilterConfig);
    }
    this.initialSharedSubscriptionFilterConfig = this.sharedSubscriptionFilterConfigForm.getRawValue();
  }

  ngOnDestroy(): void {
    this.destroy$.next();
    this.destroy$.complete();}

  registerOnChange(fn: any): void {
    this.propagateChange = fn;
  }

  registerOnTouched(fn: any): void {
  }

  setDisabledState(isDisabled: boolean): void {
    this.disabled.set(isDisabled);
    if (this.disabled()) {
      this.sharedSubscriptionFilterConfigForm.disable({emitEvent: false});
    } else {
      this.sharedSubscriptionFilterConfigForm.enable({emitEvent: false});
    }
  }

  writeValue(sharedSubscriptionFilterConfig?: SharedSubscriptionFilterConfig): void {
    this.sharedSubscriptionFilterConfig = sharedSubscriptionFilterConfig;
    if (!this.initialSharedSubscriptionFilterConfig && sharedSubscriptionFilterConfig) {
      this.initialSharedSubscriptionFilterConfig = deepClone(sharedSubscriptionFilterConfig);
    }
    this.updateButtonDisplayValue();
    this.updateSharedSubscriptionConfigForm(sharedSubscriptionFilterConfig);
  }

  toggleSharedSubscriptionFilterPanel($event: Event) {
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

    this.sharedSubscriptionFilterOverlayRef = this.overlay.create(config);
    this.sharedSubscriptionFilterOverlayRef.backdropClick().subscribe(() => {
      this.sharedSubscriptionFilterOverlayRef.dispose();
    });
    this.sharedSubscriptionFilterOverlayRef.attach(new TemplatePortal(this.sharedSubscriptionFilterPanel(),
      this.viewContainerRef));
    this.resizeWindows = fromEvent(window, 'resize').subscribe(() => {
      this.sharedSubscriptionFilterOverlayRef.updatePosition();
    });
  }

  cancel() {
    this.updateSharedSubscriptionConfigForm(this.sharedSubscriptionFilterConfig);
    if (this.overlayRef) {
      this.overlayRef.dispose();
    } else {
      this.resizeWindows.unsubscribe();
      this.sharedSubscriptionFilterOverlayRef.dispose();
    }
  }

  update() {
    this.sharedSubscriptionConfigUpdated(this.sharedSubscriptionFilterConfigForm.value);
    this.sharedSubscriptionFilterConfigForm.markAsPristine();
    if (this.panelMode) {
      this.panelResult = this.sharedSubscriptionFilterConfig;
    }
    if (this.overlayRef) {
      this.overlayRef.dispose();
    } else {
      this.resizeWindows.unsubscribe();
      this.sharedSubscriptionFilterOverlayRef.dispose();
    }
  }

  reset() {
    const initialSharedSubscriptionFilterConfig = this.initialSharedSubscriptionFilterConfig;
    if (initialSharedSubscriptionFilterConfig) {
      if (this.buttonMode() || this.panelMode) {
        const sharedSubscriptionFilterConfig = this.sharedSubscriptionFilterConfigFromFormValue(this.sharedSubscriptionFilterConfigForm.value);
        if (!sharedSubscriptionFilterConfigEquals(sharedSubscriptionFilterConfig, initialSharedSubscriptionFilterConfig)) {
          this.updateSharedSubscriptionConfigForm(initialSharedSubscriptionFilterConfig);
          this.sharedSubscriptionFilterConfigForm.markAsDirty();
        }
      } else {
        if (!sharedSubscriptionFilterConfigEquals(this.sharedSubscriptionFilterConfig, initialSharedSubscriptionFilterConfig)) {
          this.sharedSubscriptionFilterConfig = initialSharedSubscriptionFilterConfig;
          this.updateButtonDisplayValue();
          this.updateSharedSubscriptionConfigForm(this.sharedSubscriptionFilterConfig);
          this.propagateChange(this.sharedSubscriptionFilterConfig);
        }
      }
    }
  }

  private updateSharedSubscriptionConfigForm(sharedSubscriptionFilterConfig?: SharedSubscriptionFilterConfig) {
    this.sharedSubscriptionFilterConfigForm.patchValue({
      shareNameSearch: sharedSubscriptionFilterConfig?.shareNameSearch,
      topicFilter: sharedSubscriptionFilterConfig?.topicFilter,
      clientIdSearch: sharedSubscriptionFilterConfig?.clientIdSearch
    }, {emitEvent: false});
  }

  private sharedSubscriptionConfigUpdated(formValue: any) {
    this.sharedSubscriptionFilterConfig = this.sharedSubscriptionFilterConfigFromFormValue(formValue);
    this.updateButtonDisplayValue();
    this.propagateChange(this.sharedSubscriptionFilterConfig);
  }

  private sharedSubscriptionFilterConfigFromFormValue(formValue: any): SharedSubscriptionFilterConfig {
    return {
      shareNameSearch: formValue.shareNameSearch,
      topicFilter: formValue.topicFilter,
      clientIdSearch: formValue.clientIdSearch
    };
  }

  private updateButtonDisplayValue() {
    if (this.buttonMode()) {
      const filterTextParts: string[] = [];
      const filterTooltipParts: string[] = [];
      if (this.sharedSubscriptionFilterConfig?.shareNameSearch?.length) {
        const shareNameSearch = this.sharedSubscriptionFilterConfig.shareNameSearch;
        filterTextParts.push(shareNameSearch);
        filterTooltipParts.push(`${this.translate.instant('shared-subscription.share-name')}: ${shareNameSearch}`);
      }
      if (this.sharedSubscriptionFilterConfig?.topicFilter?.length) {
        const topicFilter = this.sharedSubscriptionFilterConfig.topicFilter;
        filterTextParts.push(topicFilter);
        filterTooltipParts.push(`${this.translate.instant('shared-subscription.topic-filter')}: ${topicFilter}`);
      }
      if (this.sharedSubscriptionFilterConfig?.clientIdSearch?.length) {
        const clientIdSearch = this.sharedSubscriptionFilterConfig.clientIdSearch;
        filterTextParts.push(clientIdSearch);
        filterTooltipParts.push(`${this.translate.instant('shared-subscription.client')}: ${clientIdSearch}`);
      }
      if (!filterTextParts.length) {
        this.buttonDisplayValue = this.translate.instant('mqtt-client-session.filter-title');
        this.buttonDisplayTooltip = null;
      } else {
        this.buttonDisplayValue = this.translate.instant('mqtt-client-session.filter-title') + `: ${filterTextParts.join('; ')}`;
        this.buttonDisplayTooltip = filterTooltipParts.join('; ');
      }
    }
  }
}
