#!bin/sh

set -e;

echo 'Preparing for benchmark...';

prepQueryBody=$(cat 'prep-query.json');

curl -s \
 -H 'Content-Length: 2661' \
 -H 'accept: */*' \
 -H 'User-Agent: Mozilla/5.0 (X11; Linux x86_64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/85.0.4183.102 Safari/537.36' \
 -H 'content-type: application/json' \
 -H 'Origin: http://localhost:3030' \
 -H 'Sec-Fetch-Site: same-origin' \
 -H 'Sec-Fetch-Mode: cors' \
 -H 'Sec-Fetch-Dest: empty' \
 -H 'Referer: http://localhost:3030/graphql' \
 -H 'Accept-Encoding: deflate, br' \
 -H 'Accept-Language: en-US,en;q=0.9,ar;q=0.8' \
 -X POST \
 -d "$prepQueryBody" \
 'http://localhost:3030/graphql';

echo 'Benchmarking read request returning:'

benchQueryBody=$(cat 'ab-query.json')

curl -s \
 -H 'Content-Length: 2661' \
 -H 'accept: */*' \
 -H 'User-Agent: Mozilla/5.0 (X11; Linux x86_64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/85.0.4183.102 Safari/537.36' \
 -H 'content-type: application/json' \
 -H 'Origin: http://localhost:3030' \
 -H 'Sec-Fetch-Site: same-origin' \
 -H 'Sec-Fetch-Mode: cors' \
 -H 'Sec-Fetch-Dest: empty' \
 -H 'Referer: http://localhost:3030/graphql' \
 -H 'Accept-Encoding: deflate, br' \
 -H 'Accept-Language: en-US,en;q=0.9,ar;q=0.8' \
 -X POST \
 -d "$benchQueryBody" \
 'http://localhost:3030/graphql';

echo '';

ab -p ab-query.json -T application/json \
 -H 'Content-Length: 2661' \
 -H 'accept: */*' \
 -H 'User-Agent: Mozilla/5.0 (X11; Linux x86_64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/85.0.4183.102 Safari/537.36' \
 -H 'content-type: application/json' \
 -H 'Origin: http://localhost:3030' \
 -H 'Sec-Fetch-Site: same-origin' \
 -H 'Sec-Fetch-Mode: cors' \
 -H 'Sec-Fetch-Dest: empty' \
 -H 'Referer: http://localhost:3030/graphql' \
 -H 'Accept-Encoding: deflate, br' \
 -H 'Accept-Language: en-US,en;q=0.9,ar;q=0.8' \
 -n 50000 -c 1000 'http://localhost:3030/graphql';
