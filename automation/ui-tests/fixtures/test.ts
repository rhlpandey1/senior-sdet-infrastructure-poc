import { test as base, expect } from '@playwright/test';
import { OrderPage } from '../pages/OrderPage';

type Fixtures = {
  orderPage: OrderPage;
};

export const test = base.extend<Fixtures>({
  orderPage: async ({ page }, use) => {
    const orderPage = new OrderPage(page);
    await orderPage.open();
    await use(orderPage);
  },
});

export { expect };
