"""Combine the completed Cucumber run and native Surefire run without changing results."""
import argparse
import html
import json
import re
import xml.etree.ElementTree as ET
from collections import Counter
from datetime import datetime
from pathlib import Path
from zoneinfo import ZoneInfo

parser = argparse.ArgumentParser()
parser.add_argument('--reports', default='reports')
args = parser.parse_args()
reports = Path(args.reports)
project = Path(__file__).resolve().parent.parent
rows = []
for feature in json.loads((reports / 'cucumber.json').read_text(encoding='utf-8')):
    for scenario in feature.get('elements', []):
        if scenario.get('type') == 'background':
            continue
        steps = scenario.get('steps', [])
        statuses = [s.get('result', {}).get('status', 'undefined') for s in steps]
        state = 'failed' if any(s not in ('passed', 'skipped') for s in statuses) else 'skipped' if 'skipped' in statuses else 'passed'
        rows.append(dict(kind='Cucumber', group=feature['name'], name=scenario['name'], status=state,
                         location=f"{feature.get('uri', '')}:{scenario.get('line', '')}",
                         steps=[dict(name=s.get('keyword', '') + s.get('name', ''),
                                     status=s.get('result', {}).get('status', 'undefined'),
                                     error=s.get('result', {}).get('error_message', '')) for s in steps]))

cases = json.loads((project / 'data/input-cases.json').read_text(encoding='utf-8'))
normalized = [c for c in cases if c['entity'] == 'shopping' and c['valid']]
native_files = sorted((reports / 'backend-unit').glob('TEST-*.xml'))
if not rows or not native_files:
    raise SystemExit('Cucumber- oder native JUnit-Ergebnisse fehlen; kein unvollständiger Gesamtbericht erzeugt.')
for path in native_files:
    for case in ET.parse(path).getroot().iter('testcase'):
        failure, error, skipped = case.find('failure'), case.find('error'), case.find('skipped')
        state = 'failed' if failure is not None or error is not None else 'skipped' if skipped is not None else 'passed'
        raw_name = case.get('name', '')
        index = re.search(r'\[(\d+)\]$', raw_name)
        label = ''
        if index:
            source = normalized if raw_name.startswith('normalizes') else cases
            label = source[int(index[1]) - 1]['id']
        rows.append(dict(kind='JUnit', group=case.get('classname'), name=label or raw_name,
                         status=state, location=raw_name,
                         steps=[dict(name=raw_name, status=state, error=''.join(e.text or '' for e in (failure, error) if e is not None))]))

now = datetime.now(ZoneInfo('Europe/Berlin')).isoformat(timespec='seconds')
counts = Counter(r['status'] for r in rows)
summary = dict(generatedAt=now, total=len(rows), counts=dict(counts),
               groups={kind: dict(Counter(r['status'] for r in rows if r['kind'] == kind)) for kind in ('Cucumber', 'JUnit')})
(reports / 'all-results.json').write_text(json.dumps(dict(summary=summary, tests=rows), ensure_ascii=False, indent=2), encoding='utf-8')

merged = ET.Element('testsuites', tests=str(len(rows)), failures=str(counts['failed']), skipped=str(counts['skipped']))
for path in [reports / 'junit.xml', *native_files]:
    source = ET.parse(path).getroot()
    for suite in ([source] if source.tag == 'testsuite' else list(source)):
        merged.append(suite)
ET.ElementTree(merged).write(reports / 'all-junit.xml', encoding='utf-8', xml_declaration=True)

esc = lambda value: html.escape(str(value or ''))
labels = dict(passed='Bestanden', failed='Fehlgeschlagen', skipped='Übersprungen')
cards = ''.join(f'<tr><td>{esc(kind)}</td><td>{sum(r["kind"] == kind for r in rows)}</td><td>{sum(r["kind"] == kind and r["status"] == "passed" for r in rows)}</td><td>{sum(r["kind"] == kind and r["status"] == "failed" for r in rows)}</td></tr>' for kind in ('Cucumber', 'JUnit'))
body = []
for index, row in enumerate(rows, 1):
    steps_html = ''.join(f'<li><strong>{esc(step["status"])}</strong> {esc(step["name"])}' + (f'<pre>{esc(re.sub(chr(27) + r"\[[0-9;]*m", "", step["error"]))}</pre>' if step['error'] else '') + '</li>' for step in row['steps'])
    body.append(f'<article data-status="{row["status"]}"><details><summary><span class="badge {row["status"]}">{labels[row["status"]]}</span> {index}. {esc(row["kind"])} · {esc(row["name"])}</summary><p>{esc(row["group"])} · {esc(row["location"])}</p><ol>{steps_html}</ol></details></article>')
page = '''<!doctype html><html lang="de"><meta charset="utf-8"><meta name="viewport" content="width=device-width,initial-scale=1"><title>FamilyHub – vollständige Regression</title>
<style>body{font:16px/1.5 system-ui;margin:30px auto;max-width:1200px;padding:20px;color:#182333;background:#f6f8fb}h1{font-size:30px}a{color:#064ea0}table{border-collapse:collapse;background:white;width:100%;margin:20px 0}td,th{padding:12px;text-align:left;border-bottom:1px solid #ccc}article{background:white;margin:8px 0;padding:12px;border:1px solid #cbd2da;border-radius:6px}summary{cursor:pointer}.badge{display:inline-block;min-width:115px;font-weight:bold}.passed{color:#126134}.failed{color:#a31919}.skipped{color:#725600}pre{white-space:pre-wrap;overflow-wrap:anywhere;background:#f1f3f6;padding:12px}input,select{font:inherit;padding:10px;margin:8px}input{width:300px;max-width:80%}</style>
<h1>FamilyHub – vollständige Regression</h1>
<p>GENERATED · Sämtliche Cucumber-Szenarien und native Backendtests, ohne Wiederholungen oder ausgeblendete Fehler.</p>
<h2>TOTAL Testfälle: PASSED bestanden · FAILED fehlgeschlagen · SKIPPED übersprungen</h2>
<table><caption>Ergebnisse nach Testebene</caption><tr><th>Testebene</th><th>Gesamt</th><th>Bestanden</th><th>Fehlgeschlagen</th></tr>CARDS</table>
<p><a href="cucumber.html">Cucumber mit Screenshots</a> · <a href="audit/index.html">Injection-/Barrierefreiheitsdetails</a> · <a href="all-junit.xml">Gesamter JUnit-Export</a> · <a href="all-results.json">Alle Fälle als JSON</a></p>
<p>Offene Fehler bleiben sichtbar. Automatische axe-Tests ersetzen keine vollständige manuelle WCAG-Prüfung.</p>
<label for="status">Ergebnis:</label><select id="status"><option value="all">Alle</option><option value="failed">Fehlgeschlagen</option><option value="passed">Bestanden</option><option value="skipped">Übersprungen</option></select>
<label for="query">Suche:</label><input id="query" type="search" placeholder="Testname, Bereich oder Eingabe"><p id="visible" aria-live="polite"></p>
ROWS
<script>function filter(){let n=0;const s=document.querySelector('#status').value,q=document.querySelector('#query').value.toLowerCase();document.querySelectorAll('article').forEach(e=>{e.hidden=!((s==='all'||e.dataset.status===s)&&e.textContent.toLowerCase().includes(q));if(!e.hidden)n++});document.querySelector('#visible').textContent=n+' Testfälle angezeigt'}document.querySelector('#status').addEventListener('change',filter);document.querySelector('#query').addEventListener('input',filter);filter();</script></html>'''
for key, value in dict(GENERATED=esc(now), TOTAL=len(rows), PASSED=counts['passed'], FAILED=counts['failed'], SKIPPED=counts['skipped'], CARDS=cards, ROWS=''.join(body)).items():
    page = page.replace(key, str(value))
(reports / 'index.html').write_text(page, encoding='utf-8')
print(json.dumps(summary, ensure_ascii=False))
