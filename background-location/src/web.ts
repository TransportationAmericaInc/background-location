import { WebPlugin } from '@capacitor/core';
import { T2BackgroundLocationPlugin } from './definitions';

export class T2BackgroundLocationWeb extends WebPlugin implements T2BackgroundLocationPlugin {
  constructor() {
    super({
      name: 'T2BackgroundLocation',
      platforms: ['web'],
    });
  }

  async echo(options: { value: string }): Promise<{ value: string }> {
    console.log('ECHO', options);
    return options;
  }
}

const T2BackgroundLocation = new T2BackgroundLocationWeb();

export { T2BackgroundLocation };

import { registerWebPlugin } from '@capacitor/core';
registerWebPlugin(T2BackgroundLocation);
