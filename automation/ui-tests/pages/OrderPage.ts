import { expect, Page } from '@playwright/test';

export class OrderPage {
  constructor(private readonly page: Page) {}

  readonly orderIdInput = this.page.getByLabel('Order ID');
  readonly searchButton = this.page.getByRole('button', { name: 'Search Order' });
  readonly orderDetailsHeading = this.page.getByRole('heading', { name: 'Order Details' });
  readonly errorMessage = this.page.locator('#errorMessage');
  readonly orderIdValue = this.page.locator('#orderIdValue');
  readonly customerValue = this.page.locator('#customerValue');
  readonly productValue = this.page.locator('#productValue');
  readonly quantityValue = this.page.locator('#quantityValue');
  readonly statusValue = this.page.locator('#statusValue');

  async open(): Promise<void> {
    await this.page.goto('/');
  }

  async searchOrder(orderId: string): Promise<void> {
    await this.orderIdInput.fill(orderId);
    await this.searchButton.click();
  }

  async searchOrderWithEnter(orderId: string): Promise<void> {
    await this.orderIdInput.fill(orderId);
    await this.orderIdInput.press('Enter');
  }

  async expectOrderDetails(orderId: string, customer: string, product: string, quantity: string, status: string): Promise<void> {
    await expect(this.orderDetailsHeading).toBeVisible();
    await expect(this.orderIdValue).toHaveText(orderId);
    await expect(this.customerValue).toHaveText(customer);
    await expect(this.productValue).toHaveText(product);
    await expect(this.quantityValue).toHaveText(quantity);
    await expect(this.statusValue).toHaveText(status);
  }

  async expectError(message: string): Promise<void> {
    await expect(this.errorMessage).toBeVisible();
    await expect(this.errorMessage).toHaveText(message);
  }
}
