// Copy of compat/cli.mjs in Knobdeck/knobdeck-companion; keep the two in step.
// Command-line front end to driver.mjs, for tests not written in JavaScript (the IntelliJ tests).
//
//   node compat/cli.mjs '<json job>'
//
// job: { "exe": "...", "env": {...}, "steps": [ {"invoke": "run", "inputs": {...}, "childId"?: "..."},
//        {"request": "query", "params": {...}} ], "setup"?: {"exe": "...", "inputs": {...}} }
// Prints one JSON line: { "results": [ {"errors": [...]} | {"value": ...} | {"error": "..."} ], "setup"?: {...} }

import { PluginDriver, runSetup } from "./driver.mjs";

const job = JSON.parse(process.argv[2]);
const out = { results: [] };

if (job.setup) out.setup = await runSetup(job.setup.exe, job.setup.inputs);

if (job.exe) {
  const plugin = await PluginDriver.start(job.exe, job.env ?? {}, { log: (l) => process.stderr.write(l + "\n") });
  try {
    for (const step of job.steps ?? []) {
      if (step.invoke) {
        out.results.push(await plugin.invoke(step.invoke, step.inputs ?? {}, { childId: step.childId }));
      } else {
        try {
          out.results.push({ value: await plugin.request(step.request, step.params ?? {}) });
        } catch (e) {
          out.results.push({ error: e.message });
        }
      }
    }
  } finally {
    await plugin.stop();
  }
}
process.stdout.write(JSON.stringify(out) + "\n");
