# Pager delivery drill

This drill is a launch gate. Configuration validation proves only that Alertmanager can
parse YAML; it does not prove a human receives an alert.

1. Put real pager and ticket webhook URLs in the external secret manager and sync the
   `gocommerce-alerting` Secret. Do not commit or paste URLs into evidence.
2. Confirm Alertmanager is healthy and its configuration shows `pager` as the receiver for
   `severity="page"`.
3. Post a uniquely labelled synthetic alert to Alertmanager:

   ```bash
   drill_id="pager-drill-$(date -u +%Y%m%dT%H%M%SZ)"
   curl --fail-with-body -H 'Content-Type: application/json' \
     -d "[{\"labels\":{\"alertname\":\"PagerDeliveryDrill\",\"severity\":\"page\",\"service\":\"operations\",\"drill_id\":\"${drill_id}\"},\"annotations\":{\"summary\":\"Synthetic pager delivery drill\"}}]" \
     https://alertmanager.example/api/v2/alerts
   ```

4. The on-call human records receipt time and the exact `drill_id`, acknowledges through
   the real paging system, and escalates to the secondary if the acknowledgement target is
   missed.
5. Resolve the alert through Alertmanager, confirm the resolution notification arrives,
   and attach redacted screenshots/event IDs to the launch record.

No drill has been performed for this repository. Do not mark alerting ready until a named
human has observed both firing and resolution end to end.
