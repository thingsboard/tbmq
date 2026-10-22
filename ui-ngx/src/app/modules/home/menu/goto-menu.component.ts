///
/// Copyright © 2016-2026 The Thingsboard Authors
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
  booleanAttribute,
  Component,
  ElementRef,
  input,
  OnDestroy,
  OnInit,
  output,
  viewChild,
  ViewEncapsulation
} from '@angular/core';
import { Observable, ReplaySubject, Subject, Subscription } from 'rxjs';
import { MenuSection } from '@core/services/menu.models';
import { MenuService } from '@core/services/menu.service';
import { TranslateModule, TranslateService } from '@ngx-translate/core';
import { distinctUntilChanged, map, share, switchMap } from 'rxjs/operators';
import { Router } from '@angular/router';
import { MatToolbar } from '@angular/material/toolbar';
import { MatButton, MatIconButton } from '@angular/material/button';
import { MatTooltip } from '@angular/material/tooltip';
import { MatFormField, MatPrefix, MatSuffix } from '@angular/material/form-field';
import { MatInput } from '@angular/material/input';
import { MatAutocomplete, MatAutocompleteTrigger, MatOption } from '@angular/material/autocomplete';
import { MatDivider } from '@angular/material/divider';
import { FormsModule } from '@angular/forms';
import { NgTemplateOutlet } from '@angular/common';
import { TbIconComponent } from '@shared/components/icon.component';
import { HighlightPipe } from '@shared/pipe/highlight.pipe';

interface GotoMenuLink {
  name: string;
  icon: string;
  breadcrumb: string;
  path: string;
}

const CRUMB_CAP = 8;

interface GotoMenuResult {
  hasMatches: boolean;
  nameMatches: GotoMenuLink[];
  crumbMatches: GotoMenuLink[];
  crumbMore: number;
}

@Component({
  selector: 'tb-goto-menu',
  templateUrl: './goto-menu.component.html',
  styleUrls: ['./goto-menu.component.scss'],
  encapsulation: ViewEncapsulation.None,
  imports: [MatToolbar, MatButton, MatIconButton, MatTooltip, MatFormField, MatPrefix, MatSuffix, MatInput, MatAutocomplete,
    MatAutocompleteTrigger, MatOption, MatDivider, FormsModule, NgTemplateOutlet, TranslateModule, TbIconComponent, HighlightPipe]
})
export class GotoMenuComponent implements OnInit, OnDestroy {

  readonly searchLinkInput = viewChild<ElementRef>('searchLinkInput');

  readonly collapsed = input(false, { transform: booleanAttribute });

  readonly searchBtnClicked = output<void>();

  readonly linkClicked = output<void>();

  gotoMenuResult: GotoMenuResult;

  private allLinksObservable$: Observable<Array<GotoMenuLink>> = null;

  private searchSubject = new Subject<string>();
  private searchSubscription: Subscription;

  searchText = '';

  constructor(private menuService: MenuService,
              private translate: TranslateService,
              private router: Router) {
  }

  ngOnInit(): void {
    this.searchSubscription = this.searchSubject.pipe(
      distinctUntilChanged(),
      switchMap(text => this.fetchLinks(text))
    ).subscribe(result => {
      this.gotoMenuResult = result;
    });
  }

  ngOnDestroy(): void {
    this.searchSubscription?.unsubscribe();
  }

  onSearchInput(): void {
    this.searchSubject.next(this.searchText || '');
  }

  clear() {
    this.searchText = '';
    this.searchSubject.next('');
  }

  searchBtnClick() {
    this.searchBtnClicked.emit();
    setTimeout(() => {
      const searchLinkInput = this.searchLinkInput()?.nativeElement;
      searchLinkInput?.blur();
      searchLinkInput?.focus();
    });
  }

  searchDisplayFn = (value: any): string => {
    return typeof value === 'string' ? value : this.searchText || '';
  };

  gotoLink(link: GotoMenuLink): void {
    this.searchText = '';
    this.searchSubject.next('');
    this.linkClicked.emit();
    this.router.navigate([link.path]).then();
  }

  private fetchLinks(searchText?: string): Observable<GotoMenuResult> {
    this.searchText = searchText;
    return this.allLinks().pipe(
      map((links) => this.filterLinks(links))
    );
  }

  private allLinks(): Observable<Array<GotoMenuLink>> {
    if (this.allLinksObservable$ === null) {
      this.allLinksObservable$ = this.menuService.menuSections().pipe(
        map((links) => this.toGotoMenus(links)),
        share({
          connector: () => new ReplaySubject(1),
          resetOnError: false,
          resetOnComplete: false,
          resetOnRefCountZero: false
        })
      );
    }
    return this.allLinksObservable$;
  }

  private filterLinks(links: GotoMenuLink[]): GotoMenuResult {
    const query = this.searchText.trim().toLowerCase();
    const nameMatches: GotoMenuLink[] = [];
    let crumbMatches: GotoMenuLink[] = [];
    for (const link of links) {
      if (link.name.toLowerCase().includes(query)) {
        nameMatches.push(link);
      } else if (link.breadcrumb.toLowerCase().includes(query)) {
        crumbMatches.push(link);
      }
    }
    let crumbMore = 0;
    if (crumbMatches.length > CRUMB_CAP) {
      crumbMore = crumbMatches.length - CRUMB_CAP;
      crumbMatches = crumbMatches.slice(0, CRUMB_CAP);
    }
    return {
      hasMatches: !!nameMatches.length || !!crumbMatches.length,
      nameMatches,
      crumbMatches,
      crumbMore
    };
  }

  private toGotoMenus(sections: MenuSection[], result: GotoMenuLink[] = [], parentNames: string[] = []): GotoMenuLink[] {
    for (const section of sections) {
      const name = this.translate.instant(section.name);
      if (section.type === 'link') {
        result.push({
          name,
          icon: section.icon,
          path: section.path,
          breadcrumb: parentNames.join(' • ')
        });
      }
      if (section.pages?.length) {
        this.toGotoMenus(section.pages, result, [...parentNames, name]);
      }
    }
    return result;
  }
}
