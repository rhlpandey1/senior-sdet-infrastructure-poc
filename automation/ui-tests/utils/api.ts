import { APIRequestContext, expect } from '@playwright/test';

export async function getOrder(request: APIRequestContext, orderId: string) {
  return request.get(`/orders/${orderId}`, { failOnStatusCode: false });
}

export async function expectOrderExists(request: APIRequestContext, orderId: string) {
  const response = await getOrder(request, orderId);
  expect(response.status()).toBe(200);
  return response.json();
}
