/** Bounded, conversation-scoped remote archive retrieval. The recent
 * history is still fetched normally with attachments. Older results are
 * text-only; they cannot reactivate stale images or other attachments. */
import { db } from './db'
import type { Prisma } from '@prisma/client'
import { historySearchTerms, PERSISTENT_INSTRUCTION_TERMS, type EarlierHistory } from './context-crystallize'

export async function loadConversationArchive(input:{
  conversationId:string
  before:Date
  currentMessageId:string
  recentIds:readonly string[]
  question:string
}):Promise<EarlierHistory>{
  const base:Prisma.MessageWhereInput={
    conversationId:input.conversationId,
    createdAt:{lte:input.before},
    id:{notIn:[input.currentMessageId,...input.recentIds]},
    role:{in:['user','assistant']},
  }
  const select={id:true,role:true,content:true,createdAt:true} as const
  const newest:Prisma.MessageOrderByWithRelationInput[]=[{createdAt:'desc'},{id:'desc'}]
  const oldest:Prisma.MessageOrderByWithRelationInput[]=[{createdAt:'asc'},{id:'asc'}]
  const terms=historySearchTerms(input.question).slice(0,8)
  const [count,first,latestUsers,instructions,matched]=await Promise.all([
    db.message.count({where:base}),
    db.message.findMany({where:{...base,role:'user'},select,orderBy:oldest,take:4}),
    db.message.findMany({where:{...base,role:'user'},select,orderBy:newest,take:12}),
    db.message.findMany({where:{...base,role:'user',OR:PERSISTENT_INSTRUCTION_TERMS.map(term=>({content:{contains:term}}))},
      select,orderBy:newest,take:12}),
    terms.length ? db.message.findMany({where:{...base,OR:terms.map(term=>({content:{contains:term}}))},
      select,orderBy:newest,take:24}) : Promise.resolve([]),
  ])
  const rows=[...new Map([...first,...latestUsers,...instructions,...matched].map(row=>[row.id,row])).values()]
    .sort((a,b)=>a.createdAt.getTime()-b.createdAt.getTime()||a.id.localeCompare(b.id))
  return {rows:rows.map(({id,role,content})=>({id,role,content})),omitted:Math.max(0,count-rows.length)}
}
