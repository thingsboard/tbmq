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
  ChangeDetectorRef,
  Component,
  ElementRef,
  forwardRef,
  OnChanges,
  OnDestroy,
  OnInit,
  SimpleChanges,
  input, model,
  output,
  viewChild
} from '@angular/core';
import {
  AbstractControl,
  ControlValueAccessor,
  NG_VALIDATORS,
  NG_VALUE_ACCESSOR,
  UntypedFormControl,
  Validator
} from '@angular/forms';
import { Ace } from 'ace-builds';
import { ActionNotificationHide, ActionNotificationShow } from '@core/notification/notification.actions';
import { Store } from '@ngrx/store';
import { AppState } from '@core/core.state';
import { CancelAnimationFrame, RafService } from '@core/services/raf.service';
import { guid, isDefinedAndNotNull, isObject, isUndefined } from '@core/utils';
import { ResizeObserver } from '@juggle/resize-observer';
import { getAce } from '@shared/models/ace/ace.models';
import { FullscreenDirective } from '@shared/components/fullscreen.directive';
import { MatButton, MatIconButton } from '@angular/material/button';
import { MatTooltip } from '@angular/material/tooltip';
import { MatIcon } from '@angular/material/icon';
import { ToastDirective } from '@shared/components/toast.directive';
import { TranslateModule } from '@ngx-translate/core';

@Component({
    selector: 'tb-ws-json-object-edit',
    templateUrl: './ws-json-object-edit.component.html',
    styleUrls: ['./ws-json-object-edit.component.scss'],
    providers: [
        {
            provide: NG_VALUE_ACCESSOR,
            useExisting: forwardRef(() => WsJsonObjectEditComponent),
            multi: true
        },
        {
            provide: NG_VALIDATORS,
            useExisting: forwardRef(() => WsJsonObjectEditComponent),
            multi: true,
        }
    ],
    imports: [FullscreenDirective, MatButton, MatIconButton, MatTooltip, MatIcon, ToastDirective, TranslateModule]
})
export class WsJsonObjectEditComponent implements OnInit, ControlValueAccessor, Validator, OnDestroy, OnChanges {

  readonly jsonEditorElmRef = viewChild<ElementRef>('jsonEditor');

  readonly isJsonValid = output<boolean>();

  private jsonEditor: Ace.Editor;
  private editorsResizeCaf: CancelAnimationFrame;
  private editorResize$: ResizeObserver;

  toastTargetId = `jsonObjectEditor-${guid()}`;

  disabled = model<boolean>();
  readonly label = input<string>();
  readonly fillHeight = input<boolean>();
  readonly editorStyle = input<{
      [klass: string]: any;
  }>();
  readonly sort = input<(key: string, value: any) => any>();
  readonly jsonRequired = input<boolean>();
  readonly readonly = input<boolean>();
  readonly jsonFormatSelected = input<boolean>();

  fullscreen = false;

  modelValue: any;

  contentValue: string;

  objectValid: boolean;

  validationError: string;

  errorShowed = false;

  ignoreChange = false;

  private propagateChange = null;

  constructor(public elementRef: ElementRef,
              protected store: Store<AppState>,
              private raf: RafService,
              private cd: ChangeDetectorRef) {
  }

  ngOnInit(): void {
    const editorElement = this.jsonEditorElmRef().nativeElement;
    let editorOptions: Partial<Ace.EditorOptions> = {
      mode: 'ace/mode/json',
      showGutter: false,
      showPrintMargin: false,
      readOnly: this.disabled() || this.readonly()
    };

    const advancedOptions = {
      enableSnippets: true,
      enableBasicAutocompletion: false,
      enableLiveAutocompletion: false,
      highlightActiveLine: false
    };

    editorOptions = {...editorOptions, ...advancedOptions};
    getAce().subscribe(
      (ace) => {
        this.jsonEditor = ace.edit(editorElement, editorOptions);
        this.jsonEditor.session.setUseWrapMode(false);
        this.jsonEditor.setValue(this.contentValue ? this.contentValue : '', -1);
        this.jsonEditor.setReadOnly(this.disabled() || this.readonly());
        this.jsonEditor.on('change', () => {
          if (!this.ignoreChange) {
            this.cleanupJsonErrors();
            this.updateView();
          }
        });
        this.editorResize$ = new ResizeObserver(() => {
          this.onAceEditorResize();
        });
        this.editorResize$.observe(editorElement);
      }
    );
  }

  ngOnDestroy(): void {
    if (this.editorResize$) {
      this.editorResize$.disconnect();
    }
    if (this.jsonEditor) {
      this.jsonEditor.destroy();
    }
  }

  ngOnChanges(changes: SimpleChanges): void {
    for (const propName of Object.keys(changes)) {
      const change = changes[propName];
      if (!change.firstChange && change.currentValue !== change.previousValue) {
        if (propName === 'jsonFormatSelected') {
          this.updateView();
        }
      }
    }
  }

  private onAceEditorResize() {
    if (this.editorsResizeCaf) {
      this.editorsResizeCaf();
      this.editorsResizeCaf = null;
    }
    this.editorsResizeCaf = this.raf.raf(() => {
      this.jsonEditor.resize();
      this.jsonEditor.renderer.updateFull();
    });
  }

  registerOnChange(fn: any): void {
    this.propagateChange = fn;
  }

  registerOnTouched(fn: any): void {
  }

  setDisabledState(isDisabled: boolean): void {
    this.disabled.set(isDisabled);
    if (this.jsonEditor) {
      this.jsonEditor.setReadOnly(this.disabled() || this.readonly());
    }
  }

  public validate(c: UntypedFormControl) {
    if (!this.jsonFormatSelected()) {
      return null;
    } else {
      return (this.objectValid) ? null : {
        jsonParseError: {
          valid: false,
        },
      };
    }
  }

  validateOnSubmit(): void {
    if (!this.disabled() && !this.readonly()) {
      this.cleanupJsonErrors();
      if (!this.objectValid) {
        this.store.dispatch(new ActionNotificationShow(
          {
            message: this.validationError,
            type: 'error',
            target: this.toastTargetId,
            verticalPosition: 'bottom',
            horizontalPosition: 'left'
          }));
        this.errorShowed = true;
      }
    }
  }

  cleanupJsonErrors(): void {
    if (this.errorShowed) {
      this.store.dispatch(new ActionNotificationHide(
        {
          target: this.toastTargetId
        }));
      this.errorShowed = false;
    }
  }

  beautifyJSON() {
    if (this.jsonEditor && this.objectValid) {
      const res = JSON.stringify(this.modelValue, null, 2);
      this.jsonEditor.setValue(res ? res : '', -1);
      // this.updateView();
    }
  }

  minifyJSON() {
    if (this.jsonEditor && this.objectValid) {
      const res = JSON.stringify(this.modelValue);
      this.jsonEditor.setValue(res ? res : '', -1);
      // this.updateView();
    }
  }

  writeValue(value: any): void {
    this.modelValue = value;
    this.contentValue = '';
    this.objectValid = false;
    try {
      if (isDefinedAndNotNull(this.modelValue)) {
        this.contentValue = JSON.stringify(this.modelValue, isUndefined(this.sort()) ? undefined :
          (key, objectValue) => this.sort()(key, objectValue), 2);
        this.objectValid = true;
      } else {
        this.objectValid = !this.jsonRequired();
        this.validationError = 'Json object is required.';
      }
    } catch (e) {
      //
    }
    if (this.jsonEditor) {
      this.ignoreChange = true;
      this.jsonEditor.setValue(this.contentValue ? this.contentValue : '', -1);
      this.ignoreChange = false;
    }
  }

  updateView() {
    const editorValue = this.jsonEditor.getValue();
    this.propagateChange(editorValue);
    if (this.jsonFormatSelected()) {
      this.contentValue = editorValue;
      let data = null;
      this.objectValid = false;
      if (this.contentValue && this.contentValue.length > 0) {
        try {
          data = JSON.parse(this.contentValue);
          if (!isObject(data)) {
            throw new TypeError(`Value is not a valid JSON`);
          }
          this.objectValid = true;
          this.validationError = '';
        } catch (ex) {
          let errorInfo = 'Error:';
          if (ex.name) {
            errorInfo += ' ' + ex.name + ':';
          }
          if (ex.message) {
            errorInfo += ' ' + ex.message;
          }
          this.validationError = errorInfo;
        }
      } else {
        this.objectValid = !this.jsonRequired();
        this.validationError = this.jsonRequired() ? 'Json object is required.' : '';
      }
      this.isJsonValid.emit(!this.validationError?.length);
      this.modelValue = data;
      this.propagateChange(data);
      this.cd.markForCheck();
    } else {
      this.objectValid = true;
    }
  }

  onFullscreen() {
    if (this.jsonEditor) {
      setTimeout(() => {
        this.jsonEditor.resize();
      }, 0);
    }
  }

}
