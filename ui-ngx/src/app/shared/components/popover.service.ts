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
  ComponentFactory,
  ComponentFactoryResolver,
  ComponentRef,
  ElementRef,
  Injectable,
  Injector,
  Renderer2,
  Type,
  ViewContainerRef
} from '@angular/core';
import { PopoverPlacement, PopoverWithTrigger } from '@shared/components/popover.models';
import { TbPopoverComponent } from '@shared/components/popover.component';
import { CdkOverlayOrigin } from '@angular/cdk/overlay';

@Injectable()
export class TbPopoverService {

  private popoverWithTriggers: PopoverWithTrigger[] = [];

  componentFactory: ComponentFactory<TbPopoverComponent> = this.resolver.resolveComponentFactory(TbPopoverComponent);

  constructor(private resolver: ComponentFactoryResolver) {
  }

  hasPopover(trigger: CdkOverlayOrigin): boolean {
    const res = this.findPopoverByTrigger(trigger);
    return res !== null;
  }

  hidePopover(trigger: CdkOverlayOrigin): boolean {
    const component: TbPopoverComponent = this.findPopoverByTrigger(trigger);
    if (component && component.tbVisible) {
      component.hide();
      return true;
    } else {
      return false;
    }
  }

  createPopoverRef(hostView: ViewContainerRef): ComponentRef<TbPopoverComponent> {
    return hostView.createComponent(this.componentFactory);
  }

  displayPopover<T>(trigger: CdkOverlayOrigin, renderer: Renderer2, hostView: ViewContainerRef,
                    componentType: Type<T>, preferredPlacement: PopoverPlacement = 'top', hideOnClickOutside = true,
                    injector?: Injector, context?: any, overlayStyle: any = {}, popoverStyle: any = {}, style?: any,
                    showCloseButton = true): TbPopoverComponent<T> {
    const componentRef = this.createPopoverRef(hostView);
    return this.displayPopoverWithComponentRef(componentRef, trigger, renderer, componentType, preferredPlacement, hideOnClickOutside,
      injector, context, overlayStyle, popoverStyle, style, showCloseButton);
  }

  displayPopoverWithComponentRef<T>(componentRef: ComponentRef<TbPopoverComponent>, trigger: CdkOverlayOrigin, renderer: Renderer2,
                                    componentType: Type<T>, preferredPlacement: PopoverPlacement = 'top',
                                    hideOnClickOutside = true, injector?: Injector, context?: any, overlayStyle: any = {},
                                    popoverStyle: any = {}, style?: any, showCloseButton = true): TbPopoverComponent<T> {
    const component = componentRef.instance;
    this.popoverWithTriggers.push({
      trigger,
      popoverComponent: component
    });
    renderer.removeChild(
      renderer.parentNode(trigger),
      componentRef.location.nativeElement
    );
    component.setOverlayOrigin(trigger);
    component.tbPlacement = preferredPlacement;
    component.tbComponentFactory = this.resolver.resolveComponentFactory(componentType);
    component.tbComponentInjector = injector;
    component.tbComponentContext = context;
    component.tbOverlayStyle = overlayStyle;
    component.tbPopoverInnerStyle = popoverStyle;
    component.tbComponentStyle = style;
    component.tbHideOnClickOutside = hideOnClickOutside;
    component.tbShowCloseButton = showCloseButton;
    component.tbVisibleChange.subscribe((visible: boolean) => {
      if (!visible) {
        componentRef.destroy();
      }
    });
    component.tbDestroy.subscribe(() => {
      this.removePopoverByComponent(component);
    });
    component.show();
    return component;
  }

  private findPopoverByTrigger(trigger: CdkOverlayOrigin): TbPopoverComponent | null {
    const res = this.popoverWithTriggers.find(val => this.elementsAreEqualOrDescendant(trigger, val.trigger));
    if (res) {
      return res.popoverComponent;
    } else {
      return null;
    }
  }

  private removePopoverByComponent(component: TbPopoverComponent): void {
    const index = this.popoverWithTriggers.findIndex(val => val.popoverComponent === component);
    if (index > -1) {
      this.popoverWithTriggers.splice(index, 1);
    }
  }

  private elementsAreEqualOrDescendant(element1: CdkOverlayOrigin, element2: CdkOverlayOrigin): boolean {
    return element1 === element2 || element1.elementRef.nativeElement.contains(element2) || element2.elementRef.nativeElement.contains(element1);
  }
}
