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
  ChangeDetectionStrategy,
  ChangeDetectorRef,
  Component,
  ComponentFactoryResolver,
  ComponentRef,
  Injector,
  Input,
  OnDestroy,
  output, OutputRefSubscription,
  viewChild,
  viewChildren
} from '@angular/core';
import { PageComponent } from '@shared/components/page.component';
import { Store } from '@ngrx/store';
import { AppState } from '@core/core.state';
import { EntityTableConfig } from '@home/models/entity/entities-table-config.models';
import { BaseData } from '@shared/models/base-data';
import { EntityTypeResource, EntityTypeTranslation } from '@shared/models/entity-type.models';
import { UntypedFormGroup } from '@angular/forms';
import { EntityComponent } from './entity.component';
import { TbAnchorComponent } from '@shared/components/tb-anchor.component';
import { EntityAction } from '@home/models/entity/entity-component.models';
import { Observable, ReplaySubject, Subscription } from 'rxjs';
import { MatTab, MatTabGroup } from '@angular/material/tabs';
import { EntityTabsComponent } from '@home/components/entity/entity-tabs.component';
import { deepClone, mergeDeep } from '@core/utils';
import { DetailsPanelComponent } from '../details-panel.component';
import { HelpComponent } from '@shared/components/help.component';
import { TranslateModule } from '@ngx-translate/core';

@Component({
    selector: 'tb-entity-details-panel',
    templateUrl: './entity-details-panel.component.html',
    styleUrls: ['./entity-details-panel.component.scss'],
    changeDetection: ChangeDetectionStrategy.OnPush,
    imports: [DetailsPanelComponent, HelpComponent, MatTabGroup, MatTab, TbAnchorComponent, TranslateModule]
})
export class EntityDetailsPanelComponent extends PageComponent implements AfterViewInit, OnDestroy {

  readonly closeEntityDetails = output<void>();
  readonly entityUpdated = output<BaseData>();
  readonly entityAction = output<EntityAction<BaseData>>();

  entityComponentRef: ComponentRef<EntityComponent<BaseData>>;
  entityComponent: EntityComponent<BaseData>;

  entityTabsComponentRef: ComponentRef<EntityTabsComponent<BaseData>>;
  entityTabsComponent: EntityTabsComponent<BaseData>;
  detailsForm: UntypedFormGroup;

  entitiesTableConfigValue: EntityTableConfig<BaseData>;
  isEditValue = false;
  selectedTab = 0;

  readonly entityDetailsFormAnchor = viewChild<TbAnchorComponent>('entityDetailsForm');
  readonly entityTabsAnchor = viewChild<TbAnchorComponent>('entityTabs');
  readonly matTabGroup = viewChild(MatTabGroup);
  readonly inclusiveTabs = viewChildren(MatTab);

  translations: EntityTypeTranslation;
  resources: EntityTypeResource<BaseData>;
  entity: BaseData;
  editingEntity: BaseData;

  protected currentEntityId: string;
  protected subscriptions: OutputRefSubscription[] = [];
  protected viewInited = false;
  protected pendingTabs: MatTab[];

  constructor(protected store: Store<AppState>,
              protected injector: Injector,
              protected cd: ChangeDetectorRef,
              protected componentFactoryResolver: ComponentFactoryResolver) {
    super(store);
  }

  @Input()
  set entityId(entityId: string) {
    if (entityId && entityId !== this.currentEntityId) {
      this.currentEntityId = entityId;
      if (this.currentEntityId) {
        this.reloadEntity();
      }
    }
  }

  @Input()
  set entitiesTableConfig(entitiesTableConfig: EntityTableConfig<BaseData>) {
    if (this.entitiesTableConfigValue !== entitiesTableConfig) {
      this.entitiesTableConfigValue = entitiesTableConfig;
      if (this.entitiesTableConfigValue) {
        this.currentEntityId = null;
        this.isEdit = false;
        this.entity = null;
        this.init();
      }
    }
  }

  get entitiesTableConfig(): EntityTableConfig<BaseData> {
    return this.entitiesTableConfigValue;
  }

  set isEdit(val: boolean) {
    this.isEditValue = val;
    if (this.entityComponent) {
      this.entityComponent.isEdit = val;
    }
    if (this.entityTabsComponent) {
      this.entityTabsComponent.isEdit = val;
    }
  }

  get isEdit() {
    return this.isEditValue;
  }

  protected init() {
    this.translations = this.entitiesTableConfig.entityTranslations;
    this.resources = this.entitiesTableConfig.entityResources;
    this.buildEntityComponent();
  }

  private clearSubscriptions() {
    this.subscriptions.forEach((subscription) => {
      subscription.unsubscribe();
    });
    this.subscriptions.length = 0;
  }

  ngOnDestroy(): void {
    super.ngOnDestroy();
    this.clearSubscriptions();
  }

  buildEntityComponent() {
    this.clearSubscriptions();
    if (this.entityComponentRef) {
      this.entityComponentRef.destroy();
      this.entityComponentRef = null;
    }
    const componentFactory = this.componentFactoryResolver.resolveComponentFactory(this.entitiesTableConfig.entityComponent);
    const viewContainerRef = this.entityDetailsFormAnchor().viewContainerRef;
    viewContainerRef.clear();
    const injector: Injector = Injector.create(
      {
        providers: [
          {
            provide: 'entity',
            useValue: this.entity
          },
          {
            provide: 'entitiesTableConfig',
            useValue: this.entitiesTableConfig
          }
        ],
        parent: this.injector
      }
    );
    this.entityComponentRef = viewContainerRef.createComponent(componentFactory, 0, injector);
    this.entityComponent = this.entityComponentRef.instance;
    this.entityComponent.isEdit = this.isEdit;
    this.detailsForm = this.entityComponent.entityForm;
    this.subscriptions.push(this.entityComponent.entityAction.subscribe((action) => {
      this.entityAction.emit(action);
    }));
    this.buildEntityTabsComponent();
    this.subscriptions.push(this.entityComponent.entityForm.valueChanges.subscribe(() => {
      this.cd.detectChanges();
    }));
  }

  buildEntityTabsComponent() {
    if (this.entityTabsComponentRef) {
      this.entityTabsComponentRef.destroy();
      this.entityTabsComponentRef = null;
    }
    const viewContainerRef = this.entityTabsAnchor().viewContainerRef;
    viewContainerRef.clear();
    this.entityTabsComponent = null;
    if (this.entitiesTableConfig.entityTabsComponent) {
      const componentTabsFactory = this.componentFactoryResolver.resolveComponentFactory(this.entitiesTableConfig.entityTabsComponent);
      this.entityTabsComponentRef = viewContainerRef.createComponent(componentTabsFactory);
      this.entityTabsComponent = this.entityTabsComponentRef.instance;
      this.entityTabsComponent.isEdit = this.isEdit;
      this.entityTabsComponent.entitiesTableConfig = this.entitiesTableConfig;
      this.entityTabsComponent.detailsForm.set(this.detailsForm);
      this.subscriptions.push(this.entityTabsComponent.entityTabsChanged.subscribe(
        (entityTabs) => {
          if (entityTabs) {
            if (this.viewInited) {
              this.matTabGroup()._tabs.reset([...this.inclusiveTabs(), ...entityTabs]);
              this.matTabGroup()._tabs.notifyOnChanges();
            } else {
              this.pendingTabs = entityTabs;
            }
          }
        }
      ));
    }
  }

  hideDetailsTabs(): boolean {
    return this.isEditValue && this.entitiesTableConfig.hideDetailsTabsOnEdit;
  }

  reloadEntity(): Observable<BaseData> {
    const loadEntitySubject = new ReplaySubject<BaseData>();
    this.isEdit = false;
    this.entitiesTableConfig.loadEntity(this.currentEntityId).subscribe(
      (entity) => {
        this.entity = entity;
        this.entityComponent.entity = entity;
        if (this.entityTabsComponent) {
          this.entityTabsComponent.entity = entity;
        }
        loadEntitySubject.next(entity);
        loadEntitySubject.complete();
      }
    );
    return loadEntitySubject;
  }

  onCloseEntityDetails() {
    this.currentEntityId = null;
    this.closeEntityDetails.emit();
  }

  onToggleEditMode(isEdit: boolean) {
    if (!isEdit) {
      this.entityComponent.entity = this.entity;
      if (this.entityTabsComponent) {
        this.entityTabsComponent.entity = this.entity;
      }
      this.isEdit = isEdit;
    } else {
      this.isEdit = isEdit;
      this.editingEntity = deepClone(this.entity);
      this.entityComponent.entity = this.editingEntity;
      if (this.entityTabsComponent) {
        this.entityTabsComponent.entity = this.editingEntity;
      }
      if (this.entitiesTableConfig.hideDetailsTabsOnEdit) {
        this.selectedTab = 0;
      }
    }
  }

  helpLinkId(): string {
    if (this.resources.helpLinkIdForEntity && this.entityComponent.entityForm) {
      return this.resources.helpLinkIdForEntity(this.entityComponent.entityForm.getRawValue());
    } else {
      return this.resources.helpLinkId;
    }
  }

  saveEntity(emitEntityUpdated = true): Observable<BaseData> {
    const saveEntitySubject = new ReplaySubject<BaseData>();
    if (this.detailsForm.valid) {
      const editingEntity = {...this.editingEntity, ...this.entityComponent.entityFormValue()};
      if (this.editingEntity.hasOwnProperty('additionalInfo')) {
        editingEntity.additionalInfo =
          mergeDeep((this.editingEntity as any).additionalInfo, this.entityComponent.entityFormValue()?.additionalInfo);
      }
      this.entitiesTableConfig.saveEntity(editingEntity, this.editingEntity).subscribe(
        (entity) => {
          this.entity = entity;
          this.entityComponent.entity = entity;
          if (this.entityTabsComponent) {
            this.entityTabsComponent.entity = entity;
          }
          this.isEdit = false;
          if (emitEntityUpdated) {
            this.entityUpdated.emit(this.entity);
          }
          saveEntitySubject.next(entity);
          saveEntitySubject.complete();
        }
      );
    } else {
      saveEntitySubject.next(null);
      saveEntitySubject.complete();
    }
    return saveEntitySubject;
  }

  ngAfterViewInit(): void {
    this.viewInited = true;
    if (this.pendingTabs) {
      this.matTabGroup()._tabs.reset([...this.inclusiveTabs(), ...this.pendingTabs]);
      this.matTabGroup()._tabs.notifyOnChanges();
      this.pendingTabs = null;
    }
  }

}
