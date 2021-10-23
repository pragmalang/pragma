val metacallEnvVarExportLines =
  os.proc("metacall", "/pragma/scripts/metacall-env.js").call().out.lines.init.init

metacallEnvVarExportLines foreach println
