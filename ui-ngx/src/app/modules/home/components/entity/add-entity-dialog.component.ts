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

import {
  AfterViewInit,
  ChangeDetectorRef,
  Component,
  ElementRef,
  HostBinding,
  Inject,
  Injector,
  OnInit,
  ViewChild,
  ViewContainerRef
} from '@angular/core';
import { ErrorStateMatcher } from '@angular/material/core';
import { MAT_DIALOG_DATA, MatDialogRef } from '@angular/material/dialog';
import { Store } from '@ngrx/store';
import { AppState } from '@core/core.state';
import { UntypedFormGroup } from '@angular/forms';
import { EntityType, EntityTypeResource, EntityTypeTranslation } from '@shared/models/entity-type.models';
import { BaseData } from '@shared/models/base-data';
import { TbAnchorComponent } from '@shared/components/tb-anchor.component';
import { EntityComponent } from './entity.component';
import { EntityTableConfig } from '@home/models/entity/entities-table-config.models';
import { AddEntityDialogData } from '@home/models/entity/entity-component.models';
import { DialogComponent } from '@shared/components/dialog.component';
import { Router } from '@angular/router';
import { ActionNotificationShow } from '@core/notification/notification.actions';
import { TranslateService } from '@ngx-translate/core';
import { MatStepper } from '@angular/material/stepper';
import { BreakpointObserver, BreakpointState } from '@angular/cdk/layout';
import { MediaBreakpoints } from '@app/shared/models/constants';
import { Subscription } from 'rxjs/internal/Subscription';
import { StepperSelectionEvent } from '@angular/cdk/stepper';

@Component({
  selector: 'tb-add-entity-dialog',
  templateUrl: './add-entity-dialog.component.html',
  providers: [{provide: ErrorStateMatcher, useExisting: AddEntityDialogComponent}],
  styleUrls: ['./add-entity-dialog.component.scss']
})
export class AddEntityDialogComponent extends DialogComponent<AddEntityDialogComponent, BaseData>
                                      implements OnInit, AfterViewInit {

  @ViewChild('addGroupEntityWizardStepper') addGroupEntityWizardStepper: MatStepper;
  @ViewChild('detailsFormStep', {read: ViewContainerRef}) detailsFormStepContainerRef: ViewContainerRef;
  @ViewChild('entityDetailsForm') entityDetailsFormAnchor: TbAnchorComponent;
  @ViewChild('ownersAndGroupPanel') ownersAndGroup: ElementRef;

  @HostBinding('style')
  style = this.data.entitiesTableConfig.addDialogStyle;

  selectedIndex = 0;

  showNext = true;

  labelPosition: 'bottom' | 'end' = 'end';

  entityComponent: EntityComponent<BaseData>;
  detailsForm: UntypedFormGroup;
  entitiesTableConfig: EntityTableConfig<BaseData>;
  translations: EntityTypeTranslation;
  resources: EntityTypeResource<BaseData>;
  entity: BaseData;
  submitted = false;

  entityType: EntityType;
  addDialogOwnerAndGroupWizard = false;

  private subscriptions: Subscription[] = [];

  constructor(protected store: Store<AppState>,
              protected router: Router,
              @Inject(MAT_DIALOG_DATA) public data: AddEntityDialogData<BaseData>,
              public dialogRef: MatDialogRef<AddEntityDialogComponent, BaseData>,
              private cd: ChangeDetectorRef,
              private translate: TranslateService,
              private breakpointObserver: BreakpointObserver) {
    super(store, router, dialogRef);
  }

  ngOnInit(): void {
    this.entitiesTableConfig = this.data.entitiesTableConfig;
    this.entityType = this.entitiesTableConfig.entityType;
    this.translations = this.entitiesTableConfig.entityTranslations;
    this.resources = this.entitiesTableConfig.entityResources;
    // this.addDialogOwnerAndGroupWizard = this.entitiesTableConfig.addDialogOwnerAndGroupWizard;
    this.entity = {};

    this.labelPosition = this.breakpointObserver.isMatched(MediaBreakpoints['gt-sm']) ? 'end' : 'bottom';

    this.subscriptions.push(this.breakpointObserver
      .observe(MediaBreakpoints['gt-sm'])
      .subscribe((state: BreakpointState) => {
          if (state.matches) {
            this.labelPosition = 'end';
          } else {
            this.labelPosition = 'bottom';
          }
        }
      ));
  }

  ngAfterViewInit() {
    const viewContainerRef = this.entityDetailsFormAnchor.viewContainerRef;
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
        parent: this.addDialogOwnerAndGroupWizard ? this.detailsFormStepContainerRef.injector : null
      }
    );
    const componentRef = viewContainerRef.createComponent(
      this.entitiesTableConfig.entityComponent,
      {index: 0, injector});
    this.entityComponent = componentRef.instance;
    this.entityComponent.isEdit = true;
    this.detailsForm = this.entityComponent.entityForm;
    this.cd.detectChanges();
  }

  ngOnDestroy() {
    super.ngOnDestroy();
    this.subscriptions.forEach(s => s.unsubscribe());
  }

  helpLinkId(): string {
    if (this.resources.helpLinkIdForEntity && this.entityComponent.entityForm) {
      return this.resources.helpLinkIdForEntity(this.entityComponent.entityForm.getRawValue());
    } else {
      return this.resources.helpLinkId;
    }
  }

  cancel(): void {
    this.dialogRef.close(null);
  }

  add(): void {
    if (this.allValid()) {
      this.entity = {...this.entity, ...this.entityComponent.entityFormValue()};
      this.entitiesTableConfig.saveEntity(this.entity).subscribe(
        (entity) => {
          this.dialogRef.close(entity);
          if (this.entitiesTableConfig.entityType === EntityType.USER) {
            this.showDefaultPassWarning();
          }
        }
      );
    }
  }

  private showDefaultPassWarning() {
    this.store.dispatch(new ActionNotificationShow(
      {
        message: this.translate.instant('profile.default-password-warn'),
        type: 'success',
        duration: 4000,
        verticalPosition: 'top',
        horizontalPosition: 'left'
      })
    );
  }

  previousStep(): void {
    this.addGroupEntityWizardStepper.previous();
  }

  nextStep(): void {
    this.addGroupEntityWizardStepper.next();
  }

  getFormLabel(index: number): string {
    switch (index) {
      case 0:
        return this.translations.details;
      case 1:
        return 'entity-group.owner-and-groups';
    }
  }

  get maxStepperIndex(): number {
    return this.addGroupEntityWizardStepper?._steps?.length - 1;
  }

  allValid(): boolean {
    if (!this.addDialogOwnerAndGroupWizard) {
      this.detailsForm.markAllAsTouched();
      return this.detailsForm.valid;
    }
    return !this.addGroupEntityWizardStepper.steps.find((item, index) => {
      if (item.stepControl.invalid) {
        item.interacted = true;
        this.addGroupEntityWizardStepper.selectedIndex = index;
        return true;
      } else {
        return false;
      }
    });
  }

  changeStep($event: StepperSelectionEvent): void {
    this.selectedIndex = $event.selectedIndex;
    if (this.selectedIndex === this.maxStepperIndex) {
      this.showNext = false;
    } else {
      this.showNext = true;
    }
  }
}
