declare module '@capacitor/core' {
  interface PluginRegistry {
    T2BackgroundLocation: T2BackgroundLocationPlugin;
  }
}

export interface T2BackgroundLocationPlugin {
  echo(options: { value: string }): Promise<{ value: string }>;
}
