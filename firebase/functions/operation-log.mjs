/** A log record safe to print. It never includes request bodies, tokens, or secrets. */
export function operationFailure(operation, error) {
  const code = typeof error?.code === 'string' && error.code ? error.code.slice(0, 80) : 'unknown';
  const message = typeof error?.message === 'string' && error.message ? error.message.slice(0, 300) : 'Unknown failure';
  return { event: 'littlepages.operation_failed', operation: String(operation).slice(0, 80), code, message };
}
