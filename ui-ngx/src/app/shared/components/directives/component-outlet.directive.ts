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
  ComponentFactory, ComponentRef,
  Directive, Injector,
  OnChanges, Renderer2,
  SimpleChange,
  SimpleChanges,
  ViewContainerRef,
  input,
  output
} from '@angular/core';

@Directive({
    // eslint-disable-next-line @angular-eslint/directive-selector
    selector: '[tbComponentOutlet]',
    exportAs: 'tbComponentOutlet',
})
export class TbComponentOutletDirective<_T = unknown> implements OnChanges {
  private componentRef: ComponentRef<any> | null = null;
  private context = new TbComponentOutletContext();
  readonly tbComponentOutletContext = input<any | null>(null);
  readonly tbComponentStyle = input<{
    [klass: string]: any;
} | null>(null);
  readonly tbComponentInjector = input<Injector | null>(null);
  readonly tbComponentOutlet = input<ComponentFactory<any>>(null);
  readonly componentChange = output<ComponentRef<any>>();

  static ngTemplateContextGuard<T>(
    // eslint-disable-next-line @typescript-eslint/naming-convention,no-underscore-dangle,id-blacklist,id-match
    _dir: TbComponentOutletDirective<T>,
    // eslint-disable-next-line @typescript-eslint/naming-convention, no-underscore-dangle, id-blacklist, id-match
    _ctx: any
  ): _ctx is TbComponentOutletContext {
    return true;
  }

  private recreateComponent(): void {
    this.viewContainer.clear();
    this.componentRef = this.viewContainer.createComponent(this.tbComponentOutlet(), 0, this.tbComponentInjector());
    this.componentChange.emit(this.componentRef);
    const tbComponentOutletContext = this.tbComponentOutletContext();
    if (tbComponentOutletContext) {
      for (const propName of Object.keys(tbComponentOutletContext)) {
        this.componentRef.instance[propName] = tbComponentOutletContext[propName];
      }
    }
    const tbComponentStyle = this.tbComponentStyle();
    if (tbComponentStyle) {
      for (const propName of Object.keys(tbComponentStyle)) {
        this.renderer.setStyle(this.componentRef.location.nativeElement, propName, tbComponentStyle[propName]);
      }
    }
  }

  private updateContext(): void {
    const newCtx = this.tbComponentOutletContext();
    const oldCtx = this.componentRef.instance as any;
    if (newCtx) {
      for (const propName of Object.keys(newCtx)) {
        oldCtx[propName] = newCtx[propName];
      }
    }
  }

  constructor(private viewContainer: ViewContainerRef,
              private renderer: Renderer2) {}

  ngOnChanges(changes: SimpleChanges): void {
    const { tbComponentOutletContext, tbComponentOutlet } = changes;
    const shouldRecreateComponent = (): boolean => {
      let shouldOutletRecreate = false;
      if (tbComponentOutlet) {
        if (tbComponentOutlet.firstChange) {
          shouldOutletRecreate = true;
        } else {
          const isPreviousOutletTemplate = tbComponentOutlet.previousValue instanceof ComponentFactory;
          const isCurrentOutletTemplate = tbComponentOutlet.currentValue instanceof ComponentFactory;
          shouldOutletRecreate = isPreviousOutletTemplate || isCurrentOutletTemplate;
        }
      }
      const hasContextShapeChanged = (ctxChange: SimpleChange): boolean => {
        const prevCtxKeys = Object.keys(ctxChange.previousValue || {});
        const currCtxKeys = Object.keys(ctxChange.currentValue || {});
        if (prevCtxKeys.length === currCtxKeys.length) {
          for (const propName of currCtxKeys) {
            if (prevCtxKeys.indexOf(propName) === -1) {
              return true;
            }
          }
          return false;
        } else {
          return true;
        }
      };
      const shouldContextRecreate =
        tbComponentOutletContext && hasContextShapeChanged(tbComponentOutletContext);
      return shouldContextRecreate || shouldOutletRecreate;
    };

    if (tbComponentOutlet) {
      this.context.$implicit = tbComponentOutlet.currentValue;
    }

    const recreateComponent = shouldRecreateComponent();
    if (recreateComponent) {
      this.recreateComponent();
    } else {
      this.updateContext();
    }
  }
}

export class TbComponentOutletContext {
  public $implicit: any;
}
