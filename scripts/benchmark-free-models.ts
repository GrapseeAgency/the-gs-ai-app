/** Opt-in live provider benchmark. Exact concrete model, no auto router,
 * no paid fallback, no credentials or prompt transcripts in artifacts. */
import { writeFileSync } from 'node:fs'
import { REMOTE_TOOLS, collectRemoteToolEvidence, type RemoteCompletion, type RemoteMessage } from '../src/lib/remote-tools'
const key = process.env.OPENROUTER_API_KEY
const candidates = (process.env.GS_BENCH_MODELS || 'nvidia/nemotron-3-ultra-550b-a55b:free').split(',').map(s=>s.trim()).filter(Boolean)
const models = [...new Set(candidates)].slice(0,3)
if (!models.length || models.some(m=>!/^[\w.\/-]+:free$/.test(m))) throw Error('Only explicit free models are permitted')
const trials = 2
const cases = [
  {id:'arithmetic',prompt:'There are 31 boxes of 26 items, with 79 items lost and 14 defective. Return only the number of usable items.',expected:/^713$/},
  {id:'reasoning',prompt:'A and B complete a job in 8 and 24 hours separately. Together how many hours? Return only the number.',expected:/^6(?:\.0+)?$/},
  {id:'format',prompt:'Return only these two lines, no punctuation: first line Kappa6, second line Delta4.',expected:/^Kappa6\r?\nDelta4$/},
  {id:'bengali',prompt:'শুধু লিখুন: সবুজ পাতা',expected:/^সবুজ পাতা$/},
  {id:'correction',prompt:'Earlier provisional code was RQ-310. Correction: approved code is DX-729. Return only the approved code.',expected:/^DX-729$/},
]
const results: {model:string;caseId:string;trial:number;status:string;latencyMs:number;reason?:string}[]=[]
async function request(model:string,messages:RemoteMessage[],signal:AbortSignal,toolMode=false):Promise<RemoteCompletion>{
  if(!key)throw Error('credentials_unconfigured')
  const response=await fetch('https://openrouter.ai/api/v1/chat/completions',{
    method:'POST',signal,headers:{Authorization:`Bearer ${key}`,'Content-Type':'application/json'},
    body:JSON.stringify({model,messages,temperature:0.2,stream:false,max_tokens:1024,
      ...(toolMode?{tools:REMOTE_TOOLS,tool_choice:'auto',provider:{require_parameters:true}}:{})})
  })
  if(!response.ok){void response.body?.cancel();throw Error('http_'+response.status)}
  const data=await response.json() as RemoteCompletion
  if(data.model!==model)throw Error('model_identity_mismatch')
  return data
}
if(!key){
  writeFileSync('free-model-benchmark.json',JSON.stringify({status:'blocked',reason:'OPENROUTER_API_KEY not configured in validation environment',results:[]},null,2))
  console.log('Live benchmark blocked: no configured credential.')
  process.exit(2)
}
for(const model of models)for(let trial=1;trial<=trials;trial++){
  for(const c of cases){
    const start=Date.now()
    try{
      const data=await request(model,[{role:'user',content:c.prompt}],AbortSignal.timeout(35000))
      const choice=data.choices?.[0]
      const text=choice?.message?.content?.trim()
      results.push({model,caseId:c.id,trial,status:choice?.finish_reason==='stop'&&text&&c.expected.test(text)?'PASS':'FAIL',latencyMs:Date.now()-start})
    }catch(e){results.push({model,caseId:c.id,trial,status:'ERROR',latencyMs:Date.now()-start,reason:e instanceof Error&&/^(http_\d+|model_identity_mismatch)$/.test(e.message)?e.message:'transport_error'})}
  }
  const start=Date.now()
  const outcome=await collectRemoteToolEvidence([{role:'user',content:'Use the calculator tool to compute 173*29+11; do not estimate.'}],{
    request:(messages,signal)=>request(model,messages,signal,true),clock:()=>null
  },{timeoutMs:35000})
  const verified=outcome.results.some(r=>r.tool==='calculator'&&r.ok&&(r.value as {result?:number})?.result===5028)
  results.push({model,caseId:'native_tool',trial,status:verified?'PASS':outcome.status==='unavailable'?'ERROR':'FAIL',latencyMs:Date.now()-start})
}
writeFileSync('free-model-benchmark.json',JSON.stringify({status:'executed',trials,results},null,2))
console.log(JSON.stringify({pass:results.filter(r=>r.status==='PASS').length,fail:results.filter(r=>r.status==='FAIL').length,error:results.filter(r=>r.status==='ERROR').length,total:results.length}))
if(results.some(r=>r.status!=='PASS'))process.exitCode=1
