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

import { NgModule } from '@angular/core';
import { CommonModule } from '@angular/common';
import { SharedModule } from '@app/shared/shared.module';
import { AddEntityDialogComponent } from '@home/components/entity/add-entity-dialog.component';
import { EntitiesTableComponent } from '@home/components/entity/entities-table.component';
import { DetailsPanelComponent } from '@home/components/details-panel.component';
import { EntityDetailsPanelComponent } from '@home/components/entity/entity-details-panel.component';
import { SharedHomeComponentsModule } from '@home/components/shared-home-components.module';
import { EventContentDialogComponent } from '@home/components/event/event-content-dialog.component';
import {
  ClientCredentialsAutocompleteComponent
} from '@home/components/client-credentials-templates/client-credentials-autocomplete.component';
import { UserPropertiesComponent } from '@home/components/client-credentials-templates/user-properties.component';
import { LastWillComponent } from '@home/components/client-credentials-templates/last-will.component';
import { SubscriptionsComponent } from '@home/components/client-credentials-templates/subscriptions.component';

@NgModule({
  declarations:
    [
      EntitiesTableComponent,
      AddEntityDialogComponent,
      DetailsPanelComponent,
      EntityDetailsPanelComponent,
      EventContentDialogComponent,
      ClientCredentialsAutocompleteComponent,
      UserPropertiesComponent,
      LastWillComponent,
      SubscriptionsComponent
    ],
  imports: [
    CommonModule,
    SharedModule,
    SharedHomeComponentsModule
  ],
  exports: [
    EntitiesTableComponent,
    AddEntityDialogComponent,
    DetailsPanelComponent,
    EntityDetailsPanelComponent,
    ClientCredentialsAutocompleteComponent,
    UserPropertiesComponent,
    LastWillComponent,
    SubscriptionsComponent
  ]
})
export class HomeComponentsModule {
}
