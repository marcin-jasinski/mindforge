import { expect, test } from '@playwright/test';

test('a new user registers and sees the knowledge bases dashboard', async ({ page }) => {
  await page.goto('/login');
  await expect(page.getByRole('heading', { name: 'Welcome back' })).toBeVisible();
  await expect(page.getByLabel('E-mail')).toBeVisible();

  const email = `smoke-${Date.now()}-${Math.random().toString(36).slice(2, 8)}@e2e.test`;
  await page.goto('/register');
  await expect(page.getByRole('heading', { name: 'Create your account' })).toBeVisible();
  await page.getByLabel('Name').fill('Smoke User');
  await page.getByLabel('E-mail').fill(email);
  await page.getByLabel('Password').fill('smoke-password-1');
  await page.getByRole('button', { name: 'Create account' }).click();

  await expect(page).toHaveURL(/\/dashboard$/);
  await expect(page.getByRole('heading', { name: 'Your knowledge bases' })).toBeVisible();
  await expect(page.getByRole('region', { name: 'Knowledge bases' })).toBeVisible();
});
