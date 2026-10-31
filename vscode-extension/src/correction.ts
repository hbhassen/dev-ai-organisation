/** Evidence retained for one finding from the original provider report. Exigences : DEVAI-090, DEVAI-098. */
export interface CorrectionItem { key: string; title: string; severity: string; path: string; status: 'corrected' | 'partial' | 'unimplemented'; description: string; explanation?: string; initial: any }
/** Downloadable outcome never infers validation from model prose. Exigences : DEVAI-090, DEVAI-098, DEVAI-175. */
export interface CorrectionReport { source: string; branch: string; created: string; initialFingerprint: string; finalFingerprint?: string; initialAnalysis?: string; finalAnalysis?: string; rounds: number; validated: boolean; target?: number; before?: number; after?: number; files: string[]; checkpoints: string[]; items: CorrectionItem[]; newFindings?: any[]; message: string; modelSummary: string; validationLog: string }
/** Matches original findings across line movements without confusing similar rules in different files. Exigences : DEVAI-090, DEVAI-098. */
export function stillPresent(original: any, findings: any[]): boolean {
  return findings.some(item => original.key && item.key === original.key || original.rule && item.rule === original.rule && item.path === original.path && item.message === original.message);
}
/** Refuses production rewrites and common test/coverage bypasses in a coverage-only task. Exigences : DEVAI-090, DEVAI-175. */
export function coverageChange(path: string, before: string, after: string): void {
  const test = /(^|\/)(test|tests|__tests__)\/|\.(test|spec)\.[^/]+$|(^|\/)test_[^/]+\.py$|Test(s)?\.(java|cs)$/.test(path);
  const manifest = /(^|\/)(pom\.xml|package\.json|pyproject\.toml|pytest\.ini|\.coveragerc|sonar-project\.properties)$/.test(path);
  if (!test && !manifest) throw new Error('Couverture : seules les sources de tests et leur configuration peuvent être modifiées');
  if (!after.trim() || before && after.length < before.length * 0.8) throw new Error('Couverture : suppression de tests ou de configuration refusée');
  testIntegrity(before, after);
}
/** Rejects obvious attempts to make a repair pass by disabling checks instead of fixing the code. Exigences : DEVAI-090, DEVAI-098. */
export function testIntegrity(before: string, after: string): void {
  const bypass = /(?:@Disabled|@Ignore|pytest\.mark\.skip(?:if)?\b|\bskip\s*[:=(]|\bskipTests\b|\bmaven\.test\.skip\b|coverage\.exclusions|sonar\.exclusions|pragma:\s*no cover|istanbul ignore|c8 ignore|exclude|omit|test\.skip|describe\.skip)/gi;
  if ((after.match(bypass) ?? []).length > (before.match(bypass) ?? []).length) throw new Error('Désactivation de tests ou ajout d’exclusions refusé');
}
/** Compares measured denominators to reject coverage gained by dropping original source files. Exigences : DEVAI-090, DEVAI-175. */
export function coverageIntact(before: any, after: any): boolean {
  if (!Number.isFinite(after?.coverage) || !Number.isFinite(before?.lines_to_cover) || after.lines_to_cover < before.lines_to_cover) return false;
  return (before.files ?? []).filter((file: any) => file.lines_to_cover > 0).every((file: any) => (after.files ?? []).some((next: any) => next.path === file.path && next.lines_to_cover >= file.lines_to_cover && (next.conditions_to_cover ?? 0) >= (file.conditions_to_cover ?? 0)));
}
