import { afterAll, expect, test } from 'bun:test'
import { db } from './db'
import { createRun, getRun, selectDueRuns } from './run-store'
import { produceRun } from './turn-producer'
import { prepareTurn } from './turn-executor'
import { parseEntry, readAfter, streamKeyFor, terminalOf } from './stream-broker'

const enabled=process.env.GS_RUN_STORE_TEST==='1'
const isolatedTest=enabled?test:test.skip
const conv='local-turn-'+crypto.randomUUID(), id=conv+'-run', stream=id+'-stream'
const remote=conv+'-remote', remoteStream=remote+'-stream'
if(enabled)afterAll(async()=>{
  await db.streamChunk.deleteMany({where:{streamKey:{in:[streamKeyFor(stream),streamKeyFor(remoteStream)]}}})
  await db.turnEvent.deleteMany({where:{runId:{in:[id,remote]}}})
  await db.turnTrace.deleteMany({where:{requestId:{in:[id,remote]}}})
  await db.turnRun.deleteMany({where:{id:{in:[id,remote]}}})
  await db.conversation.deleteMany({where:{id:conv}})
  await db.$disconnect()
})

isolatedTest('real durable calculator turn completes with no model call or provider capacity',async()=>{
  await db.conversation.create({data:{id:conv,title:'Local calculator regression'}})
  const content='Calculate (1250*0.18)+37'
  const prepared=await prepareTurn({conversationId:conv,content,clientVersion:'web',requestId:id,
    attachmentIds:[],mode:'flash',runId:id},{content,attachments:[]})
  if(prepared.kind!=='ready') throw Error('Preparation failed')
  expect(prepared.prep.localExactCalculation).toBe('(1250*0.18)+37')
  await createRun({runId:remote,streamId:remoteStream,conversationId:conv,
    userMessageId:prepared.prep.userMessage.id,startFacts:{content:'Remote work',attachmentIds:[],mode:'flash'}})
  await createRun({runId:id,streamId:stream,conversationId:conv,userMessageId:prepared.prep.userMessage.id,
    startFacts:{content,attachmentIds:[],mode:'flash',clientVersion:'web'},requiresProvider:false})
  const due=await selectDueRuns(false,100)
  expect(due.some(row=>row.id===id)).toBe(true)
  expect(due.some(row=>row.id===remote)).toBe(false)
  // Rebuild from durable facts, exactly as the queue worker does.
  await produceRun(id)
  const run=await getRun(id)
  expect(run?.status).toBe('completed')
  const answer=await db.message.findUnique({where:{id:run!.assistantMessageId!}})
  expect(answer?.content).toBe('262')
  const events=await db.turnEvent.findMany({where:{runId:id},orderBy:{seq:'asc'}})
  expect(events.some(e=>e.eventType==='LLMCallRequested')).toBe(false)
  expect(events.some(e=>e.eventType==='SearchRequested')).toBe(false)
  expect(events.some(e=>e.eventType==='ToolCallCompleted'&&JSON.parse(e.payload).value===262)).toBe(true)
  const chunks=(await readAfter(stream,'0-0')).map(row=>parseEntry(row.payload).chunk).filter(Boolean)
  expect(chunks.some(c=>c?.event==='delta'&&c.data==='262')).toBe(true)
  expect(chunks.some(c=>c?.event==='done')).toBe(true)
  expect((await terminalOf(stream))?.status).toBe('completed')
},30000)
