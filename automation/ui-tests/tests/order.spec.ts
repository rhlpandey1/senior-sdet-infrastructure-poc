import { test, expect } from '../fixtures/test';
import { orders } from '../test-data/orders';
import { expectOrderExists } from '../utils/api';

test.describe('Order Management UI', () => {

  test('should search and display order details', async ({ orderPage, request }) => {
    const order = await expectOrderExists(request, orders.existing.id);
    expect(order.id).toBe(orders.existing.id);

    await orderPage.searchOrder(orders.existing.id);

    await orderPage.expectOrderDetails(
      orders.existing.id,
      orders.existing.customer,
      orders.existing.product,
      orders.existing.quantity,
      orders.existing.status
    );
  });

  test('should display error for unknown order', async ({ orderPage }) => {
    await orderPage.searchOrder(orders.unknown.id);
    await orderPage.expectError('Order not found');
  });

  test('should search order using Enter key', async ({ orderPage }) => {
    await orderPage.searchOrderWithEnter(orders.existing.id);
    await expect(orderPage.orderDetailsHeading).toBeVisible();
    await expect(orderPage.statusValue).toHaveText(orders.existing.status);
  });

  test('should handle backend failure gracefully', async ({ page, orderPage }) => {
    await page.route('**/orders/ORD-5000', async route => {
      await route.fulfill({
        status: 500,
        contentType: 'application/json',
        body: JSON.stringify({ message: 'Internal Server Error' }),
      });
    });

    await orderPage.searchOrder('ORD-5000');
    await orderPage.expectError('Unable to retrieve order');
  });
});
