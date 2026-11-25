import { Page, expect, test } from '@playwright/test';

import { register } from './register';

// The quiz referenceAnswer of model-stub/mappings/quiz.json; ModelStubMappingsTest keeps the two equal.
const REFERENCE_ANSWER = 'E2E-SECRET-REFERENCE-ANSWER';
// The stub answers come at once; the bound only covers the queue poll and the run's steps on a slow host.
const RUN_TIMEOUT = 60_000;

test('a learner goes from an upload to a downloaded OKF bundle of the wiki', async ({ page }) => {
  test.setTimeout(180_000);

  // register
  await page.goto('/login');
  await expect(page.getByRole('heading', { name: 'Welcome back' })).toBeVisible();
  await register(page, 'journey');

  // upload
  await page.getByLabel('Name').fill('Biology');
  await page.getByRole('button', { name: 'Create', exact: true }).click();
  await expect(page.getByRole('heading', { level: 1, name: 'Biology' })).toBeVisible();
  await page.getByLabel('Choose a document').setInputFiles('fixtures/biology.md');
  await page.getByRole('button', { name: 'Upload and build pages' }).click();

  // run completes
  const activity = page.getByRole('region', { name: 'Activity' });
  await expect(activity.getByText('completed', { exact: true })).toBeVisible({ timeout: RUN_TIMEOUT });

  // pages appear
  await page.getByRole('navigation', { name: 'Knowledge base sections' }).getByRole('link', { name: 'Pages' }).click();
  const concepts = page.getByRole('region', { name: 'Concepts' });
  const sources = page.getByRole('region', { name: 'Sources' });
  await expect(concepts.getByRole('link')).toHaveCount(1);
  await expect(concepts.getByRole('link', { name: /Mitosis/ })).toBeVisible();
  await expect(sources.getByRole('link')).toHaveCount(1);
  await expect(sources.getByRole('link', { name: /biology/ })).toBeVisible();

  // open a page
  await concepts.getByRole('link', { name: /Mitosis/ }).click();
  await expect(page).toHaveURL(/\/pages\/concepts\/mitosis$/);
  await expect(page.getByRole('heading', { level: 1, name: 'Mitosis' })).toBeVisible();
  await expect(page.getByText('Mitosis splits a cell in two.')).toBeVisible();

  // review flashcards
  await page.getByRole('navigation', { name: 'Knowledge base sections' }).getByRole('link', { name: 'Study' }).click();
  await page.getByRole('button', { name: 'Review flashcards' }).click();
  await expect(page.getByText('What is mitosis?')).toBeVisible({ timeout: RUN_TIMEOUT });
  await expect(page.getByText('Cell division.')).toHaveCount(0);
  await page.getByRole('button', { name: /Show answer/ }).click();
  await expect(page.getByText('Cell division.')).toBeVisible();
  await page.getByRole('button', { name: '4 — Remembered', exact: true }).click();
  await expect(page.getByRole('heading', { name: 'Session complete' })).toBeVisible();
  await expect(page.getByText('You reviewed 1 card and remembered 1.')).toBeVisible();

  // take a quiz
  await page.getByRole('button', { name: 'End session' }).click();
  await page.getByRole('button', { name: 'Start a quiz' }).click();
  await expect(page.getByText('What does mitosis mean?')).toBeVisible({ timeout: RUN_TIMEOUT });
  await expectNoReferenceAnswer(page);
  await page.getByLabel('Your answer').fill('A cell splits into two cells.');
  await page.getByRole('button', { name: 'Check answer' }).click();
  await expect(page.getByText('4 / 5', { exact: false }).first()).toBeVisible({ timeout: RUN_TIMEOUT });
  await expect(page.getByText('Almost right.')).toBeVisible();
  await expectNoReferenceAnswer(page);
  await page.getByRole('button', { name: /See results/ }).click();
  await expect(page.getByRole('heading', { name: 'Quiz complete' })).toBeVisible();
  await expectNoReferenceAnswer(page);

  // export bundle: the server validates it before it answers, so a download is a conformant bundle
  const downloading = page.waitForEvent('download');
  await page.getByRole('link', { name: /Export/ }).click();
  const download = await downloading;
  expect(download.suggestedFilename()).toBe('biology-okf.zip');
  expect(zipEntryNames(await readAll(download.createReadStream()))).toEqual(
    expect.arrayContaining([
      'biology-okf/index.md',
      'biology-okf/log.md',
      'biology-okf/concepts/mitosis.md',
      'biology-okf/sources/biology.md',
    ]),
  );
});

/** The marker is in no text and no attribute of the document. */
async function expectNoReferenceAnswer(page: Page): Promise<void> {
  await expect(page.locator('body')).not.toContainText(REFERENCE_ANSWER);
  expect(await page.content()).not.toContain(REFERENCE_ANSWER);
}

async function readAll(stream: Promise<AsyncIterable<Uint8Array>>): Promise<Uint8Array> {
  const chunks: Uint8Array[] = [];
  for await (const chunk of await stream) {
    chunks.push(chunk);
  }
  const bytes = new Uint8Array(chunks.reduce((total, chunk) => total + chunk.length, 0));
  let offset = 0;
  for (const chunk of chunks) {
    bytes.set(chunk, offset);
    offset += chunk.length;
  }
  return bytes;
}

/** The entry names from a zip's central directory (no zip64, no archive comment beyond 64 KiB). */
function zipEntryNames(zip: Uint8Array): string[] {
  const view = new DataView(zip.buffer, zip.byteOffset, zip.byteLength);
  let end = zip.length - 22;
  while (end >= 0 && view.getUint32(end, true) !== 0x06054b50) {
    end--;
  }
  expect(end, 'end of central directory record').toBeGreaterThanOrEqual(0);
  const count = view.getUint16(end + 10, true);
  let header = view.getUint32(end + 16, true);
  const names: string[] = [];
  for (let i = 0; i < count; i++) {
    expect(view.getUint32(header, true), 'central directory header').toBe(0x02014b50);
    const nameLength = view.getUint16(header + 28, true);
    const extraLength = view.getUint16(header + 30, true);
    const commentLength = view.getUint16(header + 32, true);
    names.push(new TextDecoder().decode(zip.subarray(header + 46, header + 46 + nameLength)));
    header += 46 + nameLength + extraLength + commentLength;
  }
  return names;
}
