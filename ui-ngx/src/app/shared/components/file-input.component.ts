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
  AfterViewInit,
  Component,
  ElementRef,
  forwardRef,
  Input,
  OnChanges,
  OnDestroy,
  SimpleChanges,
  input,
  output,
  viewChild, model
} from '@angular/core';
import { PageComponent } from '@shared/components/page.component';
import { Store } from '@ngrx/store';
import { AppState } from '@core/core.state';
import { ControlValueAccessor, NG_VALUE_ACCESSOR } from '@angular/forms';
import { Subscription } from 'rxjs';
import { FlowDirective, FlowInjectionToken, NgxFlowModule } from '@flowjs/ngx-flow';
import Flow from '@flowjs/flow.js';
import { TranslateService, TranslateModule } from '@ngx-translate/core';
import { UtilsService } from '@core/services/utils.service';
import { DialogService } from '@core/services/dialog.service';
import { FileSizePipe } from '@shared/pipe/file-size.pipe';

import { MatIconButton, MatButton } from '@angular/material/button';
import { MatTooltip } from '@angular/material/tooltip';
import { MatIcon } from '@angular/material/icon';
import { HintTooltipIconComponent } from '@shared/components/hint-tooltip-icon.component';
import { TbErrorComponent } from '@shared/components/tb-error.component';

@Component({
  selector: 'tb-file-input',
  templateUrl: './file-input.component.html',
  styleUrls: ['./file-input.component.scss'],
  providers: [
    {
      provide: NG_VALUE_ACCESSOR,
      useExisting: forwardRef(() => FileInputComponent),
      multi: true
    },
    {
      provide: FlowInjectionToken,
      useValue: Flow
    },
    FileSizePipe
  ],
  standalone: true,
  imports: [NgxFlowModule, MatIconButton, MatTooltip, MatIcon, MatButton, TranslateModule, FileSizePipe, HintTooltipIconComponent, TbErrorComponent]
})
export class FileInputComponent extends PageComponent implements AfterViewInit, OnDestroy, ControlValueAccessor, OnChanges {

  readonly label = input<string>();
  readonly hint = input<string>();
  readonly accept = input('*!/!*');
  readonly noFileText = input('import.no-file');
  readonly inputId = input(this.utils.guid());
  readonly allowedExtensions = input<string>();
  readonly dropLabel = input<string>();
  readonly maxSizeByte = input<number>();
  readonly contentConvertFunction = input<(content: string) => any>();
  readonly required = input<boolean>();
  readonly requiredAsError = input<boolean>();
  readonly existingFileName = input<string>();
  readonly readAsBinary = input(false);
  readonly workFromFileObj = input(false);

  disabled = model<boolean>();
  private multipleFileValue = false;

  @Input()
  set multipleFile(value: boolean) {
    this.multipleFileValue = value;
    if (this.flow()?.flowJs) {
      this.updateMultipleFileMode(this.multipleFile);
    }
  }

  get multipleFile(): boolean {
    return this.multipleFileValue;
  }

  readonly fileNameChanged = output<string | string[]>();

  fileName: string | string[];
  fileContent: any;
  files: File[];

  readonly flow = viewChild<FlowDirective>('flow');
  readonly flowInput = viewChild<ElementRef>('flowInput');

  autoUploadSubscription: Subscription;
  private propagateChange = null;

  constructor(protected store: Store<AppState>,
              private utils: UtilsService,
              private translate: TranslateService,
              private dialog: DialogService,
              private fileSize: FileSizePipe) {
    super(store);
  }

  ngAfterViewInit() {
    this.autoUploadSubscription = this.flow().events$.subscribe(event => {
      if (event.type === 'filesAdded') {
        const readers = [];
        let showMaxSizeAlert = false;
        (event.event[0] as flowjs.FlowFile[]).forEach(file => {
          if (this.filterFile(file)) {
            if (this.checkMaxSize(file)) {
              readers.push(this.readerAsFile(file));
            } else {
              showMaxSizeAlert = true;
            }
          }
        });

        if (showMaxSizeAlert) {
          this.dialog.alert(
            this.translate.instant('dashboard.cannot-upload-file'),
            this.translate.instant('dashboard.maximum-upload-file-size', {size: this.fileSize.transform(this.maxSizeByte())})
          ).subscribe(() => { });
        }

        if (readers.length) {
          Promise.all(readers).then((files) => {
            files = files.filter(file => file.fileContent != null || file.files != null);
            if (files.length === 1) {
              this.fileContent = files[0].fileContent;
              this.fileName = files[0].fileName;
              this.files = files[0].files;
              this.updateModel();
            } else if (files.length > 1) {
              this.fileContent = files.map(content => content.fileContent);
              this.fileName = files.map(content => content.fileName);
              this.files = files.map(content => content.files);
              this.updateModel();
            }
          });
        }
      }
    });
    if (!this.multipleFile) {
      this.updateMultipleFileMode(this.multipleFile);
    }
  }

  private readerAsFile(file: flowjs.FlowFile): Promise<any> {
    return new Promise((resolve) => {
      const reader = new FileReader();
      reader.onload = () => {
        let fileName = null;
        let fileContent = null;
        let files = null;
        if (reader.readyState === reader.DONE) {
          if (!this.workFromFileObj()) {
            fileContent = reader.result;
            if (fileContent && fileContent.length > 0) {
              const contentConvertFunction = this.contentConvertFunction();
              if (contentConvertFunction) {
                fileContent = contentConvertFunction(fileContent);
              }
              fileName = fileContent ? file.name : null;
            }
          } else if (file.name || file.file){
            files = file.file;
            fileName = file.name;
          }
        }
        resolve({fileContent, fileName, files});
      };
      reader.onerror = () => {
        resolve({fileContent: null, fileName: null, files: null});
      };
      if (this.readAsBinary()) {
        reader.readAsBinaryString(file.file);
      } else {
        reader.readAsText(file.file);
      }
    });
  }

  private checkMaxSize(file: flowjs.FlowFile): boolean {
    return !this.maxSizeByte() || file.size <= this.maxSizeByte();
  }

  private filterFile(file: flowjs.FlowFile): boolean {
    const allowedExtensions = this.allowedExtensions();
    if (allowedExtensions) {
      return allowedExtensions.split(',').indexOf(file.getExtension()) > -1;
    } else {
      return true;
    }
  }

  ngOnDestroy() {
    if (this.autoUploadSubscription) {
      this.autoUploadSubscription.unsubscribe();
    }
  }

  registerOnChange(fn: any): void {
    this.propagateChange = fn;
  }

  registerOnTouched(fn: any): void {
  }

  setDisabledState(isDisabled: boolean): void {
    this.disabled.set(isDisabled);
  }

  writeValue(value: any): void {
    let fileName = null;
    if (this.workFromFileObj() && value instanceof File) {
      fileName = Array.isArray(value) ? value.map(file => file.name) : value.name;
    }
    this.fileName = this.existingFileName() || fileName;
  }

  ngOnChanges(changes: SimpleChanges): void {
    for (const propName of Object.keys(changes)) {
      const change = changes[propName];
      if (change.currentValue !== change.previousValue) {
        if (propName === 'existingFileName') {
          this.fileName = this.existingFileName() || null;
        }
      }
    }
  }

  private updateModel() {
    if (this.workFromFileObj()) {
      this.propagateChange(this.files);
    } else {
      this.propagateChange(this.fileContent);
      this.fileNameChanged.emit(this.fileName);
    }
  }

  clearFile() {
    this.fileName = null;
    this.fileContent = null;
    this.files = null;
    this.updateModel();
  }

  private updateMultipleFileMode(multiple: boolean) {
    this.flow().flowJs.opts.singleFile = !multiple;
    if (!multiple) {
      this.flowInput().nativeElement.removeAttribute('multiple');
    }
  }
}
