import { TestBed } from '@angular/core/testing';
import { of } from 'rxjs';

import { RunApi } from './run-api';
import { ProgressService } from './progress.service';

class FakeSource {
  static last: FakeSource;
  onopen: (() => void) | null = null;
  closed = false;
  private listeners: Record<string, (e: unknown) => void> = {};
  constructor(public url: string) {
    FakeSource.last = this;
  }
  addEventListener(type: string, fn: (e: unknown) => void) {
    this.listeners[type] = fn;
  }
  emit(data: unknown) {
    this.listeners['progress']({ data: JSON.stringify(data) });
  }
  close() {
    this.closed = true;
  }
}

describe('ProgressService', () => {
  let get: ReturnType<typeof vi.fn>;
  let service: ProgressService;

  beforeEach(() => {
    vi.stubGlobal('EventSource', FakeSource);
    get = vi.fn(() => of([{ runId: 'r1' }]));
    TestBed.configureTestingModule({
      providers: [ProgressService, { provide: RunApi, useValue: { runs: get, progressUrl: (kbId: string) => kbId } }],
    });
    service = TestBed.inject(ProgressService);
  });

  afterEach(() => vi.unstubAllGlobals());

  it('reads the run list on connect and again on every reconnect', () => {
    service.watch('kb');
    expect(get).toHaveBeenCalledTimes(1);
    FakeSource.last.onopen!();
    expect(get).toHaveBeenCalledTimes(2);
    expect(service.runs()).toEqual([{ runId: 'r1' }]);
  });

  it('records a step without reloading for a known run', () => {
    service.watch('kb');
    get.mockClear();
    FakeSource.last.emit({ runId: 'r1', step: 'EXTRACT' });
    expect(service.steps()['r1'].step).toBe('EXTRACT');
    expect(get).not.toHaveBeenCalled();
  });

  it('reloads for a new run and for a terminal status change', () => {
    service.watch('kb');
    get.mockClear();
    FakeSource.last.emit({ runId: 'new', step: 'EXTRACT' });
    FakeSource.last.emit({ runId: 'r1', step: null });
    expect(get).toHaveBeenCalledTimes(2);
  });

  it('closes the stream when watching again and on destroy', () => {
    service.watch('kb');
    const first = FakeSource.last;
    service.watch('kb');
    expect(first.closed).toBe(true);
    service.ngOnDestroy();
    expect(FakeSource.last.closed).toBe(true);
  });
});
