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

import { fileURLToPath } from 'node:url';
import { defineConfig } from 'vitest/config';

const fromRoot = (path: string): string => fileURLToPath(new URL(path, import.meta.url));

// Mirrors compilerOptions.paths of tsconfig.json (the Angular compiler is not involved in Vitest).
export default defineConfig({
  resolve: {
    alias: [
      { find: /^@app\//, replacement: fromRoot('./src/app/') },
      { find: /^@env\//, replacement: fromRoot('./src/environments/') },
      { find: /^@core\//, replacement: fromRoot('./src/app/core/') },
      { find: /^@modules\//, replacement: fromRoot('./src/app/modules/') },
      { find: /^@shared\//, replacement: fromRoot('./src/app/shared/') },
      { find: /^@home\//, replacement: fromRoot('./src/app/modules/home/') },
      { find: /^jszip$/, replacement: fromRoot('./node_modules/jszip/dist/jszip.min.js') },
      { find: /^ace$/, replacement: fromRoot('./node_modules/ace-builds/src-noconflict/ace.js') },
      { find: /^jquery$/, replacement: fromRoot('./node_modules/jquery/dist/jquery.min.js') },
      { find: /^jquery\.terminal$/, replacement: fromRoot('./node_modules/jquery.terminal/js/jquery.terminal.js') },
      { find: /^tooltipster$/, replacement: fromRoot('./node_modules/tooltipster/dist/js/tooltipster.bundle.min.js') },
      { find: /^jstree$/, replacement: fromRoot('./node_modules/jstree/dist/jstree.min.js') }
    ]
  },
  test: {
    environment: 'node',
    include: ['src/**/*.spec.ts']
  }
});
