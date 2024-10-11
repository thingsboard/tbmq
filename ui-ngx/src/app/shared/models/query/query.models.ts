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

export enum FilterPredicateType {
  STRING = 'STRING',
  NUMERIC = 'NUMERIC',
  BOOLEAN = 'BOOLEAN',
  COMPLEX = 'COMPLEX'
}

export enum StringOperation {
  EQUAL = 'EQUAL',
  NOT_EQUAL = 'NOT_EQUAL',
  STARTS_WITH = 'STARTS_WITH',
  ENDS_WITH = 'ENDS_WITH',
  CONTAINS = 'CONTAINS',
  NOT_CONTAINS = 'NOT_CONTAINS',
  IN = 'IN',
  NOT_IN = 'NOT_IN'
}

export const stringOperationTranslationMap = new Map<StringOperation, string>(
  [
    [StringOperation.EQUAL, 'filter.operation.equal'],
    [StringOperation.NOT_EQUAL, 'filter.operation.not-equal'],
    [StringOperation.STARTS_WITH, 'filter.operation.starts-with'],
    [StringOperation.ENDS_WITH, 'filter.operation.ends-with'],
    [StringOperation.CONTAINS, 'filter.operation.contains'],
    [StringOperation.NOT_CONTAINS, 'filter.operation.not-contains'],
    [StringOperation.IN, 'filter.operation.in'],
    [StringOperation.NOT_IN, 'filter.operation.not-in']
  ]
);

export enum NumericOperation {
  EQUAL = 'EQUAL',
  NOT_EQUAL = 'NOT_EQUAL',
  GREATER = 'GREATER',
  LESS = 'LESS',
  GREATER_OR_EQUAL = 'GREATER_OR_EQUAL',
  LESS_OR_EQUAL = 'LESS_OR_EQUAL'
}

export const numericOperationTranslationMap = new Map<NumericOperation, string>(
  [
    [NumericOperation.EQUAL, 'filter.operation.equal'],
    [NumericOperation.NOT_EQUAL, 'filter.operation.not-equal'],
    [NumericOperation.GREATER, 'filter.operation.greater'],
    [NumericOperation.LESS, 'filter.operation.less'],
    [NumericOperation.GREATER_OR_EQUAL, 'filter.operation.greater-or-equal'],
    [NumericOperation.LESS_OR_EQUAL, 'filter.operation.less-or-equal']
  ]
);

export enum BooleanOperation {
  EQUAL = 'EQUAL',
  NOT_EQUAL = 'NOT_EQUAL'
}

export const booleanOperationTranslationMap = new Map<BooleanOperation, string>(
  [
    [BooleanOperation.EQUAL, 'filter.operation.equal'],
    [BooleanOperation.NOT_EQUAL, 'filter.operation.not-equal']
  ]
);
