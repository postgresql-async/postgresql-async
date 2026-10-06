#!/usr/bin/env bash
# Thin wrapper for CI service containers, which cannot pass a command.
exec bash /custom/scripts/entrypoint.sh postgres
