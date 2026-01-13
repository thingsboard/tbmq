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

import { ChangeDetectionStrategy, Component, input, output } from '@angular/core';
import { Color } from '@iplab/ngx-color-picker';
import { MatFormField, MatPrefix, MatSuffix } from '@angular/material/form-field';
import { MatInput } from '@angular/material/input';
import { CopyButtonComponent } from '../button/copy-button.component';
import { TranslateModule } from '@ngx-translate/core';

@Component({
    selector: `tb-hex-input`,
    templateUrl: `./hex-input.component.html`,
    styleUrls: ['./hex-input.component.scss'],
    changeDetection: ChangeDetectionStrategy.OnPush,
    imports: [MatFormField, MatPrefix, MatInput, CopyButtonComponent, MatSuffix, TranslateModule]
})
export class HexInputComponent {

  readonly colorChange = output<Color>();

  readonly color = input<Color>();
  readonly labelVisible = input(false);
  readonly prefixValue = input('#');

  get value() {
    const color = this.color();
    return color ? color.toHexString(color.getRgba().alpha < 1).replace('#', '') : '';
  }

  get copyColor() {
    return this.prefixValue() + this.value;
  }

  get hueValue(): string {
    const color = this.color();
    return color ? Math.round(color.getRgba().alpha * 100).toString() : '';
  }

  onHueInputChange(event: KeyboardEvent, inputValue: string): void {
    const color = this.color().getRgba();
    const alpha = +inputValue / 100;
    if (color.getAlpha() !== alpha) {
      const newColor = new Color().setRgba(color.red, color.green, color.blue, alpha).toHexString(true);
      this.colorChange.emit(new Color(newColor));
    }
  }

  onInputChange(event: KeyboardEvent, inputValue: string): void {
    const value = inputValue.replace('#', '').toLowerCase();
    if (
      ((event.keyCode === 13 || event.key.toLowerCase() === 'enter') && value.length === 3)
      || value.length === 6 || value.length === 8
    ) {
      const hex = parseInt(value, 16);
      const hexStr = hex.toString(16);
      if (hexStr.padStart(value.length, '0') === value && this.value.toLowerCase() !== value) {
        const newColor = new Color(`#${value}`);
        this.colorChange.emit(newColor);
      }
    }
  }
}
