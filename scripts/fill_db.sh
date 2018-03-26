#!/usr/bin/env bash

PGHOST=localhost
PGUSER=postgres
export PGPASSWORD=postgres
USER_PREFIX=random
USER_PW_HASH='a4ayc/80/OGda4BO/1o/V0etpOqiLx1JwB5S3beHW0s=' # password: 1
N_USERS=10

BOT_LANG='C++'
BOT_SOURCE=$(xxd -p bots/random.cpp | tr -d '\n')

dbexec() {
  psql -h $PGHOST -U $PGUSER -d postgres -c "$1"
}

entry_codes=""
players=""
submissions=""
for ((i=0; i < N_USERS; i++)); do
  USER=$USER_PREFIX$i

  if [[ ! -z $entry_codes ]]; then entry_codes="$entry_codes, "; fi
  entry_codes+="('test_code_$USER')"

  if [[ ! -z $players ]]; then players="$players, "; fi
  players+="('$USER', '$USER_PW_HASH', 'test_code_$USER', '$USER', 'FEUP', '$USER@example.com', false, $(date +%s)000)"

  if [[ ! -z $submissions ]]; then submissions="$submissions, "; fi
  submissions+="('$USER', '$BOT_LANG', 'file', decode(\$BOT\$$BOT_SOURCE\$BOT\$, 'hex'), '{\"Submitted\":{}}', $(date +%s)000)"
done

dbexec 'INSERT INTO "EntryCodes" VALUES '"$entry_codes"
dbexec 'INSERT INTO "Players" VALUES '"$players"
dbexec 'INSERT INTO "Submissions" ("user", "language", "filename", "payload", "state", "submitted") VALUES '"$submissions"
