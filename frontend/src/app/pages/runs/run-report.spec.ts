import { HttpErrorResponse } from '@angular/common/http';
import { TestBed } from '@angular/core/testing';
import { provideRouter } from '@angular/router';
import { of, throwError } from 'rxjs';

import { RunApi } from '../../core/services/run-api';
import { ProgressService } from '../../core/services/progress.service';
import { RunReportView } from './run-report';

describe('RunReportView revert', () => {
  function setup(revert: ReturnType<typeof vi.fn>, confirmed = true) {
    vi.spyOn(window, 'confirm').mockReturnValue(confirmed);
    const api = { report: vi.fn(() => of({ revertOffered: true, pages: [], supersessions: [], failures: [], findings: [] })), revert };
    const progress = { reload: vi.fn() };
    TestBed.configureTestingModule({
      providers: [
        provideRouter([]),
        { provide: RunApi, useValue: api },
        { provide: ProgressService, useValue: progress },
      ],
    });
    const fixture = TestBed.createComponent(RunReportView);
    fixture.componentRef.setInput('kbId', 'kb');
    fixture.componentRef.setInput('runId', 'run');
    fixture.detectChanges();
    return { fixture, api, progress };
  }

  afterEach(() => vi.restoreAllMocks());

  it('posts the revert for this run and refreshes the run list once confirmed', () => {
    const { fixture, api, progress } = setup(vi.fn(() => of({})));
    fixture.componentInstance.revert();
    expect(window.confirm).toHaveBeenCalled();
    expect(api.revert).toHaveBeenCalledWith('kb', 'run');
    expect(progress.reload).toHaveBeenCalledWith('kb');
  });

  it('sends nothing when the confirmation is declined', () => {
    const { fixture, api, progress } = setup(vi.fn(() => of({})), false);
    fixture.componentInstance.revert();
    expect(api.revert).not.toHaveBeenCalled();
    expect(progress.reload).not.toHaveBeenCalled();
  });

  it('explains a busy knowledge base instead of failing silently', () => {
    const busy = new HttpErrorResponse({ status: 409, error: { code: 'KNOWLEDGE_BASE_BUSY' } });
    const { fixture, progress } = setup(vi.fn(() => throwError(() => busy)));
    fixture.componentInstance.revert();
    expect(fixture.componentInstance.message()).toContain('Another run');
    expect(progress.reload).not.toHaveBeenCalled();
  });
});
