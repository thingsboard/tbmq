///
/// Copyright © 2016-2023 The Thingsboard Authors
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

import { Component, OnInit, ViewChild } from '@angular/core';
import { TranslateService } from "@ngx-translate/core";
import { MatAccordion } from '@angular/material/expansion';

@Component({
  selector: 'tb-getting-started',
  templateUrl: './getting-started.component.html',
  styleUrls: ['./getting-started.component.scss']
})
export class GettingStartedComponent implements OnInit {

  @ViewChild(MatAccordion) accordion: MatAccordion;
  step1: boolean;
  step2: boolean;
  step3: boolean;
  step4: boolean;
  step5: boolean;
  step6: boolean;

  constructor(private translate: TranslateService) { }

  ngOnInit(): void {
    console.log('this.accordion', this.accordion);
  }

  expandedChange(step: number) {
    switch (step) {
      case 1:
        // this.step2 = !this.step1;
        break;
      case 2:
        // this.step1 = !this.step1;
        break;
      case 3:
        // this.step3 = !this.step3;
        break;
      case 4:
        // this.step3 = !this.step3;
        break;
      case 5:
        // this.step3 = !this.step3;
        break;
      case 6:
        // this.step3 = !this.step3;
        break;
    }
  }
}
