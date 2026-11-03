/** Builds the editable presentation from reviewed local sources. Does not build the extension. */
import fs from 'node:fs/promises';
import path from 'node:path';
import { fileURLToPath, pathToFileURL } from 'node:url';
import { createRequire } from 'node:module';

const here = path.dirname(fileURLToPath(import.meta.url));
const repository = path.dirname(here);
const runtimeModules = process.env.RUNTIME_NODE_MODULES;
const skillDir = process.env.PRESENTATIONS_SKILL_DIR;
const python = process.env.RUNTIME_PYTHON;
if (!runtimeModules || !skillDir || !python) throw new Error('Définir RUNTIME_NODE_MODULES, PRESENTATIONS_SKILL_DIR et RUNTIME_PYTHON.');
const requireRuntime = createRequire(path.join(runtimeModules, '__presentation__.cjs'));
const { Presentation, PresentationFile } = await import(pathToFileURL(requireRuntime.resolve('@oai/artifact-tool')).href);
const { finalizePresentation, applyPresentationChartFont } = await import(pathToFileURL(path.join(skillDir, 'container_tools/artifact_tool_utils.mjs')).href);
const build = path.join(repository, '.artifacts/demo-presentation');
const revision = process.env.DEMO_REVISION || '1';
const output = path.join(build, 'output', `ODS-presentation-${revision}.pptx`);
const data = JSON.parse(await fs.readFile(path.join(here, 'slides.json'), 'utf8'));
await fs.mkdir(path.join(build, 'renders'), { recursive: true });
await fs.mkdir(path.dirname(output), { recursive: true });
const p = Presentation.create({ slideSize: { width: 1280, height: 720 } });
const colors = { paper: '#F6F7F5', ink: '#102A35', muted: '#425B64', accent: '#007D74', dark: '#102A35', light: '#E2F3EF' };
const font = 'Segoe UI';

function text(slide, name, value, x, y, w, h, size = 28, color = colors.ink, bold = false) {
  const box = slide.shapes.add({ geometry: 'textbox', name, position: { left: x, top: y, width: w, height: h }, fill: 'none', line: { fill: 'none', width: 0 } });
  box.text = value;
  box.text.style = { typeface: font, fontSize: size, color, bold, insets: 0, wrap: 'square', autoFit: 'none', verticalAlignment: 'top' };
  return box;
}

function bodyLines(slide, value, x, y, width, spacing = 62, size = 27, color = colors.ink) {
  value.split('\n').forEach((line, index) => {
    if (line) text(slide, `line-${x}-${index}`, line, x, y + index * spacing, width, spacing - 6, size, color);
  });
}

for (const [index, d] of data.slides.entries()) {
  const slide = p.slides.add();
  const dark = d.type === 'cover' || d.type === 'closing';
  slide.background.fill = dark ? colors.dark : colors.paper;
  const fg = dark ? '#FFFFFF' : colors.ink;
  if (d.type === 'cover') {
    text(slide, 'cover-title', d.title, 80, 130, 1110, 300, 68, fg, true);
    text(slide, 'cover-subtitle', d.subtitle, 84, 458, 1090, 100, 28, colors.light);
    text(slide, 'cover-edition', d.caption, 84, 635, 1050, 30, 18, '#B9D4D2');
  } else {
    text(slide, 'slide-title', d.title, 64, 48, 1150, 115, 43, fg, true);
    if (d.type === 'split') {
      text(slide, 'lead', d.lead, 66, 165, 1145, 76, 29, colors.muted);
      text(slide, 'left-heading', d.leftTitle, 66, 270, 542, 45, 29, colors.accent, true);
      text(slide, 'right-heading', d.rightTitle, 665, 270, 550, 45, 29, colors.accent, true);
      bodyLines(slide, d.left, 66, 330, 542, 62, 26);
      bodyLines(slide, d.right, 665, 330, 550, 62, 26);
    } else if (d.type === 'steps') {
      d.steps.forEach(([label, explanation], n) => {
        const y = 183 + n * 101;
        text(slide, `number-${n}`, String(n + 1).padStart(2, '0'), 66, y, 90, 65, 44, colors.accent, true);
        text(slide, `step-title-${n}`, label, 170, y + 1, 275, 70, 30, colors.ink, true);
        text(slide, `step-detail-${n}`, explanation, 455, y + 3, 745, 82, 27, colors.muted);
      });
    } else if (d.type === 'table') {
      const values = [d.headers, ...d.rows];
      const rowHeight = d.rows.length > 4 ? 74 : d.rows.length === 3 ? 110 : 89;
      const widths = d.headers.length === 2 ? [370, 782] : [300, 432, 420];
      const table = slide.tables.add({ rows: values.length, columns: d.headers.length, left: 64, top: 160, width: 1152, height: rowHeight * values.length, columnWidths: widths, values });
      table.borders.assign({ fill: '#CBD8D8', width: 0.6, style: 'solid' });
      table.cells.block({ row: 0, column: 0, rowCount: values.length, columnCount: d.headers.length }).assign({ textStyle: { typeface: font, fontSize: 24, color: colors.ink }, margins: { top: 7, right: 14, bottom: 7, left: 14 }, anchor: 'center' });
      for (let r = 0; r < values.length; r++) {
        table.rows[r].height = r === 0 && values.length === 6 ? 60 : rowHeight;
        for (let c = 0; c < d.headers.length; c++) {
          const cell = table.getCell(r, c);
          cell.fill = r === 0 ? colors.dark : r % 2 === 1 ? '#FFFFFF' : '#EDF2F1';
          cell.text.style = { typeface: font, fontSize: 24, color: r === 0 ? '#FFFFFF' : colors.ink, bold: r === 0 || c === 0, verticalAlignment: 'middle' };
        }
      }
    } else if (d.type === 'image') {
      text(slide, 'image-explanation', d.body, 66, 188, 510, 430, 29, colors.ink);
      const bytes = await fs.readFile(path.join(here, d.image));
      slide.images.add({ blob: bytes.buffer.slice(bytes.byteOffset, bytes.byteOffset + bytes.byteLength), contentType: 'image/png', alt: 'Interface IA ODS 0.6.0 avec données de démonstration', fit: 'contain', position: { left: 630, top: 155, width: 575, height: 470 } });
    } else if (d.type === 'chart') {
      const chart = slide.charts.add('bar', {
        position: { left: 62, top: 175, width: 700, height: 420 },
        categories: d.categories,
        series: [{ name: 'Couverture Sonar (%)', values: d.values, valuesFormatCode: '0.0" %"', fill: colors.accent, points: [{ idx: 0, fill: '#809BA4' }, { idx: 1, fill: '#D39D48' }, { idx: 2, fill: colors.accent }] }],
        hasLegend: false,
        barOptions: { direction: 'column', grouping: 'clustered', gapWidth: 90 },
        xAxis: { textStyle: { typeface: font, fontSize: 25 } },
        yAxis: { min: 0, max: 110, majorUnit: 20, numberFormatCode: '0" %"', textStyle: { typeface: font, fontSize: 21 }, majorGridlines: { fill: '#D5DFDF', width: 1 } },
        dataLabels: { showValue: true, position: 'outEnd', numberFormatCode: '0.0" %"', textStyle: { typeface: font, fontSize: 26 } },
        chartFill: colors.paper, plotAreaFill: colors.paper,
      });
      applyPresentationChartFont(chart, { fontFamily: font, availableFonts: [font] });
      text(slide, 'chart-explanation', d.body, 815, 205, 392, 385, 28, colors.ink);
    } else if (d.type === 'closing') {
      text(slide, 'decision', d.statement, 66, 185, 1140, 185, 57, '#FFFFFF', true);
      text(slide, 'decision-body', d.body, 70, 410, 1100, 210, 29, colors.light);
    }
    if (d.foot) text(slide, 'scope-note', d.foot, 66, 636, 1100, 60, 18, colors.muted);
    text(slide, 'page-number', String(index + 1).padStart(2, '0'), 1220, 670, 45, 24, 17, dark ? '#B9D4D2' : colors.muted);
  }
  const citations = d.sources.map(s => `- ${s}\n  https://github.com/hbhassen/dev-ai-organisation/blob/${data.sourceCommit}/${s}`).join('\n');
  slide.speakerNotes.text = `${d.notes}\n\n${d.sources.length ? 'Sources du dépôt, état inspecté :\n' + citations : 'Proposition de pilote et critères à valider avec l’organisation.'}`;
}

const candidate = path.join(build, `candidate-${revision}.pptx`);
await (await PresentationFile.exportPptx(p)).save(candidate);
console.log(`Draft: ${data.slides.length} slides`);
const final = await finalizePresentation({
  workspaceDir: repository, candidatePath: candidate, finalPath: output,
  pythonExecutable: python,
  integrityValidatorPath: path.join(skillDir, 'container_tools/inspect_presentation_package_integrity.py'),
  layoutValidatorPath: path.join(skillDir, 'container_tools/inspect_presentation_layout_geometry.py'),
  layoutArgs: ['--expected-slide-size-emu', '12192000,6858000', '--validate-bullet-geometry', '--validate-heading-fit', ...data.slides.flatMap((s, i) => s.type === 'table' ? ['--require-native-table-slide', String(i + 1)] : [])],
  explicitTotalSlideCount: data.slides.length,
  requiredNativeTableOwnerSlides: data.slides.flatMap((s, i) => s.type === 'table' ? [i + 1] : []),
  requiredNativeChartOwnerSlides: [19],
  materializeLiteralChartWorkbooks: true,
  nativeChartTargetApplication: 'powerpoint',
  fontPolicy: { basis: 'design', families: [font] },
  verifyArtifactToolImport: true,
  receiptPath: path.join(build, `validation-${revision}.json`),
});
console.log('Finalized', output);
// Render the finalized file so that QA covers the actual delivered package.
const { FileBlob } = await import(pathToFileURL(requireRuntime.resolve('@oai/artifact-tool')).href);
const rendered = await PresentationFile.importPptx(await FileBlob.load(output));
for (const [i, s] of rendered.slides.items.entries()) {
  const image = await rendered.export({ slide: s, format: 'png', scale: 1 });
  await fs.writeFile(path.join(build, 'renders', `${String(i + 1).padStart(2, '0')}.png`), new Uint8Array(await image.arrayBuffer()));
  console.log(`Rendered ${i + 1}/${data.slides.length}`);
}
const notes = data.slides.map((s, i) => `## ${i + 1}. ${s.title.replaceAll('\n', ' ')}\n\n${s.notes}\n\nSources : ${s.sources.length ? s.sources.map(x => `[${path.basename(x)}](../${x})`).join(', ') : 'Proposition pour le pilote.'}`).join('\n\n');
await fs.writeFile(path.join(here, 'NOTES_ORATEUR.md'), `# Notes de présentation ODS\n\nÉdition ${data.edition}, ${data.date}. 24 diapositives principales et 6 annexes. Les mêmes notes figurent dans le PowerPoint. Les sources renvoient à l’état du dépôt étudié, sans relancer les validations historiques.\n\n${notes}\n`, 'utf8');
console.log('Notes written. Copy the finalized PPTX to demo after visual QA.');
