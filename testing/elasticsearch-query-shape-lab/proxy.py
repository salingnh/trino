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

"""A lab-only ES8 recording/fault proxy. Bound only to loopback on the host."""
import http.client
import json
import threading
from http.server import BaseHTTPRequestHandler, ThreadingHTTPServer
from urllib.parse import parse_qs, urlsplit

lock = threading.Lock()
state = {'mode': 'normal', 'searches': []}


class Proxy(BaseHTTPRequestHandler):
    def log_message(self, *args):
        pass

    def handle_request(self):
        parsed = urlsplit(self.path)
        if parsed.path.startswith('/__lab/'):
            with lock:
                if parsed.path == '/__lab/control':
                    args = parse_qs(parsed.query)
                    state['mode'] = args.get('mode', ['normal'])[0]
                    if args.get('reset') == ['1']:
                        state['searches'] = []
                data = json.dumps(state).encode()
            self.send_response(200)
            self.send_header('Content-Type', 'application/json')
            self.send_header('Content-Length', str(len(data)))
            self.end_headers()
            self.wfile.write(data)
            return
        body = self.rfile.read(int(self.headers.get('Content-Length', 0)))
        connection = http.client.HTTPConnection('es8', 9200, timeout=120)
        headers = {'Content-Type': self.headers.get('Content-Type', 'application/json')}
        connection.request(self.command, self.path, body or None, headers)
        response = connection.getresponse()
        data = response.read()
        status = response.status
        content_type = response.getheader('Content-Type', 'application/json')
        if self.command == 'POST' and (parsed.path.endswith('/_search') or parsed.path == '/_search/scroll'):
            request = json.loads(body or b'{}')
            result = json.loads(data)
            if request.get('size') == 0 and 'error' not in result:
                with lock:
                    mode = state['mode']
                    state['mode'] = 'normal'
                if mode == 'missing_completion':
                    result.pop('timed_out', None)
                elif mode == 'timeout':
                    result['timed_out'] = True
                elif mode == 'failed_shard':
                    result['_shards']['failed'] = 1
                elif mode == 'invalid_failed_type':
                    result['_shards']['failed'] = '0'
                elif mode == 'inexact_count':
                    result['hits']['total']['relation'] = 'gte'
                elif mode == 'negative_count':
                    result['hits']['total']['value'] = -1
                if mode != 'normal':
                    data = json.dumps(result, separators=(',', ':')).encode()
            with lock:
                composite = request.get('aggs', {}).get('groups', {}).get('composite', {})
                state['searches'].append({
                    'path': self.path, 'size': request.get('size'),
                    'aggregation': bool(request.get('aggs')), 'composite_size': composite.get('size'),
                    'after': composite.get('after'), 'response_bytes': len(data),
                    'source_hits': len(result.get('hits', {}).get('hits', [])),
                    'buckets': len(result.get('aggregations', {}).get('groups', {}).get('buckets', [])),
                })
        self.send_response(status)
        self.send_header('Content-Type', content_type)
        self.send_header('Content-Length', str(len(data)))
        self.end_headers()
        self.wfile.write(data)
        connection.close()

    do_GET = handle_request
    do_POST = handle_request
    do_DELETE = handle_request
    do_PUT = handle_request
    do_HEAD = handle_request


ThreadingHTTPServer(('0.0.0.0', 9200), Proxy).serve_forever()
