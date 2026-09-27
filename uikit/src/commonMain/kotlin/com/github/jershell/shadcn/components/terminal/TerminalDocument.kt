package com.github.jershell.shadcn.components.terminal

import com.github.jershell.shadcn.generated.resources.Res
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.jsonObject
import kotlin.io.encoding.Base64
import com.github.jershell.shadcn.theme.DEPARTURE_MONO_RESOURCE_PATH

internal suspend fun terminalHtml(options: TerminalOptions, colors: TerminalColors): String {
    suspend fun asset(name: String) = Res.readBytes("files/terminal/$name").decodeToString()
    val fields = terminalJson.encodeToJsonElement(TerminalOptions.serializer(), options).jsonObject.toMutableMap()
    fields["theme"] = JsonObject(colors.webTheme().mapValues { JsonPrimitive(it.value) })
    val font = Base64.Default.encode(Res.readBytes(DEPARTURE_MONO_RESOURCE_PATH))
    return terminalDocument(
        css = "@font-face{font-family:'Departure Mono';src:url(data:font/otf;base64,$font) format('opentype');font-display:swap;}" + asset("xterm.css"),
        engine = asset("xterm.js"),
        addon = asset("addon-fit.js"),
        adapter = asset("terminal.js"),
        options = JsonObject(fields).toString(),
    )
}

internal fun terminalDocument(css: String, engine: String, addon: String, adapter: String, options: String): String = """
    <!doctype html>
    <html><head><meta charset="utf-8">
    <meta name="viewport" content="width=device-width,initial-scale=1">
    <meta http-equiv="Content-Security-Policy" content="default-src 'none'; script-src 'unsafe-inline'; style-src 'unsafe-inline'; img-src data:; font-src data:; connect-src 'none'; base-uri 'none'; form-action 'none'">
    <style>$css
    html,body,#terminal{width:100%;height:100%;margin:0;overflow:hidden;}
    #terminal{box-sizing:border-box;}.xterm{height:100%;}</style>
    </head><body><div id="terminal"></div>
    <script type="application/json" id="terminal-options">${options.replace("<", "\\u003c")}</script>
    <script>${engine.replace("</script", "<\\/script", ignoreCase = true)}</script>
    <script>${addon.replace("</script", "<\\/script", ignoreCase = true)}</script>
    <script>$adapter</script>
    </body></html>
""".trimIndent()
