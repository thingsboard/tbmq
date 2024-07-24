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

export const gettingStartedGuideTitle = (id: string) => gettingStartedGuides.find(el => el.id === id)?.title;

export const gettingStartedGuides: GettingStartedLink[] = [
  {
    id: 'websocket',
    img: '/assets/home/no_data_bg.svg',
    title: 'ws-client.ws-client',
    subtitle: 'getting-started.guide.ws-client-subtitle',
    url: '/ws-client'
  },
  {
    id: 'guide-mosquitto',
    img: '/assets/home/no_data_bg.svg',
    title: 'getting-started.guide.mosquitto-title',
    subtitle: 'getting-started.guide.mosquitto-subtitle',
    url: '/getting-started/guide-mosquitto'
  },
  {
    id: 'guide-javascript',
    img: '/assets/getting-started/js.png',
    title: 'getting-started.guide.javascript-title',
    subtitle: 'getting-started.guide.javascript-subtitle',
    url: '/getting-started/guide-javascript'
  },
  {
    id: 'guide-python',
    img: '/assets/getting-started/python.png',
    title: 'getting-started.guide.python-title',
    subtitle: 'getting-started.guide.python-subtitle',
    url: '/getting-started/guide-python'
  },
  {
    id: 'guide-arduino',
    img: '/assets/getting-started/arduino.png',
    title: 'getting-started.guide.arduino-title',
    subtitle: 'getting-started.guide.arduino-subtitle',
    url: '/getting-started/guide-arduino'
  },
  {
    id: 'github',
    img: '/assets/home/no_data_bg.svg',
    title: 'Github',
    subtitle: '',
    url: ''
  },
]

export interface GettingStartedLink {
  id: string;
  url: string;
  img: string;
  title?: string;
  subtitle?: string;
}
