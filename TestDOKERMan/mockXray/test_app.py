"""Exercise Graph Scan over real HTTP, including authentication and pending responses."""
import base64
import json
import os
import threading
import unittest
import urllib.error
import urllib.request
from http.server import ThreadingHTTPServer
import app


class GraphScanTest(unittest.TestCase):
    @classmethod
    def setUpClass(cls):
        os.environ.update(MOCK_XRAY_USER='test', MOCK_XRAY_PASSWORD='test-only', MOCK_XRAY_TOKEN='test-token')
        cls.server = ThreadingHTTPServer(('127.0.0.1', 0), app.Handler)
        cls.thread = threading.Thread(target=cls.server.serve_forever, daemon=True)
        cls.thread.start()
        cls.url = 'http://127.0.0.1:' + str(cls.server.server_port)

    @classmethod
    def tearDownClass(cls):
        cls.server.shutdown()
        cls.server.server_close()
        cls.thread.join()

    def request(self, route, data=None, authorization='Bearer test-token'):
        req = urllib.request.Request(self.url + route, json.dumps(data).encode() if data is not None else None,
            {'Authorization': authorization, 'Content-Type': 'application/json'})
        try:
            with urllib.request.urlopen(req) as response:
                return response.status, json.load(response)
        except urllib.error.HTTPError as error:
            return error.code, json.load(error)

    def test_rejects_missing_auth_and_unknown_watch(self):
        graph = {'component_id': 'root-node', 'nodes': []}
        self.assertEqual(401, self.request(app.PREFIX, graph, '')[0])
        self.assertEqual(403, self.request(app.PREFIX + '?watch=wrong&scan_type=dependency', graph)[0])

    def test_invalid_graph_and_unknown_scan(self):
        self.assertEqual(400, self.request(app.PREFIX, {'nodes': []})[0])
        self.assertEqual(404, self.request(app.PREFIX + '/unknown')[0])

    def test_graph_scan_contract_and_async_lifecycle(self):
        component = 'gav://org.apache.commons:commons-lang3:3.17.0'
        graph = {'component_id': 'root-node', 'nodes': [{'component_id': component, 'nodes': []}]}
        auth = 'Basic ' + base64.b64encode(b'test:test-only').decode()
        status, submitted = self.request(app.PREFIX + '?watch=manual-test-watch&scan_type=dependency', graph, auth)
        self.assertEqual(201, status)
        scan_id = submitted['scan_id']
        self.assertEqual(202, self.request(app.PREFIX + '/' + scan_id)[0])
        app.SCANS[scan_id]['ready'] = 0
        status, result = self.request(app.PREFIX + '/' + scan_id + '?include_vulnerabilities=true&include_licenses=true')
        self.assertEqual(200, status)
        self.assertEqual(scan_id, result['scan_id'])
        issue = result['vulnerabilities'][0]
        self.assertIsInstance(issue['components'], dict)
        self.assertEqual(component, issue['components'][component]['impact_paths'][0][1]['component_id'])
        self.assertIsInstance(issue['components'][component]['fixed_versions'], list)
        self.assertEqual('manual-test-watch', result['violations'][0]['watch_name'])
        self.assertTrue(result['violations'][0]['fail_build'])
        self.assertIn('license_key', result['licenses'][0])

    def test_corrected_dependency_clears_synthetic_finding(self):
        graph = {'component_id': 'root-node', 'nodes': [{'component_id': 'gav://org.apache.commons:commons-lang3:3.18.0', 'nodes': []}]}
        result = app.report('fixed', graph, 'manual-test-watch')
        self.assertEqual([], result['vulnerabilities'])
        self.assertEqual([], result['violations'])


if __name__ == '__main__':
    unittest.main()
