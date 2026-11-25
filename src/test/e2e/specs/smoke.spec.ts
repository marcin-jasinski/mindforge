import { expect, test } from '@playwright/test';

import { register } from './register';

test('a new user registers and sees the knowledge bases dashboard', async ({ page }) => {
  await page.goto('/login');
  await expect(page.getByRole('heading', { name: 'Welcome back' })).toBeVisible();
  await expect(page.getByLabel('E-mail')).toBeVisible();

  await register(page, 'smoke');

  await expect(page).toHaveURL(/\/dashboard$/);
  await expect(page.getByRole('heading', { name: 'Your knowledge bases' })).toBeVisible();

  // a new user has no knowledge base: the region is loaded but empty, and the empty state shows
  await expect(page.getByRole('region', { name: 'Knowledge bases' })).toHaveAttribute('aria-busy', 'false');
  await expect(page.getByText('Start your first knowledge base')).toBeVisible();
});
