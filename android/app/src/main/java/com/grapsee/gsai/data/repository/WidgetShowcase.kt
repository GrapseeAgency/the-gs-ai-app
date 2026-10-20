package com.grapsee.gsai.data.repository

/**
 * The deterministic model's tour of the rendered component catalogue.
 *
 * The widget renderer is model-agnostic: a fence is a fence whether a cloud
 * model, the on-device llama build, or the offline responder wrote it. The gap
 * was that the offline responder only ever wrote prose. This object teaches it
 * the wire format by EXAMPLE — every page below is a real reply the local
 * responder returns, parsed by the same `parseWidget` path as any server
 * answer.
 *
 * The catalogue is split into pages on purpose. A single message carrying all
 * 195 declared types would take a minute to lay out and would be impossible to
 * read; each page ends with a `followups` widget whose prompts open the next
 * page. The union of the pages covers every kind and every chart type the
 * Android renderer supports.
 */
object WidgetShowcase {

    /** Added to the native model's prompt so a small model can emit fences too. */
    val nativeInstruction: String = """
You are GS, a warm, concise assistant. When a visual would answer better than prose, append ONE fenced block per visual: a triple-backtick fence whose language is the widget kind, then a single JSON object, then a closing fence. Kinds: chart, stat, table, compare, progress, timeline, gauge, quiz, form, carousel, cards, followups, media, actions, panel, voice, calc, app, sim, stage3d, canvas, editor, terminal, collab. Chart types include bar, line, area, pie, donut, scatter, bubble, radar, funnel, heatmap, treemap, waterfall, candlestick, gantt, boxplot and more. Never put markdown inside the JSON. Never invent data: if you do not have values, ask for them or use prose.
""".trim()

    private const val TOUR = "Show a gallery of rendered components"
    private const val CHARTS = "Show the chart catalogue"
    private const val SHAPES = "Show the structural chart catalogue"
    private const val ACTIONS = "Show the form and quiz catalogue"
    private const val APPS = "Show the app catalogue"
    private const val MEDIA = "Show the media catalogue"
    private const val OBJECTS = "Show the 3D and canvas catalogue"

    /** Null when the prompt is not asking for the catalogue. */
    fun replyFor(prompt: String): String? {
        val lower = prompt.lowercase()
        val trigger = "gallery" in lower ||
            "what can you render" in lower ||
            "what can you show" in lower ||
            "show widgets" in lower ||
            "component catalogue" in lower ||
            "component gallery" in lower ||
            "rendered components" in lower ||
            "every component" in lower ||
            "show me everything" in lower ||
            lower.startsWith("tour") ||
            "catalogue" in lower
        if (!trigger) return null
        return when {
            "structural" in lower || "shapes" in lower -> shapesPage()
            "chart" in lower -> chartsPage()
            "3d" in lower || "canvas" in lower || "worlds" in lower || "objects" in lower -> objectsPage()
            "form" in lower || "quiz" in lower || "action" in lower || "sheet" in lower -> actionsPage()
            "app catalogue" in lower || "apps page" in lower || "apps" in lower -> appsPage()
            "media" in lower || "docs" in lower || "image" in lower || "video" in lower -> mediaPage()
            else -> tourPage()
        }
    }

    // -----------------------------------------------------------------------
    // Page 1 — the everyday pieces
    // -----------------------------------------------------------------------

    private fun tourPage(): String = buildString {
        append("Here's the tour. Every block below is a real widget — tap a bar, a slice or a row and it answers.\n\n")
        append(fence("stat", """
{"title":"This week at a glance","cards":[
{"label":"Replies","value":"42","delta":"+8","direction":"up"},
{"label":"Saved","value":"7","delta":"+2","direction":"up"},
{"label":"Open tasks","value":"4","delta":"-1","direction":"down"}]}
"""))
        append("\n")
        append(fence("table", """
{"title":"What each block is for","columns":["Block","Best for","Tap"],"rows":[
["Stat","A few numbers that matter","—"],
["Table","Comparison with many rows","Sort a column"],
["Timeline","A plan or a history","—"],
["Gauge","One value against a limit","—"],
["Cards","Three ways forward","Sends a prompt"]]}
"""))
        append("\n")
        append(fence("compare", """
{"title":"Cloud or on-device","left":{"title":"Provider","items":["Best quality","Needs a connection","Streams as it writes"]},"right":{"title":"On-device","items":["Private by default","Works offline","Smaller model, shorter answers"]}}
"""))
        append("\n")
        append(fence("progress", """
{"title":"Setup","items":[{"label":"Interests","percent":100},{"label":"Voice and tools","percent":100},{"label":"Model installed","percent":35}]}
"""))
        append("\n")
        append(fence("timeline", """
{"title":"A calm launch week","entries":[
{"date":"Mon","title":"Pick one outcome","detail":"Write the sentence that defines success."},
{"date":"Wed","title":"Ship a rough v0","detail":"Real feedback beats another planning session."},
{"date":"Fri","title":"Read the feedback","detail":"Keep the one change that moved the outcome."}]}
"""))
        append("\n")
        append(fence("gauge", """
{"title":"Confidence","items":[{"label":"Evidence","value":72,"max":100,"unit":"%"},{"label":"Assumptions","value":28,"max":100,"unit":"%"}]}
"""))
        append("\n")
        append(fence("cards", """
{"title":"Pick a direction","cards":[
{"title":"Make the charts page","subtitle":"All the chart types, one tap each","prompt":"$CHARTS"},
{"title":"Make the apps page","subtitle":"Boards, forms, calculators and editors","prompt":"$APPS"},
{"title":"Make the media page","subtitle":"Images, docs, search results and files","prompt":"$MEDIA"}]}
"""))
        append("\n")
        append(fence("followups", """
{"title":"Keep going","prompts":["$CHARTS","$ACTIONS","$APPS","$MEDIA","$OBJECTS"]}
"""))
    }

    // -----------------------------------------------------------------------
    // Page 2 — ask, answer, act
    // -----------------------------------------------------------------------

    private fun actionsPage(): String = buildString {
        append("These are the pieces that take an answer. The sheets are real fields; the quiz checks itself; the buttons send, copy or open.\n\n")
        append(fence("form", """
{"title":"A short brief","submitLabel":"Send the brief","fields":[
{"type":"text","label":"What are we making?","placeholder":"A launch note","required":true},
{"type":"textarea","label":"Who is it for?","placeholder":"The team, the customer, the board"},
{"type":"select","label":"Tone","options":["Warm","Direct","Formal"]},
{"type":"segmented","label":"Length","options":["Short","Standard","Detailed"]},
{"type":"slider","label":"Confidence","min":0,"max":100,"value":60,"unit":"%"},
{"type":"date","label":"Needed by"}]}
"""))
        append("\n")
        append(fence("quiz", """
{"title":"A tiny check","questions":[
{"q":"What makes a chart readable?","options":["More colours","One idea and honest axes","A 3D effect"],"answer":1,"explain":"One idea, honest axes, and labels you can read."},
{"q":"When is a table better than a chart?","options":["When exact values matter","Never","When there are two numbers"],"answer":0,"explain":"Tables win when the reader needs the number, not the shape."}]}
"""))
        append("\n")
        append(fence("actions", """
{"title":"Try an action","buttons":[
{"label":"Copy this answer","kind":"copy","value":"One idea, honest axes, labels you can read.","style":"tonal"},
{"label":"Ask for an example","kind":"prompt","value":"Give me a concrete example of a readable chart.","style":"filled"},
{"label":"Open the docs","kind":"open","value":"https://developer.android.com/jetpack/compose","style":"text"}]}
"""))
        append("\n")
        append(fence("panel", """
{"title":"Why this exists","summary":"A widget is data, not a document.","body":"The payload is capped at 64 KB and validated before rendering. A malformed payload degrades to a code block instead of breaking the transcript.","expanded":false,"badges":[{"label":"Safe","tone":"success"},{"label":"Native","tone":"accent"}],"popover":"The same contract runs on Android and iOS."}
"""))
        append("\n")
        append(fence("voice", """
{"title":"Talk it through","prompt":"Help me think out loud about a decision.","hint":"The microphone opens the real speech screen; nothing is simulated."}
"""))
        append("\n")
        append(fence("media", """
{"type":"search","title":"Sourced results","results":[
{"title":"Compose layout basics","url":"https://developer.android.com/develop/ui/compose/layouts/basics","snippet":"Rows, columns, boxes and constraints.","domain":"developer.android.com"},
{"title":"Material 3 in Compose","url":"https://developer.android.com/develop/ui/compose/designsystems/material3","snippet":"Theming, colour roles and dynamic colour.","domain":"developer.android.com"}]}
"""))
        append("\n")
        append(fence("media", """
{"type":"code","title":"A tiny parser guard","language":"kotlin","text":"A failed parse must never take the transcript down.","code":"fun parseOrNull(raw: String): Widget? = runCatching { parseWidget(raw) }.getOrNull()"}
"""))
        append("\n")
        append(fence("followups", """
{"title":"More","prompts":["$CHARTS","$APPS","$MEDIA","$TOUR"]}
"""))
    }

    // -----------------------------------------------------------------------
    // Page 3 — the series charts
    // -----------------------------------------------------------------------

    private fun chartsPage(): String = buildString {
        append("Every chart below is interactive. Tap a bar, a point or a slice for its exact value; tap it again to clear.\n\n")
        append(chartFence("bar", "A week of focus", listOf("Mon", "Tue", "Wed", "Thu", "Fri", "Sat", "Sun"), listOf("Deep work" to listOf(2f to 4f, 3f to 7f, 4f to 5f, 5f to 9f, 6f to 6f, 7f to 3f, 8f to 2f))))
        append("\n")
        append(chartFence("hbar", "Where the hours went", listOf("Writing", "Research", "Meetings", "Admin"), listOf("Hours" to listOf(0f to 8f, 1f to 5f, 2f to 3f, 3f to 2f))))
        append("\n")
        append(chartFence("grouped", "This month against last", listOf("Week 1", "Week 2", "Week 3", "Week 4"), listOf("This month" to listOf(0f to 12f, 1f to 18f, 2f to 15f, 3f to 21f), "Last month" to listOf(0f to 10f, 1f to 14f, 2f to 16f, 3f to 17f))))
        append("\n")
        append(chartFence("stacked", "Where the budget sits", listOf("Q1", "Q2", "Q3", "Q4"), listOf("Product" to listOf(0f to 40f, 1f to 45f, 2f to 52f, 3f to 58f), "Marketing" to listOf(0f to 20f, 1f to 22f, 2f to 18f, 3f to 25f), "Ops" to listOf(0f to 15f, 1f to 16f, 2f to 18f, 3f to 17f))))
        append("\n")
        append(chartFence("line", "Reply time, seconds", listOf("1", "2", "3", "4", "5", "6"), listOf("Median" to listOf(1f to 740f, 2f to 690f, 3f to 710f, 4f to 640f, 5f to 620f, 6f to 600f))))
        append("\n")
        append(chartFence("area", "Active conversations", listOf("Jan", "Feb", "Mar", "Apr", "May"), listOf("Active" to listOf(0f to 12f, 1f to 18f, 2f to 17f, 3f to 24f, 4f to 29f))))
        append("\n")
        append(chartFence("stackedArea", "Where new chats start", listOf("Jan", "Feb", "Mar", "Apr"), listOf("Home" to listOf(0f to 8f, 1f to 11f, 2f to 10f, 3f to 14f), "Drawer" to listOf(0f to 3f, 1f to 4f, 2f to 6f, 3f to 5f), "Widget" to listOf(0f to 1f, 1f to 2f, 2f to 2f, 3f to 4f))))
        append("\n")
        append(fence("chart", pieJson()))
        append("\n")
        append(fence("chart", donutJson()))
        append("\n")
        append(chartFence("scatter", "Cost against latency", listOf("Cost", "Latency"), listOf("Models" to listOf(2f to 12f, 4f to 18f, 6f to 15f, 8f to 26f, 10f to 22f, 12f to 31f))))
        append("\n")
        append(fence("chart", """
{"type":"bubble","title":"Reach and engagement","labels":["Channels"],"series":[{"name":"Channels","data":[[2,14,4],[4,22,7],[6,18,5],[8,30,9],[10,26,6]]}],"unit":""}
"""))
        append("\n")
        append(chartFence("radar", "How the week felt", listOf("Focus", "Energy", "Calm", "Progress", "Rest"), listOf("This week" to listOf(0f to 8f, 1f to 6f, 2f to 7f, 3f to 8f, 4f to 4f))))
        append("\n")
        append(chartFence("funnel", "From idea to shipped", listOf("Read", "Try", "Keep"), listOf("People" to listOf(0f to 100f, 1f to 48f, 2f to 21f))))
        append("\n")
        append(chartFence("histogram", "Reply length", listOf("Short", "Medium", "Long", "Essay"), listOf("Replies" to listOf(0f to 14f, 1f to 22f, 2f to 9f, 3f to 3f))))
        append("\n")
        append(chartFence("boxplot", "Spread of reply times", listOf("Week 1", "Week 2", "Week 3"), listOf(
            "Week 1" to listOf(0f to 420f, 0f to 560f, 0f to 690f, 0f to 820f, 0f to 980f),
            "Week 2" to listOf(0f to 380f, 0f to 510f, 0f to 640f, 0f to 760f, 0f to 900f),
            "Week 3" to listOf(0f to 340f, 0f to 470f, 0f to 590f, 0f to 700f, 0f to 840f)
        )))
        append("\n")
        append(chartFence("waterfall", "From sign-ups to active", listOf("Sign-ups", "Verified", "Onboarded", "Active", "Retained"), listOf("Change" to listOf(0f to 120f, 1f to -18f, 2f to -24f, 3f to -31f, 4f to -12f))))
        append("\n")
        append(chartFence("gantt", "A four-week plan", listOf("Scope", "Build", "Test", "Launch"), listOf("Plan" to listOf(0f to 2f, 2f to 4f, 4f to 5f, 5f to 6f))))
        append("\n")
        append(fence("followups", """
{"title":"More chart types","prompts":["$SHAPES","$APPS","$TOUR"]}
"""))
    }

    // -----------------------------------------------------------------------
    // Page 4 — structural charts
    // -----------------------------------------------------------------------

    private fun shapesPage(): String = buildString {
        append("These charts carry their own shape in the payload. Every one is tappable where the renderer has a datum to reveal.\n\n")
        append(chartFence("heatmap", "When the week is busy", listOf("Mon", "Tue", "Wed", "Thu", "Fri"), listOf(
            "Morning" to listOf(0f to 2f, 1f to 4f, 2f to 6f, 3f to 8f, 4f to 5f),
            "Midday" to listOf(0f to 5f, 1f to 8f, 2f to 9f, 3f to 7f, 4f to 4f),
            "Evening" to listOf(0f to 3f, 1f to 2f, 2f to 4f, 3f to 6f, 4f to 7f)
        )))
        append("\n")
        append(chartFence("treemap", "Where the storage goes", listOf("Models", "Media", "Chats", "Cache"), listOf("Size" to listOf(0f to 48f, 1f to 26f, 2f to 18f, 3f to 8f))))
        append("\n")
        append(chartFence("candlestick", "A week of tokens", listOf("Mon", "Tue", "Wed", "Thu", "Fri"), listOf("Price" to listOf(0f to 12f, 1f to 18f, 2f to 15f, 3f to 19f, 4f to 17f))))
        append("\n")
        append(fence("chart", """
{"type":"sankey","title":"Where prompts go","nodes":[{"id":"Prompts","label":"Prompts"},{"id":"Chat","label":"Chat"},{"id":"Widgets","label":"Widgets"},{"id":"Rendered","label":"Rendered"},{"id":"Prose","label":"Prose"}],"links":[{"source":"Prompts","target":"Chat","value":70},{"source":"Prompts","target":"Widgets","value":30},{"source":"Chat","target":"Prose","value":58},{"source":"Widgets","target":"Rendered","value":26}]}
"""))
        append("\n")
        append(fence("chart", """
{"type":"sunburst","title":"The product tree","root":{"label":"GS","children":[{"label":"Chat","children":[{"label":"Reply","value":60},{"label":"Widget","value":25}]},{"label":"Settings","children":[{"label":"Appearance","value":8},{"label":"Privacy","value":4}]}]}}
"""))
        append("\n")
        append(fence("chart", """
{"type":"wordcloud","title":"What people ask for","items":[{"text":"summarise","weight":38},{"text":"draft","weight":31},{"text":"plan","weight":26},{"text":"explain","weight":22},{"text":"chart","weight":18},{"text":"translate","weight":14},{"text":"fix","weight":12},{"text":"compare","weight":9}]}
"""))
        append("\n")
        append(fence("chart", """
{"type":"bullet","title":"Progress against target","items":[{"label":"Replies kept under 2s","value":78,"max":100,"target":90},{"label":"Widgets rendered","value":64,"max":100,"target":75},{"label":"Docs opened","value":42,"max":100,"target":50}]}
"""))
        append("\n")
        append(fence("chart", """
{"type":"quadrant","title":"Effort against impact","axes":["Effort","Impact"],"series":[{"name":"Ideas","data":[[2,8],[3,4],[5,9],[7,6],[8,2],[4,7]]}]}
"""))
        append("\n")
        append(fence("chart", """
{"type":"chord","title":"Where time moves","labels":["Work","Family","Rest","Study"],"series":[{"name":"Work","data":[[1,4],[2,2],[3,1]]},{"name":"Family","data":[[0,3],[2,3]]},{"name":"Rest","data":[[0,2],[1,2],[3,1]]},{"name":"Study","data":[[0,2]]}]}
"""))
        append("\n")
        append(fence("followups", """
{"title":"Keep going","prompts":["$APPS","$MEDIA","$OBJECTS"]}
"""))
    }

    // -----------------------------------------------------------------------
    // Page 5 — apps, editors, 3D
    // -----------------------------------------------------------------------

    private fun appsPage(): String = buildString {
        append("These are the small applications. They keep their own state while the transcript scrolls, and every control does something.\n\n")
        append(fence("app", """
{"type":"kanban","title":"A calm week","columns":[{"title":"Now","items":[{"title":"Draft the note","subtitle":"20 minutes","badge":"Today"},{"title":"Send the invoice","meta":"Finance"}]},{"title":"Next","items":[{"title":"Book the call","meta":"Thu"}]},{"title":"Done","items":[{"title":"Clean the desk","meta":"Mon"}]}]}
"""))
        append("\n")
        append(fence("app", """
{"type":"wizard","title":"Choose a plan","steps":[{"title":"Scope","body":"Pick the one outcome that matters."},{"title":"Rhythm","body":"Choose how often you want to check in."}],"submitLabel":"Create the plan"}
"""))
        append("\n")
        append(fence("calc", """
{"type":"loan","title":"A simple loan","fields":[{"key":"principal","label":"Amount","value":12000,"unit":"currency","min":100,"max":100000,"step":100},{"key":"rate","label":"Annual rate","value":6.5,"unit":"%","min":0,"max":30,"step":0.1},{"key":"years","label":"Years","value":4,"unit":"years","min":1,"max":30,"step":1}],"outputs":[{"label":"Monthly payment","expr":"principal * (rate/100/12) / (1 - (1 + rate/100/12) ^ (-years*12))","format":"currency","primary":true},{"label":"Total interest","expr":"principal * (rate/100/12) / (1 - (1 + rate/100/12) ^ (-years*12)) * years * 12 - principal","format":"currency"}],"note":"Change any field and the result updates."}
"""))
        append("\n")
        append(fence("calc", """
{"type":"unit","title":"Convert a distance","fields":[{"key":"km","label":"Kilometres","value":5,"unit":"km","min":0,"max":1000,"step":0.1}],"outputs":[{"label":"Miles","expr":"km * 0.621371","unit":"mi","format":"number","primary":true},{"label":"Metres","expr":"km * 1000","unit":"m"}],"note":"One field, two answers."}
"""))
        append("\n")
        append(fence("app", """
{"type":"dashboard","title":"A small dashboard","items":[{"title":"Open tasks","subtitle":"4","meta":"Today"},{"title":"Replies","subtitle":"42","meta":"This week"},{"title":"Saved","subtitle":"7","meta":"All time"}]}
"""))
        append("\n")
        append(fence("editor", """
{"editor":"sheet","title":"A tiny budget","columns":["Item","Cost","Kept"],"rows":[["Rent","1200","1200"],["Food","400","360"],["Travel","150","120"]],"cells":{"0:1":"1200","0:2":"1200","1:1":"400","1:2":"360","2:1":"150","2:2":"120"}}
"""))
        append("\n")
        append(fence("editor", """
{"editor":"video","title":"A four-scene cut","duration":32,"tracks":[{"name":"Video","clips":[{"start":0,"dur":8,"label":"Open"},{"start":8,"dur":10,"label":"Demo"},{"start":18,"dur":14,"label":"Close"}]},{"name":"Music","clips":[{"start":0,"dur":32,"label":"Bed"}]}]}
"""))
        append("\n")
        append(fence("canvas", """
{"surface":"mindmap","title":"A decision map","nodes":[{"label":"Move?","x":0.5,"y":0.18,"group":"core"},{"label":"Cost","x":0.20,"y":0.55},{"label":"Time","x":0.42,"y":0.72},{"label":"People","x":0.68,"y":0.70},{"label":"Joy","x":0.86,"y":0.48}],"edges":[[0,1],[0,2],[0,3],[0,4]]}
"""))
        append("\n")
        append(fence("sim", """
{"sim":"particles","title":"A small world","gravity":9.8,"speed":1,"damping":0.99,"count":48,"size":64,"interactive":true}
"""))
        append("\n")
        append(fence("stage3d", """
{"view":"molecule","title":"Water","nodes":[{"label":"O","x":0,"y":0,"z":0},{"label":"H","x":0.8,"y":0.5,"z":0.2},{"label":"H","x":-0.8,"y":0.5,"z":0.2}],"bonds":[[0,1],[0,2]],"spin":true}
"""))
        append("\n")
        append(fence("terminal", """
{"title":"A tiny console","welcome":"Type help, date, or calc 2 + 2.","commands":[{"name":"help","description":"List commands"},{"name":"date","description":"Today"},{"name":"calc","description":"Evaluate an expression"}]}
"""))
        append("\n")
        append(fence("collab", """
{"surface":"counter","room":"tour-room","title":"A shared counter","label":"Taps","start":0}
"""))
        append("\n")
        append(fence("followups", """
{"title":"More","prompts":["$MEDIA","$CHARTS","$TOUR"]}
"""))
    }

    // -----------------------------------------------------------------------
    // Page 6 — media and documents
    // -----------------------------------------------------------------------

    private fun mediaPage(): String = buildString {
        append("Media blocks are real previews, not thumbnails pretending to work. Files and documents open, links hand off to the system browser.\n\n")
        append(fence("media", """
{"type":"gallery","title":"A small gallery","items":[
{"url":"https://images.unsplash.com/photo-1500530855697-b586d89ba3ee?w=900","title":"A quiet coast","caption":"A place to think"},
{"url":"https://images.unsplash.com/photo-1441974231531-c6227db76b6e?w=900","title":"A green path","caption":"A walk between meetings"},
{"url":"https://images.unsplash.com/photo-1470770841072-f978cf4d019e?w=900","title":"Still water","caption":"The end of the day"}]}
"""))
        append("\n")
        append(fence("media", """
{"type":"video","title":"A short preview","items":[{"url":"https://interactive-examples.mdn.mozilla.net/media/cc0-videos/flower.mp4","thumb":"https://images.unsplash.com/photo-1490750967868-88aa4486c946?w=900","title":"Thirty seconds of spring","caption":"A real inline player"}]}
"""))
        append("\n")
        append(fence("media", """
{"type":"audio","title":"A short listen","items":[{"url":"https://interactive-examples.mdn.mozilla.net/media/cc0-audio/t-rex-roar.mp3","title":"Ambient test tone","caption":"Tap play; the system player takes over."}]}
"""))
        append("\n")
        append(fence("media", """
{"type":"doc","title":"A two-page brief","pages":[{"page":1,"text":"The problem: a reader cannot tell which parts of a long answer are interactive. The goal is one honest, tappable surface."},{"page":2,"text":"The decision: every visual is data with a renderer, validated before it reaches the transcript. A bad payload is a code block, never a crash."}]}
"""))
        append("\n")
        append(fence("media", """
{"type":"file","title":"Files","items":[{"url":"https://developer.android.com/static/images/logo.png","title":"A downloadable reference","subtitle":"PNG · 24 KB","meta":"Download"},{"url":"https://www.w3.org/WAI/ER/tests/xhtml/testfiles/resources/pdf/dummy.pdf","title":"A sample PDF","subtitle":"PDF · 13 KB","meta":"Open"}]}
"""))
        append("\n")
        append(fence("media", """
{"type":"link","title":"Reading list","items":[{"url":"https://developer.android.com/jetpack/compose","title":"Jetpack Compose","subtitle":"The official starting point","domain":"developer.android.com"},{"url":"https://kotlinlang.org/docs/home.html","title":"Kotlin docs","subtitle":"Language and standard library","domain":"kotlinlang.org"}]}
"""))
        append("\n")
        append(fence("media", """
{"type":"citation","title":"Sources","items":[{"title":"Compose layout basics","url":"https://developer.android.com/develop/ui/compose/layouts/basics","domain":"developer.android.com","snippet":"Measure, place and arrange."},{"title":"Material 3 colour roles","url":"https://m3.material.io/styles/color/roles","domain":"m3.material.io","snippet":"Semantic colour, not hex values."}]}
"""))
        append("\n")
        append(fence("media", """
{"type":"product","title":"A product card","items":[{"title":"A warm desk lamp","price":"$48","rating":"4.6","badge":"Editor's pick","prompt":"Compare this lamp with two quieter options under $60.","url":"https://example.com/lamp"}]}
"""))
        append("\n")
        append(fence("media", """
{"type":"entity","title":"An entity","items":[{"title":"Ada Lovelace","subtitle":"Mathematician · 1815–1852","snippet":"Wrote the first published algorithm intended for a machine.","prompt":"Tell me more about Ada Lovelace's notes on the Analytical Engine."}]}
"""))
        append("\n")
        append(fence("followups", """
{"title":"More","prompts":["$OBJECTS","$APPS","$TOUR"]}
"""))
    }

    // -----------------------------------------------------------------------
    // Page 7 — objects: canvas surfaces, simulations, 3D stages
    // -----------------------------------------------------------------------

    private fun objectsPage(): String = buildString {
        append("Spaces, not pictures. Draw, drag, orbit, slosh — each one keeps the state you give it while the transcript scrolls.\n\n")
        append(fence("canvas", """
{"surface":"whiteboard","title":"A shared whiteboard"}
"""))
        append("\n")
        append(fence("canvas", """
{"surface":"mindmap","title":"A launch plan","nodes":[{"label":"Launch","x":0.48,"y":0.16,"group":"core"},{"label":"Audience","x":0.16,"y":0.46},{"label":"Message","x":0.38,"y":0.74},{"label":"Channel","x":0.66,"y":0.72},{"label":"Date","x":0.86,"y":0.44}],"edges":[[0,1],[0,2],[0,3],[0,4]]}
"""))
        append("\n")
        append(fence("canvas", """
{"surface":"diagram","title":"How a turn moves","nodes":[{"label":"Prompt","x":0.10,"y":0.28},{"label":"Route?","x":0.36,"y":0.28},{"label":"Provider","x":0.66,"y":0.12},{"label":"On-device","x":0.66,"y":0.52},{"label":"Widget?","x":0.42,"y":0.80},{"label":"Render","x":0.80,"y":0.80}],"edges":[[0,1],[1,2],[1,3],[2,4],[3,4],[4,5]]}
"""))
        append("\n")
        append(fence("canvas", """
{"surface":"polygons","title":"Sketch a zone"}
"""))
        append("\n")
        append(fence("sim", """
{"sim":"fluid","title":"A tank of water","gravity":9.8,"speed":1,"damping":0.995,"count":72,"size":64,"interactive":true}
"""))
        append("\n")
        append(fence("sim", """
{"sim":"cloth","title":"A hanging cloth","gravity":9.8,"speed":0.8,"damping":0.99,"count":60,"size":64,"interactive":true}
"""))
        append("\n")
        append(fence("sim", """
{"sim":"game","title":"A tiny dodge game","gravity":0,"speed":1.2,"damping":1,"count":24,"size":64,"interactive":true}
"""))
        append("\n")
        append(fence("stage3d", """
{"view":"molecule","title":"Water","nodes":[{"label":"O","x":0,"y":0,"z":0,"r":0.38},{"label":"H","x":0.8,"y":0.5,"z":0.2,"r":0.24},{"label":"H","x":-0.8,"y":0.5,"z":0.2,"r":0.24}],"bonds":[[0,1],[0,2]],"spin":true}
"""))
        append("\n")
        append(fence("stage3d", """
{"view":"surface3d","title":"A quiet hill","z":"sin(x) * cos(y)","span":2.2,"spin":true}
"""))
        append("\n")
        append(fence("stage3d", """
{"view":"scatter3d","title":"Three dimensions of a week","nodes":[{"label":"Mon","x":-1.2,"y":-0.6,"z":0.4,"r":0.26,"group":"work"},{"label":"Tue","x":-0.4,"y":0.8,"z":-0.5,"r":0.26,"group":"rest"},{"label":"Wed","x":0.5,"y":0.2,"z":1.0,"r":0.26,"group":"work"},{"label":"Thu","x":1.1,"y":-0.9,"z":-0.3,"r":0.26,"group":"study"},{"label":"Fri","x":0.2,"y":1.1,"z":0.7,"r":0.26,"group":"rest"}],"bonds":[],"spin":true}
"""))
        append("\n")
        append(fence("stage3d", """
{"view":"cad","title":"A bracket","nodes":[{"label":"A","x":-0.9,"y":-0.5,"z":0,"r":0.22},{"label":"B","x":0.7,"y":-0.5,"z":0,"r":0.22},{"label":"C","x":0.7,"y":0.6,"z":0,"r":0.22},{"label":"D","x":-0.9,"y":0.6,"z":0.5,"r":0.22}],"bonds":[[0,1],[1,2],[2,3],[3,0],[0,2]],"spin":false}
"""))
        append("\n")
        append(fence("followups", """
{"title":"Keep going","prompts":["$APPS","$MEDIA","$TOUR"]}
"""))
    }

    // -----------------------------------------------------------------------
    // Helpers
    // -----------------------------------------------------------------------

    private fun fence(kind: String, json: String): String =
        "```$kind\n${json.trim()}\n```"

    /**
     * Chart JSON with points as [x,y]. `series` is name to points; labels are
     * the category names. Keeps the pages above readable and guarantees every
     * payload has the same shape the renderer validates.
     */
    private fun chartFence(
        type: String,
        title: String,
        labels: List<String>,
        series: List<Pair<String, List<Pair<Float, Float>>>>,
        unit: String = "",
    ): String {
        val labelsJson = labels.joinToString(",") { "\"${escape(it)}\"" }
        val seriesJson = series.joinToString(",") { (name, points) ->
            val pts = points.joinToString(",") { (x, y) -> "[${num(x)},${num(y)}]" }
            """{"name":"${escape(name)}","data":[$pts]}"""
        }
        val unitJson = if (unit.isBlank()) "" else ",\"unit\":\"${escape(unit)}\""
        return fence(
            "chart",
            """{"type":"$type","title":"${escape(title)}","labels":[$labelsJson],"series":[$seriesJson]$unitJson}"""
        )
    }

    private fun pieJson(): String = """
{"type":"pie","title":"Where the week went","labels":["Deep work","Meetings","Admin","Rest"],"series":[{"name":"Deep work","data":[[0,18]]},{"name":"Meetings","data":[[1,9]]},{"name":"Admin","data":[[2,6]]},{"name":"Rest","data":[[3,11]]}]}
""".trim()

    private fun donutJson(): String = """
{"type":"donut","title":"How replies arrive","labels":["Text","Widgets","Voice"],"series":[{"name":"Text","data":[[0,62]]},{"name":"Widgets","data":[[1,28]]},{"name":"Voice","data":[[2,10]]}]}
""".trim()

    private fun num(value: Float): String =
        if (value == value.toLong().toFloat()) value.toLong().toString() else value.toString()

    private fun escape(value: String): String =
        value.replace("\\", "\\\\").replace("\"", "\\\"")
}
