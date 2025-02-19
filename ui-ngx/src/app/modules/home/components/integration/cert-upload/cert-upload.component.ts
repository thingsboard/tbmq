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

import { Component, input } from '@angular/core';
import { ReactiveFormsModule, UntypedFormGroup } from '@angular/forms';
import { FileInputComponent } from '@shared/components/file-input.component';
import { TranslateModule } from '@ngx-translate/core';

@Component({
  selector: 'tb-cert-upload',
  templateUrl: './cert-upload.component.html',
  styleUrls: [],
  imports: [
    ReactiveFormsModule,
    FileInputComponent,
    TranslateModule
  ]
})
export class CertUploadComponent {

  readonly form = input<UntypedFormGroup>();

  readonly disabled = input<boolean>();

  readonly ignoreCaCert = input(false);

  constructor() {
  }

}
