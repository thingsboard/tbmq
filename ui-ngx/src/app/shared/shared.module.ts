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

import { NgModule, SecurityContext } from '@angular/core';
import { CommonModule, DatePipe } from '@angular/common';
import { FooterComponent } from '@shared/components/footer.component';
import { LogoComponent } from '@shared/components/logo.component';
import { TbSnackBarComponent, ToastDirective } from '@shared/components/toast.directive';
import { BreadcrumbComponent } from '@shared/components/breadcrumb.component';

import { MatAutocompleteModule } from '@angular/material/autocomplete';
import { MatButtonModule } from '@angular/material/button';
import { MatCardModule } from '@angular/material/card';
import { MatCheckboxModule } from '@angular/material/checkbox';
import { MatChipsModule } from '@angular/material/chips';
import { MatDialogModule } from '@angular/material/dialog';
import { MatDividerModule } from '@angular/material/divider';
import { MatExpansionModule } from '@angular/material/expansion';
import { MatGridListModule } from '@angular/material/grid-list';
import { MatIconModule } from '@angular/material/icon';
import { MatInputModule } from '@angular/material/input';
import { MatMenuModule } from '@angular/material/menu';
import { MatPaginatorModule } from '@angular/material/paginator';
import { MatProgressBarModule } from '@angular/material/progress-bar';
import { MatProgressSpinnerModule } from '@angular/material/progress-spinner';
import { MatRadioModule } from '@angular/material/radio';
import { MatSelectModule } from '@angular/material/select';
import { MatSidenavModule } from '@angular/material/sidenav';
import { MatSlideToggleModule } from '@angular/material/slide-toggle';
import { MatSliderModule } from '@angular/material/slider';
import { MatSnackBarModule } from '@angular/material/snack-bar';
import { MatSortModule } from '@angular/material/sort';
import { MatStepperModule } from '@angular/material/stepper';
import { MatTableModule } from '@angular/material/table';
import { MatTabsModule } from '@angular/material/tabs';
import { MatToolbarModule } from '@angular/material/toolbar';
import { MatTooltipModule } from '@angular/material/tooltip';
import { MatListModule } from '@angular/material/list';
import { FlexLayoutModule } from '@angular/flex-layout';
import { FormsModule, ReactiveFormsModule } from '@angular/forms';
import { RouterModule } from '@angular/router';
import { NgxHmCarouselModule } from 'ngx-hm-carousel';
import { UserMenuComponent } from '@shared/components/user-menu.component';
import { NospacePipe } from '@shared/pipe/nospace.pipe';
import { TranslateModule } from '@ngx-translate/core';
import { HelpComponent } from '@shared/components/help.component';
import { TbAnchorComponent } from '@shared/components/tb-anchor.component';
import { MillisecondsToTimeStringPipe } from '@shared/pipe/milliseconds-to-time-string.pipe';
import { OverlayModule } from '@angular/cdk/overlay';
import { ClipboardModule } from 'ngx-clipboard';
import { MarkdownModule, MarkedOptions } from 'ngx-markdown';
import { FullscreenDirective } from '@shared/components/fullscreen.directive';
import { HighlightPipe } from '@shared/pipe/highlight.pipe';
import { FooterFabButtonsComponent } from '@shared/components/footer-fab-buttons.component';
import { TbErrorComponent } from '@shared/components/tb-error.component';
import { MatChipDraggableDirective } from '@shared/components/mat-chip-draggable.directive';
import { ConfirmDialogComponent } from '@shared/components/dialog/confirm-dialog.component';
import { AlertDialogComponent } from '@shared/components/dialog/alert-dialog.component';
import { DndModule } from 'ngx-drag-drop';
import { MAT_DATE_LOCALE } from '@angular/material/core';
import { CopyButtonComponent } from '@shared/components/button/copy-button.component';
import { TogglePasswordComponent } from '@shared/components/button/toggle-password.component';
import { TbCheckboxComponent } from '@shared/components/tb-checkbox.component';
import { TimewindowComponent } from '@shared/components/time/timewindow.component';
import { TimewindowPanelComponent } from '@shared/components/time/timewindow-panel.component';
import { TimeintervalComponent } from '@shared/components/time/timeinterval.component';
import { DatetimePeriodComponent } from '@shared/components/time/datetime-period.component';
import { DatetimeComponent } from '@shared/components/time/datetime.component';
import { TimezoneSelectComponent } from '@shared/components/time/timezone-select.component';
import { QuickTimeIntervalComponent } from '@shared/components/time/quick-time-interval.component';
import { MatDatepickerModule } from '@angular/material/datepicker';
import { MatDatetimepickerModule, MatNativeDatetimeModule } from '@mat-datetimepicker/core';
import { BooleanPipe } from '@shared/pipe/boolean.pipe';
import { markedOptionsFactory } from '@shared/components/markdown.factory';

@NgModule({
  providers: [
    DatePipe,
    MillisecondsToTimeStringPipe,
    {
      provide: MAT_DATE_LOCALE,
      useValue: 'en-GB'
    }
  ],
  declarations: [
    FooterComponent,
    LogoComponent,
    FooterFabButtonsComponent,
    ToastDirective,
    FullscreenDirective,
    MatChipDraggableDirective,
    TbAnchorComponent,
    HelpComponent,
    TbCheckboxComponent,
    TbSnackBarComponent,
    TbErrorComponent,
    BreadcrumbComponent,
    UserMenuComponent,
    ConfirmDialogComponent,
    AlertDialogComponent,
    NospacePipe,
    MillisecondsToTimeStringPipe,
    HighlightPipe,
    BooleanPipe,
    CopyButtonComponent,
    TogglePasswordComponent,
    TimewindowComponent,
    TimewindowPanelComponent,
    TimeintervalComponent,
    TimezoneSelectComponent,
    DatetimePeriodComponent,
    DatetimeComponent,
    QuickTimeIntervalComponent
  ],
  imports: [
    CommonModule,
    RouterModule,
    TranslateModule,
    MatButtonModule,
    MatCheckboxModule,
    MatIconModule,
    MatCardModule,
    MatProgressBarModule,
    MatInputModule,
    MatSnackBarModule,
    MatSidenavModule,
    MatToolbarModule,
    MatMenuModule,
    MatGridListModule,
    MatDialogModule,
    MatSelectModule,
    MatTooltipModule,
    MatTableModule,
    MatPaginatorModule,
    MatSortModule,
    MatProgressSpinnerModule,
    MatDividerModule,
    MatTabsModule,
    MatRadioModule,
    MatSlideToggleModule,
    MatSliderModule,
    MatExpansionModule,
    MatStepperModule,
    MatAutocompleteModule,
    MatChipsModule,
    MatListModule,
    MatDatepickerModule,
    MatNativeDatetimeModule,
    MatDatetimepickerModule,
    ClipboardModule,
    FlexLayoutModule.withConfig({addFlexToParent: false}),
    FormsModule,
    ReactiveFormsModule,
    OverlayModule,
    NgxHmCarouselModule,
    DndModule,
    // ngx-markdown
    MarkdownModule.forRoot({
      sanitize: SecurityContext.NONE,
      markedOptions: {
        provide: MarkedOptions,
        useFactory: markedOptionsFactory
      }
    })
  ],
  exports: [
    FooterComponent,
    LogoComponent,
    FooterFabButtonsComponent,
    ToastDirective,
    FullscreenDirective,
    MatChipDraggableDirective,
    TbAnchorComponent,
    HelpComponent,
    TbCheckboxComponent,
    TbErrorComponent,
    BreadcrumbComponent,
    UserMenuComponent,
    MatButtonModule,
    MatCheckboxModule,
    MatIconModule,
    MatCardModule,
    MatProgressBarModule,
    MatInputModule,
    MatSnackBarModule,
    MatSidenavModule,
    MatToolbarModule,
    MatMenuModule,
    MatGridListModule,
    MatDialogModule,
    MatSelectModule,
    MatTooltipModule,
    MatTableModule,
    MatPaginatorModule,
    MatSortModule,
    MatProgressSpinnerModule,
    MatDividerModule,
    MatTabsModule,
    MatRadioModule,
    MatSlideToggleModule,
    MatSliderModule,
    MatExpansionModule,
    MatStepperModule,
    MatAutocompleteModule,
    MatChipsModule,
    MatListModule,
    MatDatepickerModule,
    MatNativeDatetimeModule,
    MatDatetimepickerModule,
    ClipboardModule,
    FlexLayoutModule,
    FormsModule,
    ReactiveFormsModule,
    OverlayModule,
    NgxHmCarouselModule,
    DndModule,
    MarkdownModule,
    ConfirmDialogComponent,
    AlertDialogComponent,
    NospacePipe,
    MillisecondsToTimeStringPipe,
    HighlightPipe,
    TranslateModule,
    CopyButtonComponent,
    TogglePasswordComponent,
    TimewindowComponent,
    TimewindowPanelComponent,
    TimeintervalComponent,
    TimezoneSelectComponent,
    DatetimePeriodComponent,
    DatetimeComponent,
    QuickTimeIntervalComponent,
    BooleanPipe
  ]
})
export class SharedModule { }
