import { CorrectionReport } from './correction';
/** Creates a portable, paginated PDF from native evidence with no browser HTML or remote resources. Exigences : DEVAI-090, DEVAI-098. */
export function correctionPdf(report: CorrectionReport): Buffer {
  const labels = { corrected: 'CORRIGE', partial: 'PARTIELLEMENT CORRIGE', unimplemented: 'NON IMPLEMENTE' };
  const sections = ['ODS | Rapport de correction', `${report.source} | ${report.created}`, `Branche : ${report.branch}`, `Tentatives : ${report.rounds} | Compilation/tests : ${report.validated ? 'VALIDES' : 'NON VALIDES'}`, report.message, `Analyse initiale : ${report.initialAnalysis ?? 'indisponible'}`, `Empreinte initiale : ${report.initialFingerprint}`, `Analyse finale : ${report.finalAnalysis ?? 'indisponible'}`, `Empreinte finale : ${report.finalFingerprint ?? 'indisponible'}`, ...(report.target === undefined ? [] : [`Couverture : ${report.before}% -> ${report.after ?? 'inconnue'}% | Objectif : ${report.target}%`]), '', ...report.items.flatMap((item, index) => [`${index + 1}. [${item.severity}] ${item.title}`, `Identifiant : ${item.key} | ${item.path}`, labels[item.status], item.description, 'Explication du modele : ' + (item.explanation ?? 'Non fournie pour cette anomalie.'), 'Constat initial : ' + JSON.stringify(item.initial), '']), 'Fichiers modifies', ...report.files, '', 'Explication du modele (declarative)', report.modelSummary || 'Aucune explication fournie.', '', 'Compilation et tests : diagnostic', report.validationLog || 'Aucun resultat disponible.'];
  if (report.newFindings?.length) sections.push('', 'Nouvelles anomalies du scan final', ...report.newFindings.map(item => JSON.stringify(item)));
  const lines: string[] = [];
  for (const section of sections) for (const paragraph of section.split(/\r?\n/)) {
    const normalized = paragraph.replace(/\t/g, '  ').replace(/[’‘]/g, "'").replace(/[–—→]/g, '-').replace(/œ/g, 'oe').replace(/[^\x20-\xFF]/g, '?');
    if (!normalized) lines.push('');
    let remaining = normalized;
    while (remaining.length > 92) {
      const space = remaining.lastIndexOf(' ', 92), end = space > 40 ? space : 92;
      lines.push(remaining.slice(0, end)); remaining = remaining.slice(end).trimStart();
    }
    if (remaining) lines.push(remaining);
  }
  const pages: string[][] = []; for (let offset = 0; offset < lines.length; offset += 48) pages.push(lines.slice(offset, offset + 48));
  const objects: string[] = ['', '', '<< /Type /Font /Subtype /Type1 /BaseFont /Helvetica /Encoding /WinAnsiEncoding >>'];
  const kids: string[] = [];
  for (const [index, page] of pages.entries()) {
    const pageId = objects.length + 1, contentId = pageId + 1; kids.push(`${pageId} 0 R`);
    const escape = (value: string) => value.replace(/([\\()])/g, '\\$1');
    const stream = `0.08 0.20 0.34 rg 40 790 515 24 re f\nBT /F1 11 Tf 1 1 1 rg 50 798 Td (ODS - ${escape(report.source)} - ${index + 1}/${pages.length}) Tj ET\nBT /F1 10 Tf 0.12 0.15 0.20 rg 14 TL 40 770 Td\n` + page.map(line => `(${escape(line)}) Tj T*`).join('\n') + '\nET';
    objects.push(`<< /Type /Page /Parent 2 0 R /MediaBox [0 0 595 842] /Resources << /Font << /F1 3 0 R >> >> /Contents ${contentId} 0 R >>`, `<< /Length ${Buffer.byteLength(stream, 'latin1')} >>\nstream\n${stream}\nendstream`);
  }
  objects[0] = '<< /Type /Catalog /Pages 2 0 R >>'; objects[1] = `<< /Type /Pages /Count ${pages.length} /Kids [${kids.join(' ')}] >>`;
  let document = '%PDF-1.4\n%\xE2\xE3\xCF\xD3\n'; const offsets = [0];
  for (const [index, object] of objects.entries()) { offsets.push(Buffer.byteLength(document, 'latin1')); document += `${index + 1} 0 obj\n${object}\nendobj\n`; }
  const start = Buffer.byteLength(document, 'latin1');
  document += `xref\n0 ${objects.length + 1}\n0000000000 65535 f \n` + offsets.slice(1).map(offset => `${String(offset).padStart(10, '0')} 00000 n \n`).join('') + `trailer\n<< /Size ${objects.length + 1} /Root 1 0 R >>\nstartxref\n${start}\n%%EOF\n`;
  return Buffer.from(document, 'latin1');
}
