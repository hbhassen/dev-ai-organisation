const ts = require('typescript');
const fs = require('node:fs');
const path = require('node:path');
const errors = [];
/** Checks every named TypeScript declaration for adjacent explanatory requirement documentation. Exigences : DEVAI-137, DEVAI-138. */
function check(file) {
  const text = fs.readFileSync(file, 'utf8'); const source = ts.createSourceFile(file, text, ts.ScriptTarget.Latest, true);
  /** Traverses symbols, including private methods and constructors, using the compiler's syntax tree. Exigences : DEVAI-137. */
  function visit(node) {
    if (ts.isClassDeclaration(node) || ts.isInterfaceDeclaration(node) || ts.isFunctionDeclaration(node) || ts.isMethodDeclaration(node) || ts.isConstructorDeclaration(node)) {
      const docs = node.jsDoc?.map(doc => doc.getText(source)).join('\n') || '';
      if (!/DEVAI-\d{3}/.test(docs) || docs.length < 45) errors.push(`${file}:${source.getLineAndCharacterOfPosition(node.getStart()).line + 1} documentation requise`);
    }
    ts.forEachChild(node, visit);
  }
  visit(source);
}
for (const file of fs.readdirSync(path.join(__dirname, '../src'))) if (file.endsWith('.ts')) check(path.join(__dirname, '../src', file));
if (errors.length) { console.error(errors.join('\n')); process.exit(1); }
console.log('Documentation TypeScript : classes, constructeurs et méthodes vérifiés.');
