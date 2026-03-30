"""
Admin router — operational metrics and diagnostics.

All endpoints require authentication.
"""
from __future__ import annotations

from fastapi import APIRouter, Depends

from api.auth import require_auth
from api.schemas import UserInfo

router = APIRouter(prefix="/api/admin", tags=["admin"])


@router.get("/metrics")
async def get_metrics(_user: UserInfo = Depends(require_auth)) -> dict:
    """Return a snapshot of in-process operational counters.

    Publishes:
    - ``egress_blocked``   — cumulative count of outbound URL violations blocked
      by the egress policy since process start.
    - ``pipeline_skipped`` — cumulative count of lesson files skipped because
      they were already processed or claimed by a concurrent process.
    - ``llm_usage_by_model`` — cumulative prompt / completion / total token
      spend broken down by model name.

    These counters are in-process and reset on process restart.  For long-term
    retention, scrape this endpoint into an external time-series store.
    """
    from processor.metrics import snapshot
    return snapshot()
