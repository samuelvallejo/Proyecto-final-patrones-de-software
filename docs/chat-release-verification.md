# Chat release verification

On 2026-10-08, Render was still running `a0f6d9f`, although the fallback fix had been pushed to GitHub. A healthy `/actuator/health` response did not identify the deployed revision. The dashboard confirmed that this service uses manual deployments from `codex/streamguard` in the main repository.

Deploying `5a9c614` through **Manual Deploy → Deploy latest commit** completed with **Deploy succeeded | Live**. The deployed source includes the change from invalid AI request status `FALLBACK` to permitted status `LOCAL`.

The production smoke check used an isolated stream with a creator and viewer, then ended the stream and logged out both test sessions. It verified:

- Creator message `hola`: `VISIBLE`.
- Viewer message `jajajaja`: `VISIBLE`.
- Creator message `buen directo`: `VISIBLE`.
- Viewer message `idiota`: `HIDDEN`.
- The public REST list contained exactly the three allowed messages.
- The viewer received the three allowed messages through WebSocket broadcasts.

The offline-model regression test checks that ordinary messages remain `SAFE` and persist the allowed `LOCAL` status. The backend suite passed 18 tests in the separate backend repository. Both frontend builds passed type checking and localization checks.

The studio now places its live chat beside the video, with the community assistant beneath the studio grid. On narrow viewports, the existing responsive grid stacks the video and chat and keeps the assistant visible below them.

For subsequent backend fixes, pushing a commit alone is insufficient while Render remains configured for manual deployment. Verify the dashboard's deployed source and test the actual message flow after release.
