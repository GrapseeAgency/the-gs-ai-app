import { NextRequest, NextResponse } from 'next/server'

export const dynamic = 'force-dynamic'

export async function GET(req: NextRequest) {
  // TEMP-DIAG: capture the public origin the platform edge presents (one-shot).
  console.log(
    `[diag] host=${req.headers.get('host')} xfh=${req.headers.get('x-forwarded-host')} proto=${req.headers.get('x-forwarded-proto')}`
  )
  return NextResponse.json({ status: 'ok' })
}
