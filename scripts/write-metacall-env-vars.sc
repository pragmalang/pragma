val metacallEnvVarExportLines = os.proc("metacall", "/pragma/scripts/metacall-env.js").call().out.lines.init 

println(metacallEnvVarExportLines.mkString("\n"))