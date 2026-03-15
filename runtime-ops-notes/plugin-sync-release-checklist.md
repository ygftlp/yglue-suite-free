# Plugin Sync Release Checklist

This checklist assumes the official release path is:

1. Publish flow and entrypoint in orchestrator.
2. Sync data into the local project through the IDEA plugin.
3. Build and release the service with the updated local `.ygflow` files.

## Release gate

- Orchestrator target project is reachable.
- Required flow versions are already marked as published.
- Required entrypoints are enabled in orchestrator.
- IDEA plugin sync completed without errors.
- Local project contains the refreshed `.ygflow` files.
- Service build and tests pass after sync.

## Operator steps

1. In orchestrator, confirm the target flow version is published.
2. In orchestrator, confirm the related REST entrypoint is enabled and points to the correct `flowCode`.
3. In IDEA, run the plugin sync action for the target project.
4. Confirm local files were updated:
   - `.ygflow/rules/<flowCode>.json`
   - `.ygflow/entrypoints.json`
5. Run the local verification script:

```powershell
.\verify-yglue-sync.ps1 `
  -BaseUrl "http://orchestrator-host:8091" `
  -ProjectKey "demo-project" `
  -SyncDir ".ygflow"
```

6. Build the service and run smoke tests.
7. Deploy the service artifact that contains the updated `.ygflow` directory.

## Smoke checklist

- Flow-free baseline endpoints still return their original business result.
- Flow-bound endpoints are intercepted and return the flow result.
- Branch flows return the selected path result only.
- Required input parameters resolve correctly from request payload.
- Disabled entrypoints are not intercepted.

## Failure policy

- If plugin sync fails: do not continue release.
- If `verify-yglue-sync.ps1` fails: do not continue release.
- If build or smoke test fails: roll back the local `.ygflow` changes and re-sync from orchestrator.

## Residual risk

The release path still depends on a successful plugin sync step. That is acceptable only if plugin sync is treated as a mandatory release gate rather than an optional developer convenience.
