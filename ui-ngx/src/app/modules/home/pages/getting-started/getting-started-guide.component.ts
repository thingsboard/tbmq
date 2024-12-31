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

import { ChangeDetectionStrategy, ChangeDetectorRef, Component, OnInit } from '@angular/core';
import { UntypedFormBuilder } from '@angular/forms';
import { ActivatedRoute } from '@angular/router';
import { InstructionsService } from '@core/http/instructions.service';

@Component({
  selector: 'tb-getting-started-guide',
  templateUrl: './getting-started-guide.component.html',
  styleUrls: ['./getting-started-guide.component.scss'],
  changeDetection: ChangeDetectionStrategy.OnPush
})
export class GettingStartedGuideComponent implements OnInit {

  data: string;

  constructor(private route: ActivatedRoute,
              private cd: ChangeDetectorRef,
              private instructionsService: InstructionsService,
              public fb: UntypedFormBuilder) {
  }

  ngOnInit() {
    this.route.params.subscribe(params => {
      this.init(params?.guideId);
    });
  }

  private init(id: string) {
    this.getStep(id);
  }

  private getStep(id: string) {
    this.instructionsService.getInstruction(id).subscribe(data => {
      this.data = data;
      this.cd.detectChanges()
    });
  }

}
