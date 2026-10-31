"""Local Graph Scan simulator; response fields follow jfrog-client-go/xray/services/scan.go. Exigences : DEVAI-173, DEVAI-177."""
import base64
import hmac
import json
import os
import threading
import time
import uuid
from http.server import BaseHTTPRequestHandler, ThreadingHTTPServer
from urllib.parse import parse_qs, urlsplit

SCANS = {}
LOCK = threading.Lock()
PREFIX = '/xray/api/v1/scan/graph'


def report(scan_id, graph, watch):
    """Generate explicit synthetic findings for submitted components using the Xray schema. Exigences : DEVAI-173, DEVAI-177."""
    nodes = list(graph['nodes'])
    affected = []
    while nodes:
        node = nodes.pop()
        if node['component_id'] == 'gav://org.apache.commons:commons-lang3:3.17.0':
            affected.append(node['component_id'])
        nodes.extend(node.get('nodes', []))
    component = affected[0] if affected else graph['component_id']
    components = {component: {'fixed_versions': ['[3.18.0]'], 'impact_paths': [[
        {'component_id': graph['component_id']}, {'component_id': component}]]}}
    issue = {'issue_id': 'XRAY-MOCK-0001', 'summary': 'MOCK: synthetic vulnerability for manual integration testing',
             'severity': 'High', 'components': components, 'cves': [], 'references': []}
    return {'scan_id': scan_id, 'status': 'completed', 'component_id': graph['component_id'],
            'vulnerabilities': [issue] if affected else [], 'violations': [dict(issue, type='security', watch_name=watch,
                fail_build=True, policies=[{'policy': 'manual-test-policy', 'rule': 'mock-high'}])] if affected else [],
            'licenses': [{'license_key': 'Apache-2.0', 'name': 'Apache License 2.0', 'components': {component: {}}}]}


class Handler(BaseHTTPRequestHandler):
    """Accept authenticated Graph Scan requests and emulate asynchronous HTTP 202 polling. Exigences : DEVAI-173, DEVAI-177."""
    def log_message(self, fmt, *args):
        # Avoid credentials, payloads and query parameters in logs.
        """Journalise la route et la methode sans credential ni contenu prive. Exigences : DEVAI-173, DEVAI-177."""
        print(json.dumps({'method': self.command, 'path': urlsplit(self.path).path}), flush=True)

    def send_json(self, status, body):
        """Serialise une reponse JSON avec son statut et ses en-tetes HTTP. Exigences : DEVAI-173, DEVAI-177."""
        data = json.dumps(body).encode()
        self.send_response(status)
        self.send_header('Content-Type', 'application/json')
        self.send_header('Content-Length', str(len(data)))
        self.end_headers()
        self.wfile.write(data)

    def authorized(self):
        """Compare les credentials Bearer ou Basic aux valeurs administrees. Exigences : DEVAI-173, DEVAI-177."""
        token = os.environ.get('MOCK_XRAY_TOKEN', '')
        basic = base64.b64encode((os.environ.get('MOCK_XRAY_USER', '') + ':' + os.environ.get('MOCK_XRAY_PASSWORD', '')).encode()).decode()
        supplied = self.headers.get('Authorization', '')
        return (bool(token) and hmac.compare_digest(supplied, 'Bearer ' + token)) or (
            bool(os.environ.get('MOCK_XRAY_PASSWORD')) and hmac.compare_digest(supplied, 'Basic ' + basic))

    def do_GET(self):
        """Retourne la sante du mock ou le resultat du scan identifie. Exigences : DEVAI-173, DEVAI-177."""
        path = urlsplit(self.path).path
        if path == '/health':
            return self.send_json(200, {'status': 'UP', 'service': 'mockXray', 'simulated': True})
        if not self.authorized():
            return self.send_json(401, {'error': 'Unauthorized'})
        if path.startswith(PREFIX + '/'):
            with LOCK:
                scan = SCANS.get(path.removeprefix(PREFIX + '/'))
            if scan is None:
                return self.send_json(404, {'error': 'Scan not found'})
            if time.monotonic() < scan['ready']:
                return self.send_json(202, {'status': 'pending'})
            return self.send_json(200, scan['report'])
        self.send_json(404, {'error': 'Not found'})

    def do_POST(self):
        """Valide le graphe et le watch puis cree un scan asynchrone. Exigences : DEVAI-173, DEVAI-177."""
        if not self.authorized():
            return self.send_json(401, {'error': 'Unauthorized'})
        route = urlsplit(self.path)
        if route.path != PREFIX:
            return self.send_json(404, {'error': 'Not found'})
        try:
            length = int(self.headers.get('Content-Length', '0'))
            if not 0 < length <= 6_000_000:
                return self.send_json(413, {'error': 'Invalid body size'})
            graph = json.loads(self.rfile.read(length))
            if not isinstance(graph.get('component_id'), str) or not isinstance(graph.get('nodes'), list):
                raise ValueError('Invalid graph')
            query = parse_qs(route.query)
            watch = query.get('watch', [''])[0]
            if watch != os.environ.get('MOCK_XRAY_WATCH', 'manual-test-watch'):
                return self.send_json(403, {'error': 'Watch not authorized'})
            if query.get('scan_type') != ['dependency']:
                raise ValueError('Dependency scan required')
            scan_id = uuid.uuid4().hex
            result = report(scan_id, graph, watch)
            with LOCK:
                if len(SCANS) >= 1000:
                    SCANS.pop(next(iter(SCANS)))
                SCANS[scan_id] = {'ready': time.monotonic() + 2, 'report': result}
            self.send_json(201, {'scan_id': scan_id})
        except (ValueError, KeyError, TypeError, IndexError):
            self.send_json(400, {'error': 'Invalid dependency graph'})


if __name__ == '__main__':
    ThreadingHTTPServer(('0.0.0.0', 8080), Handler).serve_forever()
