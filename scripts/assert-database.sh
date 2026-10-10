#!/usr/bin/env bash
set -euo pipefail
mkdir -p test-results
docker compose exec -T mysql sh -c 'MYSQL_PWD="$MYSQL_PASSWORD" mysql -u"$MYSQL_USER" "$MYSQL_DATABASE" -N -e "SELECT COUNT(*) FROM space s LEFT JOIN (SELECT space_id,COUNT(*) AS n,SUM(bytes) AS b FROM picture GROUP BY space_id) p ON p.space_id=s.id WHERE s.used_count<>COALESCE(p.n,0) OR s.used_bytes<>COALESCE(p.b,0); SELECT COUNT(*) FROM (SELECT space_id,user_id,COUNT(*) AS n FROM space_member GROUP BY space_id,user_id HAVING n>1) d; SELECT COUNT(*) FROM ai_job WHERE status=\"SUCCEEDED\" AND picture_id IS NULL;"' > test-results/database-check.txt
expected=$'0\n0\n0'
actual=$(cat test-results/database-check.txt)
if [[ "$actual" != "$expected" ]]; then
  echo "Database invariant verification failed: $actual"
  exit 1
fi
echo "SQL verified: quota mismatches=0, duplicate members=0, successful AI jobs missing image=0"
