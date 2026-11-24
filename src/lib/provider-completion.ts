/** Validate OpenAI-style non-streaming envelopes before accepting answer
 * text. Legacy SDKs may omit a finish reason; an explicit incomplete finish
 * or tool request is never a successful text response. */
export class ProviderCompletionError extends Error {
  constructor(readonly reason:'invalid'|'provider_error'|'unfinished'|'tool_call'){
    super('Provider completion rejected: '+reason)
    this.name='ProviderCompletionError'
  }
}
function record(value:unknown):Record<string,unknown>|null{
  return value&&typeof value==='object'&&!Array.isArray(value)?value as Record<string,unknown>:null
}
export function assertTextCompletionEnvelope(payload:unknown):void{
  const envelope=record(payload)
  if(!envelope)throw new ProviderCompletionError('invalid')
  if(envelope.error!=null)throw new ProviderCompletionError('provider_error')
  const choice=Array.isArray(envelope.choices)?record(envelope.choices[0]):null
  // Empty choices are handled by the caller's existing capacity policy.
  if(!choice)return
  const message=record(choice.message),delta=record(choice.delta)
  const calls=message?.tool_calls??delta?.tool_calls
  if((Array.isArray(calls)?calls.length>0:calls!=null)||message?.function_call!=null||
    delta?.function_call!=null||choice.finish_reason==='tool_calls'||choice.finish_reason==='function_call')
    throw new ProviderCompletionError('tool_call')
  if(choice.finish_reason!=null&&choice.finish_reason!=='stop')
    throw new ProviderCompletionError('unfinished')
}
