/** Add a narrowly scoped retry instruction without removing platform,
 * assistant or conversational instructions, evidence or the original ask. */
export function appendSystemInstruction<T extends {role:string}>(messages: readonly T[], instruction: string): T[] {
  const first = messages.findIndex(m => m.role === 'system')
  if (first >= 0) {
    const original = (messages[first] as T & {content?: unknown}).content
    if (typeof original === 'string') return messages.map((message,index) =>
      index === first ? {...message,content: original + '\n\n' + instruction} : message)
  }
  return [{role:'system',content:instruction} as unknown as T,...messages]
}
