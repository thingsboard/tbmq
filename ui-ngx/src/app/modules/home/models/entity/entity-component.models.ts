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

import {BaseData} from '@shared/models/base-data';
import { EntityTableConfig, HeaderActionDescriptor } from './entities-table-config.models';
import { EntityType, EntityTypeResource, EntityTypeTranslation } from "@shared/models/entity-type.models";
import { EntityComponent } from "@home/components/entity/entity.component";
import { Type } from "@angular/core";

export interface AddEntityDialogData<T extends BaseData> {
  entitiesTableConfig: EntityTableConfig<T>;
}

export interface EntityAction<T extends BaseData> {
  event: Event;
  action: string;
  entity: T;
}

export abstract class CardComponent<T extends BaseData> {

  constructor() {}

  entitType: EntityType = null;
  entityComponent: Type<EntityComponent<T>>;
  entityResources: EntityTypeResource<T>;
  entityTranslation: EntityTypeTranslation;
  cardTitle = '';
  docsEnabled = true;
  headerActionDescriptors: Array<HeaderActionDescriptor>;
}
