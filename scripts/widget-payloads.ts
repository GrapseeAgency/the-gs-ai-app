/**
 * Shared payload table: one representative, VALID payload per declared
 * sub-type. Used by scripts/test-widget-catalogue.ts (validate + fence route)
 * and scripts/test-widget-render.ts (actually render every one of them).
 */

export const series = '{"name":"s","data":[1,2,3]}'
export const twoNodes = '"nodes":[{"label":"a","x":0.3,"y":0.3},{"label":"b","x":0.7,"y":0.6}],"edges":[[0,1]]'

export const chartPayload = (t: string): string => {
  if (t === 'sankey') return `{"type":"sankey","nodes":["a","b"],"links":[{"source":"a","target":"b","value":3}]}`
  if (t === 'network' || t === 'force')
    return `{"type":"${t}","nodes":[{"id":"a"},{"id":"b"}],"edges":[{"source":"a","target":"b","weight":2}]}`
  if (t === 'wordcloud') return `{"type":"wordcloud","items":[{"text":"gs","weight":9},{"text":"ai","weight":4}]}`
  if (t === 'sunburst') return `{"type":"sunburst","root":{"label":"r","children":[{"label":"a","value":1}]}}`
  if (t === 'dendrogram') return `{"type":"dendrogram","root":{"label":"r","children":[{"label":"a","distance":1},{"label":"b","distance":1}]}}`
  if (t === 'bullet') return `{"type":"bullet","items":[{"label":"k","value":70,"target":100,"max":100,"ranges":[50,80]}]}`
  if (t === 'violin') return `{"type":"violin","series":[{"name":"d","data":[1,2,2,3,3,3,4,5]}]}`
  if (t === 'contour')
    return `{"type":"contour","points":[[0,0,1],[1,0,2],[2,0,1],[0,1,2],[1,1,3],[2,1,2],[0,2,1],[1,2,2],[2,2,1]],"levels":4}`
  if (t === 'parallel') return `{"type":"parallel","axes":["a","b","c"],"series":[{"name":"s","values":[1,2,3]}]}`
  if (t === 'depth') return `{"type":"depth","bids":[[100,1],[99,2]],"asks":[[101,1],[102,2]]}`
  // shape-aware payloads for the charts whose renderers read structured rows —
  // the validator accepts loose shapes, the proof renders REAL geometry
  if (t === 'candlestick')
    return `{"type":"candlestick","labels":["mon","tue"],"series":[{"name":"px","data":[[10,12,9,11],[11,13,10,12.5]]}]}`
  if (t === 'boxplot')
    return `{"type":"boxplot","labels":["a","b"],"series":[{"name":"s","data":[[1,2,3,4],[2,3,4,5]]}]}`
  if (t === 'chord')
    return `{"type":"chord","labels":["a","b"],"series":[{"name":"a","data":[[0,2],[2,0]]},{"name":"b","data":[[2,0],[0,2]]}]}`
  if (t === 'scatter' || t === 'bubble')
    return `{"type":"${t}","series":[{"name":"s","data":[[1,2,3],[2,3,5],[3,2,4]]}]}`
  if (t === 'heatmap')
    return `{"type":"heatmap","labels":["x","y"],"series":[{"name":"r1","data":[[0,5],[1,9]]},{"name":"r2","data":[[0,2],[1,7]]}]}`
  if (t === 'correlation')
    return `{"type":"correlation","labels":["a","b","c"],"series":[{"name":"a","data":[[0,1],[1,0.2],[2,0.5]]},{"name":"b","data":[[0,0.2],[1,1],[2,-0.3]]},{"name":"c","data":[[0,0.5],[1,-0.3],[2,1]]}]}`
  if (t === 'quadrant')
    return `{"type":"quadrant","series":[{"name":"s","data":[[1,2],[3,1],[2,3]]}]}`
  return `{"type":"${t}","series":[${series}]}`
}

export const mediaPayload = (t: string): string => {
  if (t === 'code') return `{"type":"code","code":"const x = 1","language":"ts"}`
  return `{"type":"${t}","items":[{"url":"https://x.test/a.jpg","title":"t"}]}`
}

export const calcPayload = (t: string): string =>
  `{"type":"${t}","fields":[{"key":"p","label":"P","value":1,"min":0,"max":9}],"outputs":[{"label":"o","expr":"p*2"}]}`

export const appPayload = (t: string): string => {
  if (t === 'timer') return `{"type":"timer","title":"Focus","items":[{"title":"Deep work","price":25}]}`
  if (t === 'wizard' || t === 'onboarding' || t === 'decision')
    return `{"type":"${t}","steps":[{"title":"a","body":"b"}]}`
  if (t === 'kanban') return `{"type":"kanban","columns":[{"title":"To do","items":[{"title":"a"}]}]}`

  return `{"type":"${t}","items":[{"title":"a","detail":"b"}]}`
}

export const fieldPayload = (t: string): string =>
  `{"title":"f","fields":[{"type":"${t}","label":"L","min":0,"max":9,"value":3,"options":["a","b"]}]}`

export const simPayload = (t: string): string => `{"sim":"${t}","count":10,"seed":1,"gravity":9.8}`

export const stage3dPayload = (t: string): string => {
  if (t === 'surface3d') return `{"view":"surface3d","z":"sin(x)*cos(y)","span":2}`
  return `{"view":"${t}","nodes":[{"label":"a","x":1,"y":0,"z":0},{"label":"b","x":-1,"y":0,"z":0}],"bonds":[[0,1]]}`
}

export const canvasPayload = (t: string): string => {
  if (t === 'whiteboard') return `{"surface":"whiteboard","title":"wb"}`
  return `{"surface":"${t}",${twoNodes}}`
}

export const editorPayload = (t: string): string => {
  if (t === 'sheet') return `{"editor":"sheet","cells":{"A1":"2","A2":"=A1*3"}}`
  if (t === 'pivot') return `{"editor":"pivot","rows":[["Region","Rev"],["Dhaka","420"],["Chattogram","180"]]}`
  if (t === 'nodes') return `{"editor":"nodes","nodes":[{"label":"A","x":0.2,"y":0.4},{"label":"B","x":0.7,"y":0.4}],"edges":[[0,1]]}`
  if (t === 'site') return `{"editor":"site","blocks":[{"type":"hero","text":"H"},{"type":"text","text":"T"}]}`
  if (t === 'css') return `{"editor":"css","layout":{"direction":"row","gap":12}}`
  if (t === 'video') return `{"editor":"video","duration":30,"tracks":[{"name":"A","clips":[{"start":0,"dur":8,"label":"intro"}]}]}`
  return `{"editor":"${t}","title":"audio"}` // audio
}

export const collabPayload = (t: string): string => {
  if (t === 'counter') return `{"surface":"counter","room":"r","label":"n"}`
  return `{"surface":"${t}","room":"r","items":["a","b"]}`
}
