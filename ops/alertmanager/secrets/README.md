# Alerting destinations

Alertmanager reads each receiver's URL from a file here. They are not committed: a webhook
URL is a credential.

```bash
printf 'https://your-paging-integration.example/hook' > pager-url
printf 'https://your-ticket-integration.example/hook' > tickets-url
```

Until these exist, Alertmanager will not start — which is deliberate. A pager that points
nowhere looks healthy and pages nobody.
