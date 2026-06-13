#!/usr/bin/env node
const fs   = require('fs');
const path = require('path');

const [capDir, cache, v, workload, budgetStr] = process.argv.slice(2);
const budget = Number(budgetStr);

let max = 0;

try {
  const prefix = `cap_${cache}_v${v}_${workload}_capacity_`;

  for (const f of fs.readdirSync(capDir)) {
    if (!f.startsWith(prefix) || !f.endsWith('.json')) continue;

    const r        = JSON.parse(fs.readFileSync(path.join(capDir, f), 'utf8'));
    const dropRate = r.iterations > 0 ? r.dropped_iterations / r.iterations : 1;
    const ok       = r.achieved_ratio >= 0.99
                  && r.error_rate <= 0.1
                  && r.p99 > 0
                  && r.p99 <= budget
                  && dropRate < 0.005;

    if (ok && r.target_rps > max) max = r.target_rps;
  }
} catch (_) { /* no files → 0 */ }

process.stdout.write(String(Math.floor(max * 0.8)));
