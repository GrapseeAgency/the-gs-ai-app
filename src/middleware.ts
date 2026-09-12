import { NextRequest, NextResponse } from 'next/server'

// TEMP-DIAG: reveal the public origin the platform edge presents on browser
// traffic. Removed after the upload-transport fix ships.
export function middleware(req: NextRequest) {
  console.log(
    `[mw-diag] path=${req.nextUrl.pathname} host=${req.headers.get('host')} xfh=${req.headers.get('x-forwarded-host')} proto=${req.headers.get('x-forwarded-proto')}`
  )
  return NextResponse.next()
}

export const config = {
  matcher: ['/'],
}
