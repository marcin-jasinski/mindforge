import { HttpErrorResponse } from '@angular/common/http';
import { TestBed } from '@angular/core/testing';
import { provideRouter } from '@angular/router';
import { of, throwError } from 'rxjs';

import { ProgressService } from '../../core/services/progress.service';
import { QueryApi } from '../../core/services/query-api';
import { RunApi } from '../../core/services/run-api';
import { WikiApi } from '../../core/services/wiki-api';
import { Chat } from './chat';

describe('Chat', () => {
  function setup(query: Partial<Record<'ask' | 'edit', ReturnType<typeof vi.fn>>>, revert = vi.fn(() => of({}))) {
    const queryApi = { open: vi.fn(() => of({ interactionId: 'session' })), ...query };
    TestBed.configureTestingModule({
      providers: [
        provideRouter([]),
        { provide: QueryApi, useValue: queryApi },
        { provide: RunApi, useValue: { revert, report: vi.fn() } },
        { provide: ProgressService, useValue: { reload: vi.fn(), steps: () => ({}) } },
        { provide: WikiApi, useValue: { index: vi.fn(() => of({ pages: [{ path: 'concepts/mitosis', title: 'Mitosis' }] })) } },
      ],
    });
    const fixture = TestBed.createComponent(Chat);
    fixture.componentRef.setInput('kbId', 'kb');
    fixture.detectChanges();
    return { chat: fixture.componentInstance, revert };
  }

  afterEach(() => vi.restoreAllMocks());

  it('keeps the question in the box when the answer fails, so it can be sent again', () => {
    const failure = new HttpErrorResponse({ status: 503, error: { code: 'MODEL_UNAVAILABLE' } });
    const { chat } = setup({ ask: vi.fn(() => throwError(() => failure)) });
    chat.draft.set('What is photosynthesis?');

    chat.ask();

    expect(chat.draft()).toBe('What is photosynthesis?');
    expect(chat.busy()).toBe(false);
  });

  it('names a cited page by its title and falls back to the path', () => {
    const { chat } = setup({});

    expect(chat.citedTitle('concepts/mitosis')).toBe('Mitosis');
    expect(chat.citedTitle('concepts/nieznana')).toBe('concepts/nieznana');
  });

  it('says the wiki is changing while an edit runs, not that it is thinking', () => {
    const { chat } = setup({ edit: vi.fn(() => of({ runId: 'run' })) });
    expect(chat.editStatus()).toBeNull();
    chat.draft.set('Add the birth year to Hannibal.');

    chat.edit();

    expect(chat.editStatus()).toBe('Changing the wiki…');
  });

  it('asks before undoing a change and sends nothing when declined', () => {
    vi.spyOn(window, 'confirm').mockReturnValue(false);
    const { chat, revert } = setup({});

    chat.revert({ runId: 'run' } as never);

    expect(window.confirm).toHaveBeenCalled();
    expect(revert).not.toHaveBeenCalled();
  });

  it('reverts once confirmed', () => {
    vi.spyOn(window, 'confirm').mockReturnValue(true);
    const { chat, revert } = setup({});

    chat.revert({ runId: 'run' } as never);

    expect(revert).toHaveBeenCalledWith('kb', 'run');
  });

  it('stops offering a revert for a change once it is reverted', () => {
    vi.spyOn(window, 'confirm').mockReturnValue(true);
    const { chat } = setup({});
    chat.messages.set([{ role: 'edit', text: 'The wiki was changed.', run: { runId: 'run', revertOffered: true } as never }]);

    chat.revert({ runId: 'run' } as never);

    expect(chat.messages()[0].run?.revertOffered).toBe(false);
    expect(chat.messages().at(-1)?.text).toBe('The change was reverted.');
  });
});
