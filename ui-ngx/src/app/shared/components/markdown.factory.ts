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


import { MarkedOptions, MarkedRenderer } from 'ngx-markdown';

export function markedOptionsFactory(): MarkedOptions {
  const renderer = new MarkedRenderer();
  const renderer2 = new MarkedRenderer();

  const copyCodeBlock = '{:copy-code}';

  let id = 1;

  renderer.code = (code: string, language: string | undefined, isEscaped: boolean) => {
    if (code.endsWith(copyCodeBlock)) {
      if (code.includes('{:hostname}')) {
        code = code.replace('{:hostname}', window.location.hostname);
        // @ts-ignore
        code = code.replace('{:port}', window.mqttPort);
      }
      code = code.substring(0, code.length - copyCodeBlock.length);
      const content = renderer2.code(code, language, isEscaped);
      id++;
      return wrapCopyCode(id, content, code);
    } else {
      return renderer2.code(code, language, isEscaped);
    }
  };

  renderer.tablecell = (content: string, flags: {
    header: boolean;
    align: 'center' | 'left' | 'right' | null;
  }) => {
    if (content.endsWith(copyCodeBlock)) {
      content = content.substring(0, content.length - copyCodeBlock.length);
      id++;
      content = wrapCopyCode(id, content, content);
    }
    return renderer2.tablecell(content, flags);
  };

  return {
    renderer,
    headerIds: true,
    gfm: true,
    breaks: false,
    pedantic: false,
    smartLists: true,
    smartypants: false,
  };
}

function wrapCopyCode(id: number, content: string, code: string): string {
  return '<div class="code-wrapper">' + content + '<span id="copyCodeId' + id + '" style="display: none;">' + code + '</span>' +
    '<button title="Copy code" id="copyCodeBtn' + id + '" onClick="markdownCopyCode(' + id + ')" ' +
    'class="clipboard-btn"><mat-icon class="material-icons" style="font-size: 16px">content_copy</mat-icon>' +
    '</button></div>';
}

(window as any).markdownCopyCode = (id: number) => {
  const text = $('#copyCodeId' + id).text();
  if (navigator.clipboard) {
    navigator.clipboard.writeText(text).then(() => {
      showCopyTooltipster(id);
    });
  } else {
    const textArea = document.createElement('textarea');
    textArea.value = text;
    textArea.style.position = 'fixed';
    document.body.appendChild(textArea);
    textArea.focus();
    textArea.select();
    try {
      document.execCommand('copy');
      showCopyTooltipster(id);
    } catch (err) {
      console.error(err);
    }
    document.body.removeChild(textArea);
  }
};

function showCopyTooltipster(id) {
  // @ts-ignore
  import('tooltipster').then(
    () => {
      const copyBtn = $('#copyCodeBtn' + id);
      if (!copyBtn.hasClass('tooltipstered')) {
        copyBtn.tooltipster(
          {
            content: 'Copied',
            theme: 'tooltipster-shadow',
            delay: 0,
            trigger: 'custom',
            triggerClose: {
              click: true,
              tap: true,
              scroll: true,
              mouseleave: true
            },
            side: 'bottom',
            distance: 12,
            trackOrigin: true
          }
        );
      }
      const tooltip = copyBtn.tooltipster('instance');
      tooltip.open();
    }
  );
}
