# Angular deployed to Vercel, Spring Boot deployed to Railway

The frontend (Angular SPA) and backend (Spring Boot API + pipeline worker) are deployed
to separate hosting platforms. Angular is deployed to Vercel; Spring Boot runs as a
Docker container on Railway. Both platforms offer free tiers suitable for a personal-use
application and first-class support for the respective runtimes.

## Considered Options

- **Single Docker container serving both (Spring Boot serves the Angular dist)** —
  considered but used only as a fallback: Spring Boot can serve the Angular build from
  `frontend/dist/` via `ResourceHttpRequestHandler`. This is used in the Docker build
  for local and staging environments. For production, the split deployment is preferred
  because Vercel provides global CDN distribution, automatic PR preview URLs for the
  Angular SPA, and zero-config HTTPS — capabilities that are not worth replicating in
  the backend container.

- **Netlify for Angular** — rejected in favour of Vercel: both are equivalent for static
  Angular deployment, but Vercel's PR preview URL integration with GitHub is tighter and
  the `vercel.json` proxy configuration for routing API calls is simpler.

- **Fly.io / Render for the backend** — rejected in favour of Railway: Railway offers
  PostgreSQL and Neo4j as managed add-ons in the same project, keeping the full backend
  topology in one dashboard. A single `railway.toml` file configures the deployment.

## Consequences

- The Angular `environment.ts` files set `apiUrl` to the Railway backend URL in
  production and to `http://localhost:8080` in development.
- CORS must be explicitly configured in Spring Security to allow requests from the
  Vercel domain. The allowed origins list is an environment variable, not hardcoded.
- GitHub Actions runs the Maven build (including the Angular build via
  `frontend-maven-plugin`) and pushes the Docker image to Railway on every merge to
  `main`. Vercel picks up Angular changes directly from the `frontend/` directory via
  its GitHub integration.
- PR preview URLs from Vercel point at the Railway staging environment. The staging
  backend URL is also an environment variable so it can differ from production.
