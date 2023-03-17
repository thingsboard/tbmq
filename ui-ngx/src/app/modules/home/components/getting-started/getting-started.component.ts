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

  markdown = `### Create Client

The first step is to create the MQTT Client with a simple Authentication method based on Username and Password.
Please click on the button below and fill the following fields:
* name: 'TB MQTT Demo' (can be any)
* username: 'tb_mqtt_demo'
* password: 'tb_mqtt_demo'

<See Clients>

<-- Additional info-->
(Info)
Note that Thingsboard MQTT Broker has different Authentication and Authorization methods.
<Read docs>

\`\`\`bash
mosquitto_sub -h localhost -p 1883 --qos 0 -i tb_mqtt_demo -P tb_mqtt_demo3 -u tb_mqtt_demo -t demo/tb_mqtt -k 100 -x 100 -V mqttv5
{:copy-code}
\`\`\``;

  constructor(private translate: TranslateService,
              private cd: ChangeDetectorRef) { }

  ngOnInit(): void {
  }

  expandedChange(index: number) {
    this.expandedStep = index;
    this.cd.detectChanges();
  }

  stepActive(index): boolean {
    return index + 1 === this.expandedStep;
  }

  stepDone(index): boolean {
    return this.expandedStep > index + 1;
  }

  stepNotDone(index): boolean {
    return this.expandedStep < index + 1 ;
  }

  addClient() {

  }

  onCopy(content) {

  }
}
