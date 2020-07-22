# Primary

The Janitor is expected to be deployed with one primary instance and 0-many secondary instances.
The primary instance controls the lifecycle of tracked resources. Having a single primary instance
live at a given time makes it easier to reason about concurrency. Only actions that should not be
done by multiple instances should be confined to the primary.