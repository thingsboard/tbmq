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
  ChangeDetectorRef,
  Component,
  ElementRef,
  EventEmitter,
  Input, NgZone,
  OnChanges,
  Output,
  Renderer2,
  SimpleChanges,
  Type,
  ViewChild,
  ViewContainerRef
} from '@angular/core';
import { MarkdownService, PrismPlugin } from 'ngx-markdown';
import { coerceBooleanProperty } from '@angular/cdk/coercion';
import { deepClone, guid, isDefinedAndNotNull } from '@core/utils';
import { Observable, of, ReplaySubject } from 'rxjs';
import { coerceBoolean } from '@shared/decorators/coercion';
import { NgClass, NgStyle } from '@angular/common';
import { ExtendedModule } from '@angular/flex-layout/extended';

let defaultMarkdownStyle;

@Component({
    selector: 'tb-markdown',
    templateUrl: './markdown.component.html',
    styleUrls: ['./markdown.component.scss'],
    imports: [ExtendedModule, NgClass, NgStyle]
})
export class TbMarkdownComponent implements OnChanges {

  @ViewChild('markdownContainer', {read: ViewContainerRef, static: true}) markdownContainer: ViewContainerRef;
  @ViewChild('fallbackElement', {static: true}) fallbackElement: ElementRef<HTMLElement>;

  @Input() data: string | undefined;

  @Input() context: any;

  @Input() additionalCompileModules: Type<any>[];

  @Input() markdownClass: string | undefined;

  @Input() containerClass: string | undefined;

  @Input() style: { [klass: string]: any } = {};

  @Input() applyDefaultMarkdownStyle = true;

  @Input() additionalStyles: string[];

  @Input()
  get lineNumbers(): boolean { return this.lineNumbersValue; }
  set lineNumbers(value: boolean) { this.lineNumbersValue = coerceBooleanProperty(value); }

  @Input()
  get fallbackToPlainMarkdown(): boolean { return this.fallbackToPlainMarkdownValue; }
  set fallbackToPlainMarkdown(value: boolean) { this.fallbackToPlainMarkdownValue = coerceBooleanProperty(value); }

  @Input()
  @coerceBoolean()
  usePlainMarkdown = false;

  @Output() ready = new EventEmitter<void>();

  private lineNumbersValue = false;
  private fallbackToPlainMarkdownValue = false;

  isMarkdownReady = false;

  error = null;

  constructor(private cd: ChangeDetectorRef,
              private zone: NgZone,
              public markdownService: MarkdownService,
              private renderer: Renderer2) {}

  ngOnChanges(changes: SimpleChanges): void {
    if (isDefinedAndNotNull(this.data)) {
      this.zone.run(() => this.render(this.data));
    }
  }

  private render(markdown: string) {
    const compiled = this.markdownService.parse(markdown, { decodeHtml: false });
    let markdownClass = 'tb-markdown-view';
    if (this.markdownClass) {
      markdownClass += ` ${this.markdownClass}`;
    }
    let template = `<div [ngStyle]="style" class="${markdownClass}">${compiled}</div>`;
    if (this.containerClass) {
      template = `<div class="${this.containerClass}" style="width: 100%; height: 100%;">${template}</div>`;
    }
    const element: HTMLDivElement = this.renderer.createElement('div');
    element.innerHTML = template;
    this.handlePlugins(element);
    this.markdownService.highlight(element);
    const preElements = element.querySelectorAll('pre');
    const matches = Array.from(template.matchAll(/<pre[\S\s]+?(?=<\/pre>)<\/pre>/g));
    for (let i = 0; i < preElements.length; i++) {
      const preHtml = preElements.item(i).outerHTML.replace('ngnonbindable=""', 'ngNonBindable');
      template = template.replace(matches[i][0], preHtml);
    }
    template = this.sanitizeCurlyBraces(template);
    this.markdownContainer.clear();
    let styles: string[] = [];
    let readyObservable: Observable<void>;
    if (this.applyDefaultMarkdownStyle) {
      if (!defaultMarkdownStyle) {
        defaultMarkdownStyle = deepClone(TbMarkdownComponent['ɵcmp'].styles)[0].replace(/\[_nghost\-%COMP%\]/g, '')
          .replace(/\[_ngcontent\-%COMP%\]/g, '');
      }
      styles.push(defaultMarkdownStyle);
    }
    if (this.additionalStyles) {
      styles = styles.concat(this.additionalStyles);
    }
    if (this.usePlainMarkdown) {
      readyObservable = this.plainMarkdown(template, styles);
      this.cd.detectChanges();
      readyObservable.subscribe(() => {
        this.ready.emit();
      });
    }
  }

  private plainMarkdown(template: string, styles?: string[]): Observable<void> {
    const element = this.fallbackElement.nativeElement;
    let styleElement;
    if (styles?.length) {
      const markdownClass = 'tb-markdown-view-' + guid();
      let innerStyle = styles.join('\n');
      innerStyle = innerStyle.replace(/\.tb-markdown-view/g, '.' + markdownClass);
      template = template.replace(/tb-markdown-view/g, markdownClass);
      styleElement = this.renderer.createElement('style');
      styleElement.innerHTML = innerStyle;
    }
    element.innerHTML = template;
    if (styleElement) {
      this.renderer.appendChild(element, styleElement);
    }
    return this.handleImages(element);
  }

  private handlePlugins(element: HTMLElement): void {
    if (this.lineNumbers) {
      this.setPluginClass(element, PrismPlugin.LineNumbers);
    }
  }

  private setPluginClass(element: HTMLElement, plugin: string | string[]): void {
    const preElements = element.querySelectorAll('pre');
    for (let i = 0; i < preElements.length; i++) {
      const classes = plugin instanceof Array ? plugin : [plugin];
      preElements.item(i).classList.add(...classes);
    }
  }

  private handleImages(element: HTMLElement): Observable<void> {
    const imgs = $('img', element);
    if (imgs.length) {
      let totalImages = imgs.length;
      const imagesLoadedSubject = new ReplaySubject<void>();
      imgs.each((index, img) => {
        $(img).one('load error', () => {
          totalImages--;
          if (totalImages === 0) {
            imagesLoadedSubject.next();
            imagesLoadedSubject.complete();
          }
        });
      });
      return imagesLoadedSubject.asObservable();
    } else {
      return of(null);
    }
  }

  private sanitizeCurlyBraces(template: string): string {
    return template.replace(/{/g, '&#123;').replace(/}/g, '&#125;');
  }

}
