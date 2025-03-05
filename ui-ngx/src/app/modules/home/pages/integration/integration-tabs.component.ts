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

import { Component, Input } from '@angular/core';
import { Store } from '@ngrx/store';
import { AppState } from '@core/core.state';
import { EntityTabsComponent } from '../../components/entity/entity-tabs.component';
import { Integration, IntegrationInfo } from '@shared/models/integration.models';
import { PageLink } from '@shared/models/page/page-link';
import { MatTab } from '@angular/material/tabs';
import { TranslateModule } from '@ngx-translate/core';
import { EventTableComponent } from '@home/components/event/event-table.component';
import { EventType } from '@shared/models/event.models';

@Component({
  selector: 'tb-integration-tabs',
  templateUrl: './integration-tabs.component.html',
  imports: [
    MatTab,
    TranslateModule,
    EventTableComponent
  ]
})
export class IntegrationTabsComponent extends EntityTabsComponent<Integration, PageLink, IntegrationInfo> {

  private defaultEventTypeValue: EventType = EventType.LC_EVENT;

  get defaultEventType(): EventType {
    return this.defaultEventTypeValue;
  }

  @Input()
  set defaultEventType(value: EventType) {
    this.defaultEventTypeValue = value || EventType.ERROR;
  }

  constructor(protected store: Store<AppState>) {
    super(store);
  }

  ngOnInit() {
    super.ngOnInit();
  }

}
