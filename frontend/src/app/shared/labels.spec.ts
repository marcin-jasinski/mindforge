import { apiErrorMessage, failureLine, failureReasonLabel } from './labels';

describe('labels', () => {
  it('answers an error by its code, not by the server text', () => {
    expect(apiErrorMessage({ error: 'x', code: 'MODEL_UNAVAILABLE', detail: 'The model returned unreadable output' }))
      .toContain('language model');
    expect(apiErrorMessage({ error: 'x', code: 'UNHEARD_OF', detail: 'raw' } as never)).toBe('Something went wrong.');
  });

  it('puts a failure on one line, whatever of step, item, path, reason and count it carries', () => {
    expect(failureLine({ step: 'linkCheck', count: 2 })).toBe('link check — 2 skipped');
    expect(failureLine({ step: 'write', path: 'concepts/mitosis', reason: 'unexpected error' }))
      .toBe('page write · concepts/mitosis: Unexpected error — the details are in the server log.');
  });

  it('translates known failure reasons and leaves unknown ones', () => {
    expect(failureReasonLabel('unexpected error')).toContain('Unexpected error');
    expect(failureReasonLabel('Extract returned 400 claims')).toBe('Extract returned 400 claims');
  });
});
