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

import { ChangeDetectionStrategy, ChangeDetectorRef, Component, OnDestroy, OnInit } from '@angular/core';
import { BehaviorSubject, Subject, Subscription } from 'rxjs';
import { BreadCrumb, BreadCrumbConfig } from './breadcrumb';
import { ActivatedRoute, ActivatedRouteSnapshot, NavigationEnd, Router, RouterLink } from '@angular/router';
import { distinctUntilChanged, filter, map } from 'rxjs/operators';
import { TranslateService, TranslateModule } from '@ngx-translate/core';
import { guid } from '@core/utils';
import { BroadcastService } from '@core/services/broadcast.service';
import { ActiveComponentService } from '@core/services/active-component.service';
import { UtilsService } from '@core/services/utils.service';
import { NgTemplateOutlet, AsyncPipe } from '@angular/common';
import { TbIconComponent } from './icon.component';

@Component({
    selector: 'tb-breadcrumb',
    templateUrl: './breadcrumb.component.html',
    styleUrls: ['./breadcrumb.component.scss'],
    changeDetection: ChangeDetectionStrategy.OnPush,
    imports: [RouterLink, NgTemplateOutlet, TbIconComponent, AsyncPipe, TranslateModule]
})
export class BreadcrumbComponent implements OnInit, OnDestroy {

  activeComponentValue: any;
  updateBreadcrumbsSubscription: Subscription = null;

  setActiveComponent(activeComponent: any) {
    if (this.updateBreadcrumbsSubscription) {
      this.updateBreadcrumbsSubscription.unsubscribe();
      this.updateBreadcrumbsSubscription = null;
    }
    this.activeComponentValue = activeComponent;
    if (this.activeComponentValue && this.activeComponentValue.updateBreadcrumbs) {
      this.updateBreadcrumbsSubscription = this.activeComponentValue.updateBreadcrumbs.subscribe(() => {
        this.breadcrumbs$.next(this.buildBreadCrumbs(this.activatedRoute.snapshot));
      });
    }
  }

  breadcrumbs$: Subject<Array<BreadCrumb>> = new BehaviorSubject<Array<BreadCrumb>>([]);

  routerEventsSubscription = this.router.events.pipe(
      filter((event) => event instanceof NavigationEnd ),
      distinctUntilChanged(),
      map( () => this.buildBreadCrumbs(this.activatedRoute.snapshot) )
  ).subscribe(breadcrumns => this.breadcrumbs$.next(breadcrumns) );

  activeComponentSubscription = this.activeComponentService.onActiveComponentChanged().subscribe(comp => this.setActiveComponent(comp));

  lastBreadcrumb$ = this.breadcrumbs$.pipe(
      map( breadcrumbs => breadcrumbs[breadcrumbs.length - 1])
  );

  constructor(private router: Router,
              private activatedRoute: ActivatedRoute,
              private broadcast: BroadcastService,
              private activeComponentService: ActiveComponentService,
              private cd: ChangeDetectorRef,
              private translate: TranslateService,
              public utils: UtilsService) {
  }

  ngOnInit(): void {
    this.broadcast.on('updateBreadcrumb', () => {
      this.cd.markForCheck();
    });
    this.setActiveComponent(this.activeComponentService.getCurrentActiveComponent());
  }

  ngOnDestroy(): void {
    if (this.routerEventsSubscription) {
      this.routerEventsSubscription.unsubscribe();
    }
    if (this.activeComponentSubscription) {
      this.activeComponentSubscription.unsubscribe();
    }
  }

  private lastChild(route: ActivatedRouteSnapshot) {
    let child = route;
    while (child.firstChild !== null) {
      child = child.firstChild;
    }
    return child;
  }

  buildBreadCrumbs(route: ActivatedRouteSnapshot, breadcrumbs: Array<BreadCrumb> = [],
                   lastChild?: ActivatedRouteSnapshot): Array<BreadCrumb> {
    if (!lastChild) {
      lastChild = this.lastChild(route);
    }
    let newBreadcrumbs = breadcrumbs;
    if (route.routeConfig && route.routeConfig.data) {
      const breadcrumbConfig = route.routeConfig.data.breadcrumb as BreadCrumbConfig<any>;
      if (breadcrumbConfig && !breadcrumbConfig.skip) {
        let label;
        let labelFunction;
        let ignoreTranslate;
        if (breadcrumbConfig.labelFunction) {
          labelFunction = () => this.activeComponentValue ?
            breadcrumbConfig.labelFunction(route, this.translate, this.activeComponentValue, lastChild.data) : breadcrumbConfig.label;
          ignoreTranslate = true;
        } else {
          label = breadcrumbConfig.label || 'home.home';
          ignoreTranslate = false;
        }
        const icon = breadcrumbConfig.icon || 'home';
        const link = [ route.pathFromRoot.map(v => v.url.map(segment => segment.toString()).join('/')).join('/') ];
        const breadcrumb = {
          id: guid(),
          label,
          labelFunction,
          ignoreTranslate,
          icon,
          link,
          queryParams: null
        };
        newBreadcrumbs = [...breadcrumbs, breadcrumb];
      }
    }
    if (route.firstChild) {
      return this.buildBreadCrumbs(route.firstChild, newBreadcrumbs, lastChild);
    }
    return newBreadcrumbs;
  }
}
