import { afterAll, expect, test } from 'bun:test'
import { db } from './db'
import { loadConversationArchive } from './conversation-archive'
import { projectHistory } from './context-crystallize'

const enabled=process.env.GS_RUN_STORE_TEST==='1'
const isolated=enabled?test:test.skip
const conv='archive-'+crypto.randomUUID(),other=conv+'-other'
if(enabled)afterAll(async()=>{
  await db.conversation.deleteMany({where:{id:{in:[conv,other]}}})
  await db.$disconnect()
})
isolated('real SQLite retrieval beyond sixty messages is scoped, bounded and chronological',async()=>{
  await db.conversation.createMany({data:[{id:conv,title:'Archive regression'},{id:other,title:'Isolation regression'}]})
  const start=Date.now()-1000000
  const at=(n:number)=>new Date(start+n*1000)
  await db.message.createMany({data:[
    {id:conv+'-rule',conversationId:conv,role:'user',content:'For this conversation, always answer in Bengali.',createdAt:at(0)},
    {id:conv+'-old',conversationId:conv,role:'assistant',content:'The confirmed project reference is LUMEN8831.',createdAt:at(1)},
    ...Array.from({length:180},(_,i)=>({id:conv+'-filler-'+i,conversationId:conv,role:i%2?'assistant':'user',content:'Unrelated notebook discussion '+i,createdAt:at(i+2)})),
    {id:conv+'-current',conversationId:conv,role:'user',content:'What is the project reference LUMEN8831?',createdAt:at(182)},
    {id:conv+'-future',conversationId:conv,role:'assistant',content:'Future LUMEN8831 must not appear.',createdAt:at(183)},
    {id:other+'-foreign',conversationId:other,role:'assistant',content:'Foreign LUMEN8831 must not appear.',createdAt:at(1)},
  ]})
  const recent=(await db.message.findMany({where:{conversationId:conv,id:{not:conv+'-current'},createdAt:{lte:at(182)}},
    orderBy:{createdAt:'desc'},take:60,select:{id:true,role:true,content:true}})).reverse()
  const archive=await loadConversationArchive({conversationId:conv,before:at(182),currentMessageId:conv+'-current',
    recentIds:recent.map(r=>r.id),question:'What is the project reference LUMEN8831?'})
  expect(archive.rows.length).toBeLessThanOrEqual(52)
  expect(archive.omitted).toBeGreaterThan(0)
  expect(archive.rows.some(r=>r.id===conv+'-rule')).toBe(true)
  expect(archive.rows.some(r=>r.id===conv+'-old')).toBe(true)
  expect(archive.rows.some(r=>r.id.endsWith('-future')||r.id.endsWith('-foreign'))).toBe(false)
  expect(archive.rows.some(r=>recent.some(m=>m.id===r.id))).toBe(false)
  const result=projectHistory(recent,'What is the project reference LUMEN8831?',[],archive)
  expect(result.keepFromIndex).toBe(0)
  expect(result.block).toContain('always answer in Bengali')
  expect(result.block).toContain('The confirmed project reference is LUMEN8831.')
  expect(result.block!.length).toBeLessThan(12500)
})
