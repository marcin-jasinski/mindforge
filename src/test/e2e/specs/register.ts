import { Page, expect } from '@playwright/test';

/** Registers a new user with a unique e-mail and waits for the knowledge bases dashboard. */
export async function register(page: Page, prefix: string): Promise<void> {
  const email = `${prefix}-${Date.now()}-${Math.random().toString(36).slice(2, 8)}@e2e.test`;
  await page.goto('/register');
  await expect(page.getByRole('heading', { name: 'Create your account' })).toBeVisible();
  await page.getByLabel('Name').fill(`${prefix} user`);
  await page.getByLabel('E-mail').fill(email);
  await page.getByLabel('Password').fill(`${prefix}-password-1`);
  await page.getByRole('button', { name: 'Create account' }).click();

  await expect(page).toHaveURL(/\/dashboard$/);
  await expect(page.getByRole('heading', { name: 'Your knowledge bases' })).toBeVisible();
}
