Set-Location "C:\Users\deploy\Projects\cc-bridge"
$env:TERM = "dumb"
& claude --dangerously-skip-permissions -p "Read CLAUDE.md carefully and build everything it describes. Create package.json, src/index.js, and README.md. Run npm install when done." 2>&1 | Tee-Object -FilePath "C:\Users\deploy\Projects\cc-bridge\build_log.txt"
