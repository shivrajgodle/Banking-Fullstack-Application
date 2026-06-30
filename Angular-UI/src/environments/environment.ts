/**
 * Development environment configuration.
 * The Angular proxy (proxy.conf.json) forwards /api/* to http://localhost:8080/api/*
 */
export const environment = {
  production: false,
  /** Base URL for all API calls — proxied to Spring Boot in dev */
  apiUrl: '/api/v1',
  appName: 'NexBank',
  version: '1.0.0'
};
