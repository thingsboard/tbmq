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

export interface CsvToJsonConfig {
  delim?: string;
  header?: boolean;
}

export interface CsvToJsonResult {
  headers?: string[];
  rows?: any[][];
}

export type CSVDelimiter = ',' | ';' | '|' | '\t';

export enum ImportEntityColumnType {
  name = 'NAME',
  description = 'DESCRIPTION',
  clientType = 'CLIENT_TYPE',
  clientId = 'CLIENT_ID',
  username = 'USERNAME',
  password = 'PASSWORD',
  subAuthRulePatterns = 'SUB_AUTH_RULE_PATTERNS',
  pubAuthRulePatterns = 'PUB_AUTH_RULE_PATTERNS',
  unknown = 'UNKNOWN',
}

export const importEntityColumnTypeTranslations = new Map<ImportEntityColumnType, string>(
  [
    [ImportEntityColumnType.name, 'import.column-type.name'],
    [ImportEntityColumnType.description, 'import.column-type.description'],
    [ImportEntityColumnType.clientType, 'import.column-type.client-type'],
    [ImportEntityColumnType.clientId, 'import.column-type.client-id'],
    [ImportEntityColumnType.username, 'import.column-type.username'],
    [ImportEntityColumnType.password, 'import.column-type.password'],
    [ImportEntityColumnType.subAuthRulePatterns, 'import.column-type.sub-auth-rule-patterns'],
    [ImportEntityColumnType.pubAuthRulePatterns, 'import.column-type.pub-auth-rule-patterns'],
  ]
);

export interface CsvColumnParam {
  type: ImportEntityColumnType;
  key: string;
  sampleData: any;
}

export interface ColumnMapping {
  type: ImportEntityColumnType;
  key?: string;
}

export interface BulkImportRequest {
  file: string;
  mapping: {
    columns: Array<ColumnMapping>;
    delimiter: CSVDelimiter;
    authRulesDelimiter: CSVDelimiter;
    header: boolean;
  };
}

export interface BulkImportResult {
  created: number;
  updated: number;
  errors: number;
  errorsList: Array<string>;
}

export function convertCSVToJson(csvdata: string, config: CsvToJsonConfig,
                                 onError: (messageId: string, params?: any) => void): CsvToJsonResult | number {
  config = config || {};
  const delim = config.delim || ',';
  const header = config.header || false;
  const result: CsvToJsonResult = {};
  const csvlines = csvdata.split(/[\r\n]+/);
  const csvheaders = splitCSV(csvlines[0], delim);
  if (csvheaders.length < 2) {
    onError('import.import-csv-number-columns-error');
    return -1;
  }
  const csvrows = header ? csvlines.slice(1, csvlines.length) : csvlines;
  result.headers = csvheaders;
  result.rows = [];
  for (const row of csvrows) {
    if (row.length === 0) {
      break;
    }
    const rowitems: any[] = splitCSV(row, delim);
    if (rowitems.length !== result.headers.length) {
      onError('import.import-csv-invalid-format-error', {line: (header ? result.rows.length + 2 : result.rows.length + 1)});
      return -1;
    }
    for (let i = 0; i < rowitems.length; i++) {
      rowitems[i] = convertStringToJSType(rowitems[i]);
    }
    result.rows.push(rowitems);
  }
  return result;
}

function splitCSV(str: string, sep: string): string[] {
  let foo: string[];
  let x: number;
  let tl: string;
  for (foo = str.split(sep = sep || ','), x = foo.length - 1, tl; x >= 0; x--) {
    if (foo[x].replace(/"\s+$/, '"').charAt(foo[x].length - 1) === '"') {
      if ((tl = foo[x].replace(/^\s+"/, '"')).length > 1 && tl.charAt(0) === '"') {
        foo[x] = foo[x].replace(/^\s*"|"\s*$/g, '').replace(/""/g, '"');
      } else if (x) {
        foo.splice(x - 1, 2, [foo[x - 1], foo[x]].join(sep));
      } else {
        foo = foo.shift().split(sep).concat(foo);
      }
    } else {
      foo[x].replace(/""/g, '"');
    }
  }
  return foo;
}

function isNumeric(str: any): boolean {
  str = str.replace(',', '.');
  return (str - parseFloat(str) + 1) >= 0 && Number(str).toString() === str;
}

function convertStringToJSType(str: string): any {
  if (isNumeric(str.replace(',', '.'))) {
    return parseFloat(str.replace(',', '.'));
  }
  if (str.search(/^(true|false)$/im) === 0) {
    return str.toLowerCase() === 'true';
  }
  if (str === '') {
    return null;
  }
  return str;
}
