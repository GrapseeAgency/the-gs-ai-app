import {expect,test} from 'bun:test'
import {collectRemoteToolEvidence,remoteToolEvidence,type RemoteCompletion,type RemoteMessage} from './remote-tools'
const call=(id:string,name:string,args:unknown)=>({id,type:'function' as const,function:{name,arguments:JSON.stringify(args)}})
const reply=(...calls:ReturnType<typeof call>[]):RemoteCompletion=>({choices:[{finish_reason:'tool_calls',message:{role:'assistant',content:null,tool_calls:calls}}]})
const done:RemoteCompletion={choices:[{finish_reason:'stop',message:{role:'assistant',content:'draft not visible'}}]}
const input=[{role:'user',content:'Calculate, count, then use the result'}]
test('sequential native tool calls receive matched actual results',async()=>{
  let n=0
  const records:string[]=[]
  const outcome=await collectRemoteToolEvidence(input,{
    request:async messages=>{
      n++
      if(n===1)return reply(call('c1','calculator',{expression:'12*7'}))
      expect(messages.at(-1)?.tool_call_id).toBe(n===2?'c1':'c2')
      if(n===2){expect(messages.at(-1)?.content).toContain('84');return reply(call('c2','text_statistics',{text:'one two'}))}
      return done
    },clock:()=>({}),record:async(stage)=>{records.push(stage)}
  })
  expect(outcome.status).toBe('complete')
  expect(records).toEqual(['requested','completed','requested','completed'])
  expect(remoteToolEvidence(outcome.results)).toContain('"words":2')
  expect(remoteToolEvidence(outcome.results)).not.toContain('draft not visible')
})
test('unknown tools, invalid args and repeat calls never execute',async()=>{
  let n=0
  const outcome=await collectRemoteToolEvidence(input,{request:async()=>++n===1?reply(
    call('a','shell',{command:'ls'}),call('b','calculator',{expression:'1+2',extra:'x'}),
    call('c','calculator',{expression:'1+2'}),call('d','calculator',{expression:'1+2'})
  ):done,clock:()=>({})})
  expect(outcome.results.map(r=>r.ok)).toEqual([false,false,true,false])
})
test('duplicate call IDs fail before execution',async()=>{
  const outcome=await collectRemoteToolEvidence(input,{request:async()=>reply(call('same','calculator',{expression:'1+2'}),call('same','calculator',{expression:'3+4'})),clock:()=>({})})
  expect(outcome.results).toEqual([])
})
test('partial tool evidence survives provider failure',async()=>{
  let n=0
  const outcome=await collectRemoteToolEvidence(input,{request:async()=>{if(++n===1)return reply(call('a','calculator',{expression:'4*5'}));throw Error('offline')},clock:()=>({})})
  expect(outcome.status).toBe('unavailable')
  expect(remoteToolEvidence(outcome.results)).toContain('20')
})
test('cancelled and over-budget calls are not executed',async()=>{
  const cancelled=await collectRemoteToolEvidence(input,{request:async()=>{throw Error('must not call')},clock:()=>({}),stopped:()=>true})
  expect(cancelled.status).toBe('cancelled')
  const limited=await collectRemoteToolEvidence(input,{request:async()=>reply(call('a','calculator',{expression:'2+2'}),call('b','calculator',{expression:'3+3'})),clock:()=>({})},{maxCalls:1})
  expect(limited.results).toEqual([])
})
