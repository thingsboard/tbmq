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
  input,
  model, Input,
  viewChild
} from '@angular/core';
import { ControlValueAccessor, NG_VALUE_ACCESSOR, UntypedFormBuilder, UntypedFormGroup, FormsModule, ReactiveFormsModule } from '@angular/forms';
import { Overlay, OverlayConfig, OverlayRef } from '@angular/cdk/overlay';
import { TemplatePortal } from '@angular/cdk/portal';

import { TranslateService, TranslateModule } from '@ngx-translate/core';
import { deepClone, isNotEmptyStr } from '@core/utils';
import { EntityType } from '@shared/models/entity-type.models';
import { fromEvent, Subject, Subscription } from 'rxjs';
import { POSITION_MAP } from '@app/shared/models/overlay.models';
import { NgTemplateOutlet } from '@angular/common';
import { MatButton } from '@angular/material/button';
import { MatTooltip } from '@angular/material/tooltip';
import { MatIcon } from '@angular/material/icon';
import { MatChipListbox, MatChipOption } from '@angular/material/chips';
import { MatFormField, MatSuffix } from '@angular/material/form-field';
import { MatInput } from '@angular/material/input';
import { takeUntil } from 'rxjs/operators';
import {
  BlockedClientFilterConfig,
  blockedClientsFilterConfigEquals,
  BlockedClientType,
  blockedClientTypeTranslationMap,
  RegexMatchTarget,
  regexMatchTargetTranslationMap
} from '@shared/models/blocked-client.models';

export const BLOCKED_CLIENTS_FILTER_CONFIG_DATA = new InjectionToken<any>('BlockedClientsFilterConfigData');

export interface BlockedClientsFilterConfigData {
  panelMode: boolean;
  blockedClientsFilterConfig: BlockedClientFilterConfig;
  initialBlockedClientsFilterConfig?: BlockedClientFilterConfig;
}

// @dynamic
@Component({
    selector: 'tb-blocked-clients-filter-config',
    templateUrl: './blocked-clients-filter-config.component.html',
    styleUrls: ['./blocked-clients-filter-config.component.scss'],
    providers: [
        {
            provide: NG_VALUE_ACCESSOR,
            useExisting: forwardRef(() => BlockedClientsFilterConfigComponent),
            multi: true
        }
    ],
    imports: [NgTemplateOutlet, MatButton, MatTooltip, MatIcon, FormsModule, ReactiveFormsModule, TranslateModule, MatChipListbox, MatChipOption, MatFormField, MatInput, MatSuffix]
})
export class BlockedClientsFilterConfigComponent implements OnInit, OnDestroy, ControlValueAccessor {

  readonly blockedClientsFilterPanel = viewChild<TemplateRef<any>>('blockedClientsPanel');

  @Input()
  initialBlockedClientsFilterConfig: BlockedClientFilterConfig;

  disabled = model<boolean>();
  readonly buttonMode = input(true);

  blockedClientTypes = Object.values(BlockedClientType);
  blockedClientTypeTranslationMap = blockedClientTypeTranslationMap;

  regexMatchTargets = Object.values(RegexMatchTarget);
  regexMatchTargetTranslationMap = regexMatchTargetTranslationMap;

  panelMode = false;
  buttonDisplayValue = this.translate.instant('blocked-client.filter-title');
  buttonDisplayTooltip: string;
  filterConfigForm: UntypedFormGroup;
  clientCredentialsFilterOverlayRef: OverlayRef;
  panelResult: BlockedClientFilterConfig = null;
  entityType = EntityType;

  private blockedClientsFilterConfig: BlockedClientFilterConfig;
  private resizeWindows: Subscription;
  private destroy$ = new Subject<void>();
  private propagateChange = (_: any) => {};

  constructor(@Optional() @Inject(BLOCKED_CLIENTS_FILTER_CONFIG_DATA)
              private data: BlockedClientsFilterConfigData | undefined,
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
      this.blockedClientsFilterConfig = this.data.blockedClientsFilterConfig;
      this.initialBlockedClientsFilterConfig = this.data.initialBlockedClientsFilterConfig;
      if (this.panelMode && !this.initialBlockedClientsFilterConfig) {
        this.initialBlockedClientsFilterConfig = deepClone(this.blockedClientsFilterConfig);
      }
    }
    this.filterConfigForm = this.fb.group({
      value: [null, []],
      typeList: [null, []],
      regexMatchTargetList: [null, []],
    });
    this.filterConfigForm.valueChanges
      .pipe(takeUntil(this.destroy$))
      .subscribe(() => {
        if (!this.buttonMode()) {
          this.configUpdated(this.filterConfigForm.value);
        }
      });
    if (this.panelMode) {
      this.updateClientCredentialsConfigForm(this.blockedClientsFilterConfig);
    }
    this.initialBlockedClientsFilterConfig = this.filterConfigForm.getRawValue();
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
      this.filterConfigForm.disable({emitEvent: false});
    } else {
      this.filterConfigForm.enable({emitEvent: false});
    }
  }

  writeValue(filterConfig?: BlockedClientFilterConfig): void {
    this.blockedClientsFilterConfig = filterConfig;
    if (!this.initialBlockedClientsFilterConfig && filterConfig) {
      this.initialBlockedClientsFilterConfig = deepClone(filterConfig);
    }
    this.updateButtonDisplayValue();
    this.updateClientCredentialsConfigForm(filterConfig);
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

    this.clientCredentialsFilterOverlayRef = this.overlay.create(config);
    this.clientCredentialsFilterOverlayRef.backdropClick().subscribe(() => {
      this.clientCredentialsFilterOverlayRef.dispose();
    });
    this.clientCredentialsFilterOverlayRef.attach(new TemplatePortal(this.blockedClientsFilterPanel(),
      this.viewContainerRef));
    this.resizeWindows = fromEvent(window, 'resize').subscribe(() => {
      this.clientCredentialsFilterOverlayRef.updatePosition();
    });
  }

  cancel() {
    this.updateClientCredentialsConfigForm(this.blockedClientsFilterConfig);
    if (this.overlayRef) {
      this.overlayRef.dispose();
    } else {
      this.resizeWindows.unsubscribe();
      this.clientCredentialsFilterOverlayRef.dispose();
    }
  }

  update() {
    this.configUpdated(this.filterConfigForm.value);
    this.filterConfigForm.markAsPristine();
    if (this.panelMode) {
      this.panelResult = this.blockedClientsFilterConfig;
    }
    if (this.overlayRef) {
      this.overlayRef.dispose();
    } else {
      this.resizeWindows.unsubscribe();
      this.clientCredentialsFilterOverlayRef.dispose();
    }
  }

  reset() {
    const initialClientCredentialsFilterConfig = this.initialBlockedClientsFilterConfig;
    if (initialClientCredentialsFilterConfig) {
      if (this.buttonMode() || this.panelMode) {
        const clientCredentialsFilterConfig = this.filterConfigFromFormValue(this.filterConfigForm.value);
        if (!blockedClientsFilterConfigEquals(clientCredentialsFilterConfig, initialClientCredentialsFilterConfig)) {
          this.updateClientCredentialsConfigForm(initialClientCredentialsFilterConfig);
          this.filterConfigForm.markAsDirty();
        }
      } else {
        if (!blockedClientsFilterConfigEquals(this.blockedClientsFilterConfig, initialClientCredentialsFilterConfig)) {
          this.blockedClientsFilterConfig = initialClientCredentialsFilterConfig;
          this.updateButtonDisplayValue();
          this.updateClientCredentialsConfigForm(this.blockedClientsFilterConfig);
          this.propagateChange(this.blockedClientsFilterConfig);
        }
      }
    }
  }

  private updateClientCredentialsConfigForm(filterConfig?: BlockedClientFilterConfig) {
    this.filterConfigForm.patchValue({
      value: filterConfig?.value,
      typeList: filterConfig?.typeList,
      regexMatchTargetList: filterConfig?.regexMatchTargetList,
    }, {emitEvent: false});
  }

  private configUpdated(formValue: any) {
    this.blockedClientsFilterConfig = this.filterConfigFromFormValue(formValue);
    this.updateButtonDisplayValue();
    this.propagateChange(this.blockedClientsFilterConfig);
  }

  private filterConfigFromFormValue(formValue: BlockedClientFilterConfig): BlockedClientFilterConfig {
    return {
      value: formValue.value,
      typeList: formValue.typeList,
      regexMatchTargetList: formValue.regexMatchTargetList,
    };
  }

  private updateButtonDisplayValue() {
    if (this.buttonMode()) {
      const filterTextParts: string[] = [];
      const filterTooltipParts: string[] = [];
      if (this.blockedClientsFilterConfig?.typeList?.length) {
        const typeList = this.blockedClientsFilterConfig.typeList.map(s =>
          this.translate.instant(this.blockedClientTypeTranslationMap.get(s))).join(', ');
        filterTextParts.push(typeList);
        filterTooltipParts.push(`${this.translate.instant('blocked-client.type')}: ${typeList}`)
      }
      if (this.blockedClientsFilterConfig?.regexMatchTargetList?.length) {
        const regexMatchTargetList = `${this.translate.instant('blocked-client.regex-match-target')}: ${this.blockedClientsFilterConfig.regexMatchTargetList.map(s =>
          this.translate.instant(this.regexMatchTargetTranslationMap.get(s))).join(', ')}`;
        filterTextParts.push(regexMatchTargetList);
        filterTooltipParts.push(`${this.translate.instant('blocked-client.regex-match-target')}: ${regexMatchTargetList}`);
      }
      if (isNotEmptyStr(this.blockedClientsFilterConfig?.value)) {
        const value = `${this.translate.instant('blocked-client.value')}: ${this.blockedClientsFilterConfig.value}`;
        filterTextParts.push(value);
        filterTooltipParts.push(`${this.translate.instant('blocked-client.value')}: ${value}`);
      }
      if (!filterTextParts.length) {
        this.buttonDisplayValue = this.translate.instant('blocked-client.filter-title');
        this.buttonDisplayTooltip = null;
      } else {
        this.buttonDisplayValue = this.translate.instant('blocked-client.filter-title') + `: ${filterTextParts.join(', ')}`;
        this.buttonDisplayTooltip = filterTooltipParts.join('; ');
      }
    }
  }

}
