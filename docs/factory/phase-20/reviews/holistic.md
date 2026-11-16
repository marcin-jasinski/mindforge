---
lens: holistic
ticket: phase-20
verdict: approve
---
- id: H1
  severity: minor
  confidence: 0.9
  location: src/main/java/dev/mindforge/api/config/RateLimitExceededException.java:3
  claim: The exception's Javadoc still says it is thrown only when "a user has spent their allowance of model-calling requests", but P20-01 now also throws it from AuthRateLimitInterceptor for anonymous sign-in and sign-up attempts per client address.
  evidence: AuthRateLimitInterceptor.preHandle (api/config/AuthRateLimitInterceptor.java:49) throws `new RateLimitExceededException(limit, resetSeconds)` for POST /api/v1/auth/login and /register, which RateLimitConfig registers. These requests have no user and spend no model call. The phase made this doc comment wrong. RateLimitConfig's Javadoc was updated, but this one was not.
  fix: Change the Javadoc to "Thrown when a caller (a user, or a client address on sign-in and sign-up) has spent its allowance for the current window."
