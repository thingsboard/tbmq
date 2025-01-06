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
import { deepClone } from '@core/utils';
import { EntityType } from '@shared/models/entity-type.models';
import { fromEvent, Subject, Subscription } from 'rxjs';
import { POSITION_MAP } from '@app/shared/models/overlay.models';
import {
  UnauthorizedClientFilterConfig,
  unauthorizedClientFilterConfigEquals
} from '@shared/models/unauthorized-client.model';
import { NgIf, NgTemplateOutlet, NgFor } from '@angular/common';
import { MatButton } from '@angular/material/button';
import { MatTooltip } from '@angular/material/tooltip';
import { MatIcon } from '@angular/material/icon';
import { FlexModule } from '@angular/flex-layout/flex';
import { MatFormField } from '@angular/material/form-field';
import { MatInput } from '@angular/material/input';
import { MatChipListbox, MatChipOption } from '@angular/material/chips';
import { takeUntil } from 'rxjs/operators';

export const UNAUTHORIZED_CLIENT_FILTER_CONFIG_DATA = new InjectionToken<any>('UnauthorizedClientFilterConfigData');

export interface UnauthorizedClientFilterConfigData {
  panelMode: boolean;
  unauthorizedClientFilterConfig: UnauthorizedClientFilterConfig;
  initialUnauthorizedClientFilterConfig?: UnauthorizedClientFilterConfig;
}

// @dynamic
@Component({
    selector: 'tb-unauthorized-client-filter-config',
    templateUrl: './unauthorized-client-filter-config.component.html',
    styleUrls: ['./unauthorized-client-filter-config.component.scss'],
    providers: [
        {
            provide: NG_VALUE_ACCESSOR,
            useExisting: forwardRef(() => UnauthorizedClientFilterConfigComponent),
            multi: true
        }
    ],
    standalone: true,
    imports: [NgIf, NgTemplateOutlet, MatButton, MatTooltip, MatIcon, FormsModule, FlexModule, ReactiveFormsModule, TranslateModule, MatFormField, MatInput, MatChipListbox, NgFor, MatChipOption]
})
export class UnauthorizedClientFilterConfigComponent implements OnInit, OnDestroy, ControlValueAccessor {

  @ViewChild('unauthorizedClientPanel')
  unauthorizedClientFilterPanel: TemplateRef<any>;

  @Input() disabled: boolean;

  @coerceBoolean()
  @Input()
  buttonMode = true;

  @coerceBoolean()
  @Input()
  propagatedFilter = true;

  @Input()
  initialUnauthorizedClientFilterConfig: UnauthorizedClientFilterConfig;

  passwordProvidedList = [true, false];
  tlsUsedList = [true, false];
  panelMode = false;
  buttonDisplayValue = this.translate.instant('unauthorized-client.filter-title');
  buttonDisplayTooltip: string;
  unauthorizedClientFilterConfigForm: UntypedFormGroup;
  unauthorizedClientFilterOverlayRef: OverlayRef;
  panelResult: UnauthorizedClientFilterConfig = null;
  entityType = EntityType;

  private unauthorizedClientFilterConfig: UnauthorizedClientFilterConfig;
  private resizeWindows: Subscription;
  private destroy$ = new Subject<void>();
  private propagateChange = (_: any) => {};

  constructor(@Optional() @Inject(UNAUTHORIZED_CLIENT_FILTER_CONFIG_DATA)
              private data: UnauthorizedClientFilterConfigData | undefined,
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
      this.unauthorizedClientFilterConfig = this.data.unauthorizedClientFilterConfig;
      this.initialUnauthorizedClientFilterConfig = this.data.initialUnauthorizedClientFilterConfig;
      if (this.panelMode && !this.initialUnauthorizedClientFilterConfig) {
        this.initialUnauthorizedClientFilterConfig = deepClone(this.unauthorizedClientFilterConfig);
      }
    }
    this.unauthorizedClientFilterConfigForm = this.fb.group({
      passwordProvidedList: [null, []],
      tlsUsedList: [null, []],
      clientId: [null, []],
      ipAddress: [null, []],
      username: [null, []],
      reason: [null, []],
    });
    this.unauthorizedClientFilterConfigForm.valueChanges
      .pipe(takeUntil(this.destroy$))
      .subscribe(() => {
        if (!this.buttonMode) {
          this.unauthorizedClientConfigUpdated(this.unauthorizedClientFilterConfigForm.value);
        }
      });
    if (this.panelMode) {
      this.updateUnauthorizedClientConfigForm(this.unauthorizedClientFilterConfig);
    }
    this.initialUnauthorizedClientFilterConfig = this.unauthorizedClientFilterConfigForm.getRawValue();
  }

  ngOnDestroy(): void {
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
      this.unauthorizedClientFilterConfigForm.disable({emitEvent: false});
    } else {
      this.unauthorizedClientFilterConfigForm.enable({emitEvent: false});
    }
  }

  writeValue(unauthorizedClientFilterConfig?: UnauthorizedClientFilterConfig): void {
    this.unauthorizedClientFilterConfig = unauthorizedClientFilterConfig;
    if (!this.initialUnauthorizedClientFilterConfig && unauthorizedClientFilterConfig) {
      this.initialUnauthorizedClientFilterConfig = deepClone(unauthorizedClientFilterConfig);
    }
    this.updateButtonDisplayValue();
    this.updateUnauthorizedClientConfigForm(unauthorizedClientFilterConfig);
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

    this.unauthorizedClientFilterOverlayRef = this.overlay.create(config);
    this.unauthorizedClientFilterOverlayRef.backdropClick().subscribe(() => {
      this.unauthorizedClientFilterOverlayRef.dispose();
    });
    this.unauthorizedClientFilterOverlayRef.attach(new TemplatePortal(this.unauthorizedClientFilterPanel,
      this.viewContainerRef));
    this.resizeWindows = fromEvent(window, 'resize').subscribe(() => {
      this.unauthorizedClientFilterOverlayRef.updatePosition();
    });
  }

  cancel() {
    this.updateUnauthorizedClientConfigForm(this.unauthorizedClientFilterConfig);
    if (this.overlayRef) {
      this.overlayRef.dispose();
    } else {
      this.resizeWindows.unsubscribe();
      this.unauthorizedClientFilterOverlayRef.dispose();
    }
  }

  update() {
    this.unauthorizedClientConfigUpdated(this.unauthorizedClientFilterConfigForm.value);
    this.unauthorizedClientFilterConfigForm.markAsPristine();
    if (this.panelMode) {
      this.panelResult = this.unauthorizedClientFilterConfig;
    }
    if (this.overlayRef) {
      this.overlayRef.dispose();
    } else {
      this.resizeWindows.unsubscribe();
      this.unauthorizedClientFilterOverlayRef.dispose();
    }
  }

  reset() {
    if (this.initialUnauthorizedClientFilterConfig) {
      if (this.buttonMode || this.panelMode) {
        const unauthorizedClientFilterConfig = this.unauthorizedClientFilterConfigFromFormValue(this.unauthorizedClientFilterConfigForm.value);
        if (!unauthorizedClientFilterConfigEquals(unauthorizedClientFilterConfig, this.initialUnauthorizedClientFilterConfig)) {
          this.updateUnauthorizedClientConfigForm(this.initialUnauthorizedClientFilterConfig);
          this.unauthorizedClientFilterConfigForm.markAsDirty();
        }
      } else {
        if (!unauthorizedClientFilterConfigEquals(this.unauthorizedClientFilterConfig, this.initialUnauthorizedClientFilterConfig)) {
          this.unauthorizedClientFilterConfig = this.initialUnauthorizedClientFilterConfig;
          this.updateButtonDisplayValue();
          this.updateUnauthorizedClientConfigForm(this.unauthorizedClientFilterConfig);
          this.propagateChange(this.unauthorizedClientFilterConfig);
        }
      }
    }
  }

  private updateUnauthorizedClientConfigForm(unauthorizedClientFilterConfig?: UnauthorizedClientFilterConfig) {
    this.unauthorizedClientFilterConfigForm.patchValue({
      clientId: unauthorizedClientFilterConfig?.clientId,
      ipAddress: unauthorizedClientFilterConfig?.ipAddress,
      username: unauthorizedClientFilterConfig?.username,
      reason: unauthorizedClientFilterConfig?.reason,
      passwordProvidedList: unauthorizedClientFilterConfig?.passwordProvidedList,
      tlsUsedList: unauthorizedClientFilterConfig?.tlsUsedList,
    }, {emitEvent: false});
  }

  private unauthorizedClientConfigUpdated(formValue: any) {
    this.unauthorizedClientFilterConfig = this.unauthorizedClientFilterConfigFromFormValue(formValue);
    this.updateButtonDisplayValue();
    this.propagateChange(this.unauthorizedClientFilterConfig);
  }

  private unauthorizedClientFilterConfigFromFormValue(formValue: any): UnauthorizedClientFilterConfig {
    return {
      clientId: formValue.clientId,
      ipAddress: formValue.ipAddress,
      username: formValue.username,
      reason: formValue.reason,
      passwordProvidedList: formValue.passwordProvidedList,
      tlsUsedList: formValue.tlsUsedList
    };
  }

  private updateButtonDisplayValue() {
    if (this.buttonMode) {
      const filterTextParts: string[] = [];
      const filterTooltipParts: string[] = [];
      if (this.unauthorizedClientFilterConfig?.clientId?.length) {
        const clientId = this.unauthorizedClientFilterConfig.clientId;
        filterTextParts.push(clientId);
        filterTooltipParts.push(`${this.translate.instant('mqtt-client.client-id')}: ${clientId}`);
      }
      if (this.unauthorizedClientFilterConfig?.username?.length) {
        const username = this.unauthorizedClientFilterConfig.username;
        filterTextParts.push(username);
        filterTooltipParts.push(`${this.translate.instant('mqtt-client-credentials.username')}: ${username}`);
      }
      if (this.unauthorizedClientFilterConfig?.ipAddress?.length) {
        const ipAddress = this.unauthorizedClientFilterConfig.ipAddress;
        filterTextParts.push(ipAddress);
        filterTooltipParts.push(`${this.translate.instant('mqtt-client-session.client-ip')}: ${ipAddress}`);
      }
      if (this.unauthorizedClientFilterConfig?.reason?.length) {
        const reason = this.unauthorizedClientFilterConfig.reason;
        filterTextParts.push(reason);
        filterTooltipParts.push(`${this.translate.instant('unauthorized-client.reason')}: ${reason}`);
      }
      if (this.unauthorizedClientFilterConfig?.passwordProvidedList?.length) {
        const passwordProvidedList = this.unauthorizedClientFilterConfig.passwordProvidedList.join(', ');
        filterTextParts.push(`PASS: ${passwordProvidedList}`);
        filterTooltipParts.push(`${this.translate.instant('unauthorized-client.password-provided')}: ${passwordProvidedList}`);
      }
      if (this.unauthorizedClientFilterConfig?.tlsUsedList?.length) {
        const tlsUsedList = this.unauthorizedClientFilterConfig.tlsUsedList.join(', ');
        filterTextParts.push(`TLS: ${tlsUsedList}`);
        filterTooltipParts.push(`${this.translate.instant('unauthorized-client.tls-used')}: ${tlsUsedList}`);
      }
      if (!filterTextParts.length) {
        this.buttonDisplayValue = this.translate.instant('unauthorized-client.filter-title');
        this.buttonDisplayTooltip = null;
      } else {
        this.buttonDisplayValue = this.translate.instant('unauthorized-client.filter-title') + `: ${filterTextParts.join('; ')}`;
        this.buttonDisplayTooltip = filterTooltipParts.join('; ');
      }
    }
  }

}
