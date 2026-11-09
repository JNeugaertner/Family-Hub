param([string]$SourcePath = '')
$ErrorActionPreference='Stop'
Push-Location $PSScriptRoot
try {
    if(Test-Path reports/backend-unit){
        Get-ChildItem reports/backend-unit -File -Filter 'TEST-*.xml' | ForEach-Object {Remove-Item -LiteralPath $_.FullName}
    }
    $nativeArgs=@('-ExecutionPolicy','Bypass','-File',(Join-Path $PSScriptRoot 'backend-tests/run.ps1'))
    if($SourcePath){$nativeArgs+=@('-SourcePath',$SourcePath)}
    & powershell @nativeArgs
    $nativeCode=$LASTEXITCODE
    & podman exec familyhub-e2e-desktop node -e 'const fs=require("fs"); for(const n of ["cucumber.json","cucumber.html","junit.xml"]){const p="reports/"+n;if(fs.existsSync(p))fs.unlinkSync(p)}'
    if($LASTEXITCODE){throw 'Vorbereitung des Cucumber-Laufs fehlgeschlagen.'}
    # Auch bei roten fachlichen Tests alle übrigen Tests ausführen.
    & podman exec familyhub-e2e-desktop npm test
    $cucumberCode=$LASTEXITCODE
    & podman cp reports/backend-unit familyhub-e2e-desktop:/tests/reports/
    if($LASTEXITCODE){throw 'Native Berichte konnten nicht übertragen werden.'}
    & podman cp scripts/build-total-report.py familyhub-e2e-desktop:/tests/scripts/build-total-report.py
    if($LASTEXITCODE){throw 'Reportgenerator konnte nicht übertragen werden.'}
    & podman exec familyhub-e2e-desktop python3 scripts/build-total-report.py
    if($LASTEXITCODE){throw 'Gesamtbericht konnte nicht erstellt werden.'}
    Write-Host 'Gesamtbericht: http://localhost:6080/reports/index.html'
    if($nativeCode -ne 0 -or $cucumberCode -ne 0){exit 1}
} finally { Pop-Location }
