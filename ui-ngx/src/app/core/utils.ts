///
/// Copyright © 2016-2024 The Thingsboard Authors
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

import _ from 'lodash';
import { Observable, Subject } from 'rxjs';
import { finalize, share } from 'rxjs/operators';
import { DataSizeUnitType, WebSocketTimeUnit } from '@shared/models/ws-client.model';

const varsRegex = /\${([^}]*)}/g;

export function onParentScrollOrWindowResize(el: Node): Observable<Event> {
  const scrollSubject = new Subject<Event>();
  const scrollParentNodes = scrollParents(el);
  const eventListenerObject: EventListenerObject = {
    handleEvent(evt: Event) {
      scrollSubject.next(evt);
    }
  };
  scrollParentNodes.forEach((scrollParentNode) => {
    scrollParentNode.addEventListener('scroll', eventListenerObject);
  });
  window.addEventListener('resize', eventListenerObject);
  return scrollSubject.pipe(
    finalize(() => {
      scrollParentNodes.forEach((scrollParentNode) => {
        scrollParentNode.removeEventListener('scroll', eventListenerObject);
      });
      window.removeEventListener('resize', eventListenerObject);
    }),
    share()
  );
}

export function isLocalUrl(url: string): boolean {
  const parser = document.createElement('a');
  parser.href = url;
  const host = parser.hostname;
  return host === 'localhost' || host === '127.0.0.1';
}

export function animatedScroll(element: HTMLElement, scrollTop: number, delay?: number) {
  let currentTime = 0;
  const increment = 20;
  const start = element.scrollTop;
  const to = scrollTop;
  const duration = delay ? delay : 0;
  const remaining = to - start;
  const animateScroll = () => {
    if (duration === 0) {
      element.scrollTop = to;
    } else {
      currentTime += increment;
      element.scrollTop = easeInOut(currentTime, start, remaining, duration);
      if (currentTime < duration) {
        setTimeout(animateScroll, increment);
      }
    }
  };
  animateScroll();
}

export function isUndefined(value: any): boolean {
  return typeof value === 'undefined';
}

export function isUndefinedOrNull(value: any): boolean {
  return typeof value === 'undefined' || value === null;
}

export function isDefined(value: any): boolean {
  return typeof value !== 'undefined';
}

export function isDefinedAndNotNull(value: any): boolean {
  return typeof value !== 'undefined' && value !== null;
}

export function isEmptyStr(value: any): boolean {
  return value === '';
}

export function isNotEmptyStr(value: any): boolean {
  return typeof value === 'string' && value.trim().length > 0;
}

export function isFunction(value: any): boolean {
  return typeof value === 'function';
}

export function isObject(value: any): boolean {
  return value !== null && typeof value === 'object';
}

export function isNumber(value: any): boolean {
  return typeof value === 'number';
}

export function isNumeric(value: any): boolean {
  return (value - parseFloat(value) + 1) >= 0;
}

export function isBoolean(value: any): boolean {
  return typeof value === 'boolean';
}

export function isString(value: any): boolean {
  return typeof value === 'string';
}

export function isLiteralObject(value: any) {
  return (!!value) && (value.constructor === Object);
}

export function isValidObjectString(s: string): boolean {
  try {
    JSON.parse(s);
    return true;
  } catch (error) {
    return false;
  }
}

export const formatValue = (value: any, dec?: number, units?: string, showZeroDecimals?: boolean): string | undefined => {
  if (isDefinedAndNotNull(value) && isNumeric(value) &&
    (isDefinedAndNotNull(dec) || isDefinedAndNotNull(units) || Number(value).toString() === value)) {
    let formatted: string | number = Number(value);
    if (isDefinedAndNotNull(dec)) {
      formatted = formatted.toFixed(dec);
    }
    if (!showZeroDecimals) {
      formatted = (Number(formatted));
    }
    formatted = formatted.toString();
    if (isDefinedAndNotNull(units) && units.length > 0) {
      formatted += ' ' + units;
    }
    return formatted;
  } else {
    return value !== null ? value : '';
  }
}

export const formatNumberValue = (value: any, dec?: number): number | undefined => {
  if (isDefinedAndNotNull(value) && isNumeric(value)) {
    let formatted: string | number = Number(value);
    if (isDefinedAndNotNull(dec)) {
      formatted = formatted.toFixed(dec);
    }
    return Number(formatted);
  }
}

export function objectValues(obj: any): any[] {
  return Object.keys(obj).map(e => obj[e]);
}

export function deleteNullProperties(obj: any) {
  if (isUndefined(obj) || obj == null) {
    return;
  }
  Object.keys(obj).forEach((propName) => {
    if (obj[propName] === null || isUndefined(obj[propName])) {
      delete obj[propName];
    } else if (isObject(obj[propName])) {
      deleteNullProperties(obj[propName]);
    } else if (obj[propName] instanceof Array) {
      (obj[propName] as any[]).forEach((elem) => {
        deleteNullProperties(elem);
      });
    }
  });
}

export function objToBase64(obj: any): string {
  const json = JSON.stringify(obj);
  return btoa(encodeURIComponent(json).replace(/%([0-9A-F]{2})/g,
    function toSolidBytes(match, p1) {
      return String.fromCharCode(Number('0x' + p1));
    }));
}

export function base64toString(b64Encoded: string): string {
  return decodeURIComponent(atob(b64Encoded).split('').map((c) => '%' + ('00' + c.charCodeAt(0).toString(16)).slice(-2)).join(''));
}

export function objToBase64URI(obj: any): string {
  return encodeURIComponent(objToBase64(obj));
}

export function base64toObj(b64Encoded: string): any {
  const json = decodeURIComponent(atob(b64Encoded).split('').map((c) => '%' + ('00' + c.charCodeAt(0).toString(16)).slice(-2)).join(''));
  return JSON.parse(json);
}

export function stringToBase64(value: string): string {
  return btoa(encodeURIComponent(value).replace(/%([0-9A-F]{2})/g,
    function toSolidBytes(match, p1) {
      return String.fromCharCode(Number('0x' + p1));
    }));
}

const scrollRegex = /(auto|scroll)/;

function parentNodes(node: Node, nodes: Node[]): Node[] {
  if (node.parentNode === null) {
    return nodes;
  }
  return parentNodes(node.parentNode, nodes.concat([node]));
}

function style(el: Element, prop: string): string {
  return getComputedStyle(el, null).getPropertyValue(prop);
}

function overflow(el: Element): string {
  return style(el, 'overflow') + style(el, 'overflow-y') + style(el, 'overflow-x');
}

function isScrollNode(node: Node): boolean {
  if (node instanceof Element) {
    return scrollRegex.test(overflow(node));
  } else {
    return false;
  }
}

function scrollParents(node: Node): Node[] {
  if (!(node instanceof HTMLElement || node instanceof SVGElement)) {
    return [];
  }
  const scrollParentNodes = [];
  const nodeParents = parentNodes(node, []);
  nodeParents.forEach((nodeParent) => {
    if (isScrollNode(nodeParent)) {
      scrollParentNodes.push(nodeParent);
    }
  });
  if (document.scrollingElement) {
    scrollParentNodes.push(document.scrollingElement);
  } else if (document.documentElement) {
    scrollParentNodes.push(document.documentElement);
  }
  return scrollParentNodes;
}

export function hashCode(str: string): number {
  let hash = 0;
  let i: number;
  let char: number;
  if (str.length === 0) {
    return hash;
  }
  for (i = 0; i < str.length; i++) {
    char = str.charCodeAt(i);
    // eslint-disable-next-line no-bitwise
    hash = ((hash << 5) - hash) + char;
    // eslint-disable-next-line no-bitwise
    hash = hash & hash; // Convert to 32bit integer
  }
  return hash;
}

export function objectHashCode(obj: any): number {
  let hash = 0;
  if (obj) {
    const str = JSON.stringify(obj);
    hash = hashCode(str);
  }
  return hash;
}

function easeInOut(
  currentTime: number,
  startTime: number,
  remainingTime: number,
  duration: number) {
  currentTime /= duration / 2;

  if (currentTime < 1) {
    return (remainingTime / 2) * currentTime * currentTime + startTime;
  }

  currentTime--;
  return (
    (-remainingTime / 2) * (currentTime * (currentTime - 2) - 1) + startTime
  );
}

export function deepClone<T>(target: T, ignoreFields?: string[]): T {
  if (target === null) {
    return target;
  }
  if (target instanceof Date) {
    return new Date(target.getTime()) as any;
  }
  if (target instanceof Array) {
    const cp = [] as any[];
    (target as any[]).forEach((v) => { cp.push(v); });
    return cp.map((n: any) => deepClone<any>(n)) as any;
  }
  if (typeof target === 'object') {
    const cp = {...(target as { [key: string]: any })} as { [key: string]: any };
    Object.keys(cp).forEach(k => {
      if (!ignoreFields || ignoreFields.indexOf(k) === -1) {
        cp[k] = deepClone<any>(cp[k]);
      }
    });
    return cp as T;
  }
  return target;
}

export function extractType<T extends object>(target: any, keysOfProps: (keyof T)[]): T {
  return _.pick(target, keysOfProps);
}

export const isEqual = (a: any, b: any): boolean => _.isEqual(a, b);

export const isEmpty = (a: any): boolean => _.isEmpty(a);

export const unset = (object: any, path: string | symbol): boolean => _.unset(object, path);

export const isEqualIgnoreUndefined = (a: any, b: any): boolean => {
  if (a === b) {
    return true;
  }
  if (isDefinedAndNotNull(a) && isDefinedAndNotNull(b)) {
    return isEqual(a, b);
  } else {
    return (isUndefinedOrNull(a) || !a) && (isUndefinedOrNull(b) || !b);
  }
};

export const isArraysEqualIgnoreUndefined = (a: any[], b: any[]): boolean => {
  const res = isEqualIgnoreUndefined(a, b);
  if (!res) {
    return (isUndefinedOrNull(a) || !a?.length) && (isUndefinedOrNull(b) || !b?.length);
  } else {
    return res;
  }
};

export function mergeDeep<T>(target: T, ...sources: T[]): T {
  return _.merge(target, ...sources);
}

export function guid(): string {
  function s4(): string {
    return Math.floor((1 + Math.random()) * 0x10000)
      .toString(16)
      .substring(1);
  }

  return s4() + s4() + '-' + s4() + '-' + s4() + '-' +
    s4() + '-' + s4() + s4() + s4();
}

const SNAKE_CASE_REGEXP = /[A-Z]/g;

export function snakeCase(name: string, separator: string): string {
  separator = separator || '_';
  return name.replace(SNAKE_CASE_REGEXP, (letter, pos) => (pos ? separator : '') + letter.toLowerCase());
}

export function getDescendantProp(obj: any, path: string): any {
  if (obj.hasOwnProperty(path)) {
    return obj[path];
  }
  return path.split('.').reduce((acc, part) => acc && acc[part], obj);
}

export function insertVariable(pattern: string, name: string, value: any): string {
  let result = pattern;
  let match = varsRegex.exec(pattern);
  while (match !== null) {
    const variable = match[0];
    const variableName = match[1];
    if (variableName === name) {
      result = result.replace(variable, value);
    }
    match = varsRegex.exec(pattern);
  }
  return result;
}

export const hasDatasourceLabelsVariables = (pattern: string): boolean => varsRegex.test(pattern) !== null;

export function parseFunction(source: any, params: string[] = ['def']): (...args: any[]) => any {
  let res = null;
  if (source?.length) {
    try {
      res = new Function(...params, source);
    }
    catch (err) {
      res = null;
    }
  }
  return res;
}

export function safeExecute(func: (...args: any[]) => any, params = []) {
  let res = null;
  if (func && typeof (func) === 'function') {
    try {
      res = func(...params);
    }
    catch (err) {
      console.log('error in external function:', err);
      res = null;
    }
  }
  return res;
}

export function padValue(val: any, dec: number): string {
  let strVal;
  let n;

  val = parseFloat(val);
  n = (val < 0);
  val = Math.abs(val);

  if (dec > 0) {
    strVal = val.toFixed(dec);
  } else {
    strVal = Math.round(val).toString();
  }
  strVal = (n ? '-' : '') + strVal;
  return strVal;
}

export function baseUrl(): string {
  let url = window.location.protocol + '//' + window.location.hostname;
  const port = window.location.port;
  if (port && port.length > 0 && port !== '80' && port !== '443') {
    url += ':' + port;
  }
  return url;
}

export function sortObjectKeys<T>(obj: T): T {
  return Object.keys(obj).sort().reduce((acc, key) => {
    acc[key] = obj[key];
    return acc;
  }, {} as T);
}

export function deepTrim<T>(obj: T): T {
  if (isNumber(obj) || isUndefined(obj) || isString(obj) || obj === null || obj instanceof File) {
    return obj;
  }
  return Object.keys(obj).reduce((acc, curr) => {
    if (isString(obj[curr])) {
      acc[curr] = obj[curr].trim();
    } else if (isObject(obj[curr])) {
      acc[curr] = deepTrim(obj[curr]);
    } else {
      acc[curr] = obj[curr];
    }
    return acc;
  }, (Array.isArray(obj) ? [] : {}) as T);
}

export function generateSecret(length?: number): string {
  if (isUndefined(length) || length == null) {
    length = 1;
  }
  const l = length > 10 ? 10 : length;
  const str = Math.random().toString(36).substr(2, l);
  if (str.length >= length) {
    return str;
  }
  return str.concat(generateSecret(length - str.length));
}

export function isMobileApp(): boolean {
  return isDefined((window as any).flutter_inappwebview);
}

const alphanumericCharacters = 'ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789';
const alphanumericCharactersLength = alphanumericCharacters.length;

export function randomAlphanumeric(length: number): string {
  let result = '';
  for (let i = 0; i < length; i++) {
    result += alphanumericCharacters.charAt(Math.floor(Math.random() * alphanumericCharactersLength));
  }
  return result;
}

function prepareMessageFromData(data): string {
  if (typeof data === 'object' && data.constructor === ArrayBuffer) {
    const msg = String.fromCharCode.apply(null, new Uint8Array(data));
    try {
      const msgObj = JSON.parse(msg);
      if (msgObj.message) {
        return msgObj.message;
      } else {
        return msg;
      }
    } catch (e) {
      return msg;
    }
  } else {
    return data;
  }
}

export const getOS = (): string => {
  const userAgent = window.navigator.userAgent.toLowerCase();
  const macosPlatforms = /(macintosh|macintel|macppc|mac68k|macos|mac_powerpc)/i;
  const windowsPlatforms = /(win32|win64|windows|wince)/i;
  const iosPlatforms = /(iphone|ipad|ipod|darwin|ios)/i;
  let os = null;

  if (macosPlatforms.test(userAgent)) {
    os = 'macos';
  } else if (iosPlatforms.test(userAgent)) {
    os = 'ios';
  } else if (windowsPlatforms.test(userAgent)) {
    os = 'windows';
  } else if (/android/.test(userAgent)) {
    os = 'android';
  } else if (/linux/.test(userAgent)) {
    os = 'linux';
  }

  return os;
};

export const camelCase = (str: string): string => {
  return _.camelCase(str);
};

export const convertTimeUnits = (value: number, valueUnit: WebSocketTimeUnit, targetUnit: WebSocketTimeUnit): number => {
  if (!valueUnit) return 0;
  let milliseconds: number = convertToMilliseconds(value, valueUnit);
  switch(targetUnit) {
    case WebSocketTimeUnit.MILLISECONDS:
      return milliseconds;
    case WebSocketTimeUnit.SECONDS:
      return milliseconds / 1000;
    case WebSocketTimeUnit.MINUTES:
      return milliseconds / (60 * 1000);
    case WebSocketTimeUnit.HOURS:
      return milliseconds / (60 * 60 * 1000);
    default:
      throw new Error(`Unsupported target unit: ${targetUnit}. Expected 'milliseconds', 'seconds', 'minutes', or 'hours'`);
  }
}

const convertToMilliseconds = (value: number, unit: WebSocketTimeUnit): number => {
  switch(unit) {
    case WebSocketTimeUnit.MILLISECONDS:
      return value;
    case WebSocketTimeUnit.SECONDS:
      return value * 1000;
    case WebSocketTimeUnit.MINUTES:
      return value * 60 * 1000;
    case WebSocketTimeUnit.HOURS:
      return value * 60 * 60 * 1000;
    default:
      throw new Error(`Unsupported unit: ${unit}. Expected 'milliseconds', 'seconds', 'minutes', or 'hours'`);
  }
}

export const convertDataSizeUnits = (value: number, valueUnit: DataSizeUnitType, targetUnit: DataSizeUnitType): number => {
  if (!valueUnit) return 0;
  let bytes: number = convertToBytes(value, valueUnit);
  switch(targetUnit) {
    case DataSizeUnitType.BYTE:
      return bytes;
    case DataSizeUnitType.KILOBYTE:
      return bytes / 1024;
    case DataSizeUnitType.MEGABYTE:
      return bytes / (1024 * 1024);
    default:
      throw new Error(`Unsupported unit: ${valueUnit}. Expected 'bytes', 'kilobytes', or 'megabytes'`)
  }
}

const convertToBytes = (value: number, valueUnit: DataSizeUnitType): number => {
  switch(valueUnit) {
    case DataSizeUnitType.BYTE:
      return value;
    case DataSizeUnitType.KILOBYTE:
      return value * 1024;
    case DataSizeUnitType.MEGABYTE:
      return value * 1024 * 1024;
    default:
      throw new Error(`Unsupported unit: ${valueUnit}. Expected 'bytes', 'kilobytes', or 'megabytes'`);
  }
}
