#!/bin/bash
# Applies db/schema.sql (mounted read-only at /schema-source/schema.sql) to the
# database the Postgres image already created from POSTGRES_DB. Runs as part of
# the official postgres image's first-boot initialization.
#
# schema.sql opens with `CREATE DATABASE event_platform;`, which is meant for
# running the file by hand against a fresh Postgres instance (see README). In
# compose, POSTGRES_DB already creates that database before this script runs,
# so that line is stripped here instead of duplicating the rest of the schema
# in a second file.
set -euo pipefail

grep -vi '^CREATE DATABASE' /schema-source/schema.sql | \
  psql -v ON_ERROR_STOP=1 --username "$POSTGRES_USER" --dbname "$POSTGRES_DB"
