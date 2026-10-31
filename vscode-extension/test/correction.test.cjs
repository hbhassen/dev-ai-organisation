const test = require('node:test');
const assert = require('node:assert/strict');
const { stillPresent, coverageChange, coverageIntact } = require('../dist/correction');
const { correctionPdf } = require('../dist/correctionPdf');
test('DEVAI-175: moved Sonar lines retain issue identity and distinct files do not match', () => {
  const original = { key: 'old', rule: 'java:S1', path: 'src/A.java', message: 'Finding' };
  assert.equal(stillPresent(original, [{ ...original, key: 'new', line: 45 }]), true);
  assert.equal(stillPresent(original, [{ ...original, key: 'new', path: 'src/B.java' }]), false);
});
test('DEVAI-090: coverage tasks refuse production rewrites, removed tests and new exclusions', () => {
  assert.throws(() => coverageChange('src/main/App.java', 'class App {}', 'class App { int x; }'));
  assert.throws(() => coverageChange('src/test/AppTest.java', 'a'.repeat(100), 'a'));
  assert.throws(() => coverageChange('pom.xml', '<project/>', '<project><sonar.coverage.exclusions>**</sonar.coverage.exclusions></project>'));
  assert.throws(() => coverageChange('tests/test_app.py', 'def test_a(): pass', '@pytest.mark.skip\ndef test_a(): pass'));
  coverageChange('src/test/AppTest.java', 'class AppTest {}', 'class AppTest { void testMore() {} }');
});
test('DEVAI-175: improved coverage must retain every original measured file and denominator', () => {
  const before = { coverage: 20, lines_to_cover: 10, files: [{ path: 'A.java', lines_to_cover: 10, conditions_to_cover: 2 }] };
  assert.equal(coverageIntact(before, { ...before, coverage: 80 }), true);
  assert.equal(coverageIntact(before, { ...before, coverage: 100, files: [] }), false);
  assert.equal(coverageIntact(before, { ...before, coverage: 100, lines_to_cover: 1 }), false);
  assert.equal(coverageIntact(before, { ...before, coverage: undefined }), false);
});
test('DEVAI-090/098: PDF has byte-correct cross references, pagination and escaped original evidence', () => {
  const report = { source: 'SONAR', branch: 'fix/example', created: '2026-10-31', rounds: 2, validated: true, initialFingerprint: 'abc', files: ['src/App.java'], checkpoints: [], message: 'Validé', modelSummary: 'Correction expliquée.', validationLog: 'Tests passed\n'.repeat(80), items: [{ key: 'issue-one', severity: 'MAJOR', path: 'src/App.java', title: 'Finding (with parentheses)', status: 'corrected', description: 'Disparition confirmée', explanation: 'Remplacement du code.', initial: { key: 'issue-one' } }] };
  const pdf = correctionPdf(report), text = pdf.toString('latin1');
  assert.ok(text.startsWith('%PDF-1.4')); assert.match(text, /\/Count [2-9]/); assert.match(text, /CORRIGE/); assert.match(text, /Finding \\\(/);
  const offset = Number(/startxref\n(\d+)/.exec(text)[1]); assert.equal(pdf.subarray(offset, offset + 4).toString(), 'xref');
  for (const match of text.matchAll(/(\d{10}) 00000 n/g)) assert.match(pdf.subarray(Number(match[1])).toString('latin1'), /^\d+ 0 obj/);
});
