#!/usr/bin/env python3
# Licensed under the Apache License, Version 2.0 (the "License");
# you may not use this file except in compliance with the License.
# You may obtain a copy of the License at
#
#     http://www.apache.org/licenses/LICENSE-2.0
#
# Unless required by applicable law or agreed to in writing, software
# distributed under the License is distributed on an "AS IS" BASIS,
# WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
# See the License for the specific language governing permissions and
# limitations under the License.

"""Lab-only ES recorder: separate listener/state for every Trino revision and backend."""
import gzip
import http.client
from http.server import BaseHTTPRequestHandler, ThreadingHTTPServer
import json
import re
import threading
import time
from urllib.parse import parse_qs, urlsplit

LOCK = threading.Lock()
EVENTS = {}
HYBRID_ABLATION = {}
BACKENDS = {'es8': 'trino-harbor-verification-es8-1', 'es7': 'trino-expr-es7'}


class Proxy(BaseHTTPRequestHandler):
    protocol_version = 'HTTP/1.1'

    def log_message(self, *args):
        pass

    def reply(self, status, data, headers=None):
        self.send_response(status)
        for name, value in (headers or {}).items():
            if name.lower() not in ('connection', 'transfer-encoding', 'content-length'):
                self.send_header(name, value)
        self.send_header('Content-Length', str(len(data)))
        self.end_headers()
        self.wfile.write(data)

    def handle_request(self):
        parsed = urlsplit(self.path)
        if parsed.path == '/__compare/hybrid-ablation':
            if self.command == 'POST':
                setting = json.loads(self.rfile.read(int(self.headers.get('Content-Length', 0))))
                if setting and (not re.fullmatch(r'expr_es8_hybrid_20261118_s\d+_n\d+_cases', setting.get('index', ''))
                                or setting.get('mode') not in ('hybrid', 'script', 'local')):
                    self.reply(400, b'Invalid lab scope')
                    return
                with LOCK:
                    HYBRID_ABLATION.clear()
                    HYBRID_ABLATION.update(setting)
            with LOCK:
                setting = dict(HYBRID_ABLATION)
            self.reply(200, json.dumps(setting).encode(), {'Content-Type': 'application/json'})
            return
        if parsed.path == '/__compare/control':
            args = parse_qs(parsed.query)
            key = args.get('variant', ['master'])[0] + ':' + args.get('backend', ['es8'])[0]
            with LOCK:
                result = list(EVENTS.get(key, []))
                if args.get('reset') == ['1']:
                    EVENTS[key] = []
            self.reply(200, json.dumps({'key': key, 'events': result}).encode(), {'Content-Type': 'application/json'})
            return
        body = self.rfile.read(int(self.headers.get('Content-Length', 0)))
        headers = {k: v for k, v in self.headers.items() if k.lower() not in ('host', 'connection', 'content-length', 'accept-encoding')}
        headers['Accept-Encoding'] = 'identity'
        with LOCK:
            ablation = dict(HYBRID_ABLATION)
        original_query = None
        rewritten = False
        if (ablation and self.server.variant == 'current' and self.server.backend == 'es8'
                and parsed.path == '/' + ablation['index'] + '/_search'):
            # Normalize all three modes equally; only pure-script replaces the WHERE predicate.
            request = json.loads(gzip.decompress(body) if headers.get('Content-Encoding') == 'gzip' else body or b'{}')
            if ablation['mode'] == 'script':
                original_query = request.get('query')
                assert 'script' in json.dumps(original_query), 'Expected an actual hybrid predicate'
                request['query'] = ablation['query']
                rewritten = True
            body = json.dumps(request, separators=(',', ':')).encode()
            headers = {k: v for k, v in headers.items() if k.lower() != 'content-encoding'}
        connection = http.client.HTTPConnection(BACKENDS[self.server.backend], 9200, timeout=120)
        start = time.monotonic()
        try:
            connection.request(self.command, self.path, body or None, headers)
            response = connection.getresponse()
            data = response.read()
            response_headers = dict(response.getheaders())
            if '/_search' in parsed.path or parsed.path.endswith('/_count'):
                request = {}
                result = {}
                try:
                    request = json.loads(gzip.decompress(body) if headers.get('Content-Encoding') == 'gzip' else body or b'{}')
                    result = json.loads(gzip.decompress(data) if response_headers.get('Content-Encoding') == 'gzip' else data)
                except (ValueError, OSError):
                    pass
                event = {
                    'method': self.command, 'path': self.path, 'status': response.status,
                    'ablation_rewritten': rewritten, 'original_query': original_query,
                    'source_ids': [hit.get('_source', {}).get('id') for hit in result.get('hits', {}).get('hits', [])],
                    'request': request, 'request_bytes': len(body), 'response_bytes': len(data),
                    'source_hits': len(result.get('hits', {}).get('hits', [])),
                    'hit_total': result.get('hits', {}).get('total'), 'count': result.get('count'),
                    'took_ms': result.get('took'), 'elapsed_ms': round((time.monotonic() - start) * 1000, 3),
                    'error': result.get('error'),
                }
                with LOCK:
                    EVENTS.setdefault(self.server.variant + ':' + self.server.backend, []).append(event)
            self.reply(response.status, data, response_headers)
        except (OSError, http.client.HTTPException) as error:
            self.reply(502, json.dumps({'error': str(error)}).encode(), {'Content-Type': 'application/json'})
        finally:
            connection.close()

    do_GET = handle_request
    do_POST = handle_request
    do_PUT = handle_request
    do_DELETE = handle_request
    do_HEAD = handle_request


if __name__ == '__main__':
    threads = []
    for backend, offset in [('es8', 0), ('es7', 10)]:
        for index, variant in enumerate(['master', 'internal', 'current']):
            server = ThreadingHTTPServer(('0.0.0.0', 9200 + offset + index), Proxy)
            server.variant = variant
            server.backend = backend
            thread = threading.Thread(target=server.serve_forever, daemon=True)
            thread.start()
            threads.append(thread)
    print('Recorder ready for three revisions on Elasticsearch 7/8', flush=True)
    threads[0].join()
