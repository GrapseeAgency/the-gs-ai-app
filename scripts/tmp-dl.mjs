import { PrismaClient } from '@prisma/client'
const db = new PrismaClient()
const row = await db.researchLedgerRow.findUnique({ where: { runId: process.argv[2] } })
if (!row) { console.log('NO LEDGER ROW'); process.exit(0) }
const l = JSON.parse(row.ledger)
console.log(`question: ${l.question.slice(0,80)}`)
console.log(`branches: ${l.branches.length}`)
for (const b of l.branches) console.log(`  [${b.status}] ${b.query.slice(0,70)} → ${b.resultSourceIds.length} sources`)
console.log(`claims: ${l.claims.length} | contradictions: ${l.contradictions.length}`)
for (const c of l.contradictions.slice(0,3)) console.log(`  CX: ${c.resolution?.slice(0,100)}`)
console.log(`gaps: ${l.gaps.length} (open: ${l.gaps.filter(g=>g.status==='open').length})`)
for (const g of l.gaps) console.log(`  [${g.status}] ${g.description} attempted=[${g.attemptedQueries.join(' | ').slice(0,60)}]`)
await db.$disconnect()
