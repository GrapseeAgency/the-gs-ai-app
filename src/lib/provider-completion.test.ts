import {expect,test} from 'bun:test'
import {assertTextCompletionEnvelope,ProviderCompletionError} from './provider-completion'
test('explicit truncated, filtered, tool-only and error completions cannot masquerade as successful text',()=>{
  for(const reason of ['length','content_filter','error','tool_calls','function_call']){
    expect(()=>assertTextCompletionEnvelope({choices:[{message:{content:'partial'},finish_reason:reason}]})).toThrow(ProviderCompletionError)
  }
  expect(()=>assertTextCompletionEnvelope({error:{message:'private upstream payload'},choices:[{message:{content:'partial'}}]})).toThrow('provider_error')
  expect(()=>assertTextCompletionEnvelope({choices:[{message:{content:'partial',tool_calls:[{}]}}]})).toThrow('tool_call')
})
test('normal and legacy envelopes preserve compatibility',()=>{
  for(const choice of [{finish_reason:'stop',message:{content:'answer'}},{message:{content:'answer'}},
    {finish_reason:'stop',message:{content:'answer',tool_calls:[]}}])
    expect(()=>assertTextCompletionEnvelope({choices:[choice]})).not.toThrow()
})
