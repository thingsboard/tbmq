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
    id: 'guide-python',
    img: '/assets/getting-started/python.png',
    title: 'getting-started.guide.python-title',
    subtitle: 'getting-started.guide.python-subtitle',
    url: '/getting-started/guide-python'
  },
  {
    id: 'guide-javascript',
    img: '/assets/getting-started/js.png',
    title: 'getting-started.guide.javascript-title',
    subtitle: 'getting-started.guide.javascript-subtitle',
    url: '/getting-started/guide-javascript'
  },
  {
    id: 'guide-arduino',
    img: '/assets/getting-started/arduino.png',
    title: 'getting-started.guide.arduino-title',
    subtitle: 'getting-started.guide.arduino-subtitle',
    url: '/getting-started/guide-arduino'
  },
  {
    id: 'guide-mosquitto',
    img: '/assets/getting-started/mosquitto.png',
    title: 'getting-started.guide.mosquitto-title',
    subtitle: 'getting-started.guide.mosquitto-subtitle',
    url: '/getting-started/guide-mosquitto'
  },
  {
    id: 'guide-java',
    img: '/assets/getting-started/java.webp',
    title: 'getting-started.guide.java-title',
    subtitle: 'getting-started.guide.java-subtitle',
    url: '/getting-started/guide-java'
  },
  {
    id: 'guide-java-ws',
    img: '/assets/getting-started/java.webp',
    title: 'getting-started.guide.java-ws-title',
    subtitle: 'getting-started.guide.java-ws-subtitle',
    url: '/getting-started/guide-java-ws'
  },
  /*{
    id: 'websocket',
    img: '/assets/home/no_data_bg.svg',
    title: 'ws-client.ws-client',
    subtitle: 'getting-started.guide.ws-client-subtitle',
    url: '/ws-client'
  },*/
]

export const gettingStartedDocs: GettingStartedLink[] = [
  {
    img: '/assets/home/no_data_bg.svg',
    title: 'Getting Started',
    subtitle: 'TBMQ',
    url: 'https://thingsboard.io/docs/mqtt-broker/getting-started'
  },
  {
    img: '/assets/home/no_data_bg.svg',
    title: 'Configuration',
    subtitle: 'TBMQ',
    url: 'https://thingsboard.io/docs/mqtt-broker/install/config/'
  },
  {
    img: '/assets/home/no_data_bg.svg',
    title: 'Security',
    subtitle: 'TBMQ',
    url: 'https://thingsboard.io/docs/mqtt-broker/getting-started'
  },
  {
    img: '/assets/home/no_data_bg.svg',
    title: 'Client Type',
    subtitle: 'TBMQ',
    url: 'https://thingsboard.io/docs/mqtt-broker/mqtt-client-type'
  },
  {
    img: '/assets/home/no_data_bg.svg',
    title: 'Monitoring',
    subtitle: 'TBMQ',
    url: 'https://thingsboard.io/docs/mqtt-broker/user-guide/ui/monitoring'
  },
  {
    img: '/assets/home/no_data_bg.svg',
    title: 'WebSocket Client',
    subtitle: 'TBMQ',
    url: 'https://thingsboard.io/docs/mqtt-broker/ui/websocket-client'
  },
  {
    img: '/assets/home/no_data_bg.svg',
    title: 'Troubleshooting',
    subtitle: 'TBMQ',
    url: 'https://thingsboard.io/docs/mqtt-broker/troubleshooting'
  },
]

export interface GettingStartedLink {
  title: string;
  url: string;
  id?: string;
  img?: string;
  subtitle?: string;
}

export const gettingStartedActions: GettingStartedLink[] = [
  {
    title: 'getting-started.guide.releases',
    url: 'https://github.com/thingsboard/tbmq/releases'
  },
  {
    title: 'getting-started.guide.support',
    url: 'https://thingsboard.io/docs/contact-us'
  },
  {
    title: 'getting-started.guide.github',
    url: 'https://github.com/thingsboard/tbmq'
  },
  {
    title: 'getting-started.guide.docs',
    url: 'https://thingsboard.io/docs/mqtt-broker/getting-started/'
  },
  {
    title: 'getting-started.guide.pricing',
    url: 'https://thingsboard.io/pricing/?section=tbmq'
  }
]
