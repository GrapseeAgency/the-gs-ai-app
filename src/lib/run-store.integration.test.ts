import { afterAll, expect, test } from 'bun:test'
import { db } from './db'
import { claimRun, createRun, finishRun, getRun, persistRunAssistant, requeueRun } from './run-store'

if (process.env.GS_RUN_STORE_TEST !== '1') throw new Error('Run this integration test only against an isolated database')
const conv = 'run-store-test-' + crypto.randomUUID()
const id = conv + '-run'
const input = {conversationId:conv,role:'assistant',content:'canonical answer'}
afterAll(async()=>{
  await db.turnEvent.deleteMany({where:{runId:id}})
  await db.turnRun.deleteMany({where:{id}})
  await db.conversation.deleteMany({where:{id:conv}})
  await db.$disconnect()
})
test('actual SQLite: atomic admission, exclusive claims, fenced retries and answer link', async()=>{
  await db.conversation.create({data:{id:conv,title:'isolated run test'}})
  const user = await db.message.create({data:{conversationId:conv,role:'user',content:'question'}})
  await createRun({runId:id,streamId:id+'-stream',conversationId:conv,userMessageId:user.id,startFacts:{content:'question',mode:'thinking'}})
  const started=await db.turnEvent.findFirst({where:{runId:id,eventType:'TurnStarted'}})
  expect(JSON.parse(started!.payload).mode).toBe('thinking')
  const claims=await Promise.all(Array.from({length:8},()=>claimRun(id)))
  expect(claims.filter(Boolean)).toHaveLength(1)
  expect((await getRun(id))?.attempts).toBe(1)
  await db.turnRun.update({where:{id},data:{leaseExpiresAt:new Date(0)}})
  const second=await claimRun(id)
  expect(second?.attempts).toBe(2)
  expect(await finishRun(id,'completed','done',null,null,1)).toBe(false)
  await expect(persistRunAssistant(id,1,input)).rejects.toThrow('lease lost')
  expect(await requeueRun(id,'provider rate limited',2,3600000)).toBe(true)
  const delayed=await getRun(id)
  expect(delayed?.retryNotBefore?.getTime()).toBeGreaterThan(Date.now()+3590000)
  expect(await claimRun(id)).toBeNull()
  await db.turnRun.update({where:{id},data:{retryNotBefore:new Date(0)}})
  const third=await claimRun(id)
  expect(third?.attempts).toBe(3)
  const saved=await persistRunAssistant(id,3,input,[
    {ordinal:1,title:'Source fixture',url:'https://example.com/source',domain:'example.com',query:'fixture',used:true}
  ])
  expect((await getRun(id))?.assistantMessageId).toBe(saved.id)
  expect(await db.messageSource.count({where:{messageId:saved.id}})).toBe(1)
  const recovered=await persistRunAssistant(id,3,{...input,content:'duplicate'})
  expect(recovered.id).toBe(saved.id)
  expect(recovered.content).toBe(input.content)
  expect(await db.message.count({where:{conversationId:conv,role:'assistant'}})).toBe(1)
  expect(await finishRun(id,'completed','done',null,saved.id,3)).toBe(true)
  expect(await claimRun(id)).toBeNull()
})
