import { apiErrorMessage, failureLine, failureReasonLabel } from './labels';

describe('labels', () => {
  it('answers an error by its code, not by the server text', () => {
    expect(apiErrorMessage({ error: 'x', code: 'MODEL_UNAVAILABLE', detail: 'The model returned unreadable output' }))
      .toContain('Model językowy');
    expect(apiErrorMessage({ error: 'x', code: 'UNHEARD_OF', detail: 'raw' } as never)).toBe('Coś poszło nie tak.');
  });

  it('puts a failure on one line, whatever of step, item, path, reason and count it carries', () => {
    expect(failureLine({ step: 'linkCheck', count: 2 })).toBe('sprawdzanie linków — pominięto 2');
    expect(failureLine({ step: 'write', path: 'concepts/mitoza', reason: 'unexpected error' }))
      .toBe('zapis strony · concepts/mitoza: Nieoczekiwany błąd — szczegóły są w logu serwera.');
  });

  it('translates known failure reasons and leaves unknown ones', () => {
    expect(failureReasonLabel('unexpected error')).toContain('Nieoczekiwany');
    expect(failureReasonLabel('Extract returned 400 claims')).toBe('Extract returned 400 claims');
  });
});
