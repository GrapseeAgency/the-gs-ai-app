import { deriveTitle, DEFAULT_CHAT_TITLE, TITLE_LIMIT } from '../src/lib/conversation-title'

/**
 * The headline's own tests, run with `bun scripts/test-conversation-title.ts`.
 *
 * Every case here is a real shape a first message takes in this app: a code
 * block, a bare URL, an attachment-only turn, a two-line instruction, and text
 * that has to be cut on a word boundary.
 */

let failures = 0
function check(name: string, got: string, want: string) {
  if (got === want) {
    console.log(`  ok   ${name}`)
    return
  }
  failures += 1
  console.error(`  FAIL ${name}\n       got:  ${JSON.stringify(got)}\n       want: ${JSON.stringify(want)}`)
}

function invariant(name: string, condition: boolean, detail = '') {
  if (condition) {
    console.log(`  ok   ${name}`)
    return
  }
  failures += 1
  console.error(`  FAIL ${name} ${detail}`)
}

console.log('deriveTitle')

check('plain sentence is kept verbatim',
  deriveTitle({ content: 'Plan my tomorrow' }),
  'Plan my tomorrow')

check('a code fence is not the subject',
  deriveTitle({ content: '```python\nimport pandas as pd\ndf = pd.read_csv("x.csv")\n```\n' }),
  DEFAULT_CHAT_TITLE)

check('text before a fence survives, the fence does not',
  deriveTitle({ content: 'Fix this loop\n```ts\nfor(;;){}\n```' }),
  'Fix this loop')

check('an unterminated fence does not swallow the whole message as a title',
  deriveTitle({ content: 'Why does this hang\n```ts\nwhile(true){}' }),
  'Why does this hang')

check('a bare URL is not a headline',
  deriveTitle({ content: 'https://example.com/a/very/long/path?with=query' }),
  DEFAULT_CHAT_TITLE)

check('inline code becomes words',
  deriveTitle({ content: 'Explain `useEffect` cleanup' }),
  'Explain useEffect cleanup')

check('markdown emphasis and headings are stripped',
  deriveTitle({ content: '## **Weekend** in _Kyoto_' }),
  'Weekend in Kyoto')

check('list bullets and numbers do not lead the title',
  deriveTitle({ content: '- first idea\n- second idea' }),
  'first idea second idea')

check('link label survives, target does not',
  deriveTitle({ content: 'Summarise [the report](https://example.com/x.pdf) please' }),
  'Summarise the report please')

check('newlines collapse to one space',
  deriveTitle({ content: 'Ideas for a\nbrand launch' }),
  'Ideas for a brand launch')

check('an attachment-only turn takes the file name',
  deriveTitle({ content: '[image: IMG_4021.HEIC]', attachmentName: 'IMG_4021.HEIC' }),
  'IMG_4021.HEIC')

check('nothing at all is the default',
  deriveTitle({ content: '   \n  ' }),
  DEFAULT_CHAT_TITLE)

const long = 'I need a detailed comparison of three laptop models for video editing under two thousand dollars'
const cut = deriveTitle({ content: long })
invariant('a long message is cut short', cut.length <= TITLE_LIMIT + 1, `(${cut.length})`)
// A word-boundary cut means: everything before the ellipsis is a prefix of the
// source, and the next source character is a space. The first version of this
// assertion tested the regex /\s\S*…$/ — which matches exactly the CORRECT output,
// so it failed a right answer. The check is about the source, not about shapes.
const head = cut.slice(0, -1)
invariant('a long message ends with an ellipsis', cut.endsWith('…'), cut)
invariant('the cut is a prefix of the source', long.startsWith(head), cut)
invariant('the cut stops at a space, not mid-word', long[head.length] === ' ', cut)

const oneToken = deriveTitle({ content: 'a'.repeat(90) })
invariant('a single long token is not cut to a stub', oneToken.length >= TITLE_LIMIT, `(${oneToken.length})`)

check('cjk is counted as characters, not bytes',
  deriveTitle({ content: '量子力学の基礎を説明して' }),
  '量子力学の基礎を説明して')

check('emoji survive',
  deriveTitle({ content: '🎨 design sprint for friday' }),
  '🎨 design sprint for friday')

if (failures > 0) {
  console.error(`\n${failures} title case(s) failed`)
  process.exit(1)
}
console.log('\nall title cases pass')
