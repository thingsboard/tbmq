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

import { ChangeDetectorRef, Component, OnInit, ViewChild } from '@angular/core';
import { TranslateService } from "@ngx-translate/core";
import { MatAccordion } from '@angular/material/expansion';
import { of } from 'rxjs';
import { number } from 'prop-types';

@Component({
  selector: 'tb-getting-started',
  templateUrl: './getting-started.component.html',
  styleUrls: ['./getting-started.component.scss']
})
export class GettingStartedComponent implements OnInit {

  steps = of([
    {
      position: 1,
      title: 'Create client',
      showButton: false,
      buttonTitle: 'Add client',
      isOpen: false
    },
    {
      position: 2,
      title: 'Subscribe for topic',
      showButton: false,
      isOpen: false
    },
    {
      position: 3,
      title: 'Publish message',
      showButton: false,
      isOpen: false
    },
    {
      position: 4,
      title: 'Disconnect client',
      showButton: false,
      isOpen: false
    }
  ]);

  @ViewChild(MatAccordion) accordion: MatAccordion;

  expandedStep = 1;

  constructor(private translate: TranslateService,
              private cd: ChangeDetectorRef) { }

  ngOnInit(): void {
    console.log('this.accordion', this.accordion);
  }

  expandedChange(index: number) {
    this.expandedStep = index;
    this.cd.detectChanges();
  }

  stepActive(index): boolean {
    console.log('this.expandedStepIndex', this.expandedStep);
    return index + 1 === this.expandedStep;
  }

  stepDone(index): boolean {
    return this.expandedStep > index + 1;
  }

  stepNotDone(index): boolean {
    return this.expandedStep < index + 1 ;
  }

  getContent(position: number) {
    switch (position) {
      case 1:
        return this.translate.instant('getting-started.connect-client.add') + '<br><br>' +
          this.translate.instant('getting-started.connect-client.name') + '<br><br>' +
          this.translate.instant('getting-started.connect-client.username') + '<br><br>' +
          this.translate.instant('getting-started.connect-client.save');
      case 2:
        return this.translate.instant('getting-started.start-session.add') + '<br><br>' +
          this.translate.instant('getting-started.start-session.details');
      case 3:
        return 'Once you have a connected client, you can also publish messages to a topic:\n' +
          '\n' +
          '              tabs: Linux/Windows/Mac OS:\n' +
          '\n' +
          '              mosquitto_pub -h localhost -p 1883 -V mqttv5 -t test_topic -q 1 -m "Hello, MQTT"';
      case 4:
        return 'Once you are finished using the client, you can disconnect the client.';
      case 5:
        return 'Change password';
      case 6:
        return 'Add admins';
    }
  }
}
