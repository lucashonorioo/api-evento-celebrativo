import path from 'node:path';
import { pathToFileURL } from 'node:url';

export * from '../../.ai/review/engineering-review-gate.mjs';
import { runEngineeringReviewGateCli } from '../../.ai/review/engineering-review-gate.mjs';

const isDirectExecution = process.argv[1]
  && pathToFileURL(path.resolve(process.argv[1])).href === import.meta.url;

if (isDirectExecution) await runEngineeringReviewGateCli();
