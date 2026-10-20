import type { CapacitorConfig } from '@capacitor/cli';

const config: CapacitorConfig = {
  appId: 'com.yomira.reader',
  appName: 'Yomira',
  webDir: 'dist',
  server: {
    androidScheme: 'https'
  },
  android: {
    backgroundColor: '#07070a',
    allowMixedContent: true
  }
};

export default config;
