$file = "C:\Users\deploy\.claude\projects\C--Users-deploy-Projects-re-lab-android-dr-claw-app\1b204828-8640-48c4-81e8-2a745767ec2d.jsonl"
Get-Content $file | Select-Object -First 5 | ForEach-Object {
    $obj = $_ | ConvertFrom-Json
    Write-Host "type=$($obj.type) cwd=$($obj.cwd) keys=$($obj.PSObject.Properties.Name -join ',')"
}
