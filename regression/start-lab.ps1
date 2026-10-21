param([ValidateSet('start','stop','status','test','all','smoke','extended','security','backend','audit','a11y','logs','remove')][string]$Action='start')
$ErrorActionPreference='Stop'
Set-StrictMode -Version Latest
function Run([string]$Program,[string[]]$Arguments) {
    & $Program @Arguments
    if($LASTEXITCODE -ne 0){throw "$Program fehlgeschlagen (Exitcode $LASTEXITCODE)"}
}
Get-Command podman -ErrorAction Stop | Out-Null
switch($Action){
    all { & powershell -ExecutionPolicy Bypass -File (Join-Path $PSScriptRoot 'run-regression.ps1'); exit $LASTEXITCODE }
    stop { Run podman -Arguments @('pod','stop','familyhub-e2e'); exit }
    status { Run podman -Arguments @('ps','-a','--filter','pod=familyhub-e2e'); exit }
    logs { Run podman -Arguments @('logs','--tail','100','familyhub-e2e-backend'); exit }
    test { Run podman -Arguments @('exec','familyhub-e2e-desktop','npm','test'); exit }
    smoke { Run podman -Arguments @('exec','familyhub-e2e-desktop','npm','run','test:smoke'); exit }
    extended { Run podman -Arguments @('exec','familyhub-e2e-desktop','npm','run','test:extended'); exit }
    security { Run podman -Arguments @('exec','familyhub-e2e-desktop','npm','run','test:security'); exit }
    backend { Run podman -Arguments @('exec','familyhub-e2e-desktop','npm','run','test:backend'); exit }
    audit { Run podman -Arguments @('exec','familyhub-e2e-desktop','npm','run','test:audit'); exit }
    a11y { Run podman -Arguments @('exec','familyhub-e2e-desktop','npm','run','test:a11y'); exit }
    remove { Run podman -Arguments @('pod','rm','-f','familyhub-e2e'); exit }
}
Run podman -Arguments @('info','--format','{{.Host.OS}}')
& podman pod exists familyhub-e2e
if($LASTEXITCODE -eq 0){
    Run podman -Arguments @('pod','start','familyhub-e2e')
    Write-Host 'Testlabor gestartet: http://localhost:6080'; exit
}
$source=Join-Path $PSScriptRoot 'source'
$revision='fb02978db0e9e1d202ba32e92241f32cc34f8272'
if(-not(Test-Path $source)){Run git -Arguments @('clone','https://github.com/JNeugaertner/Family-Hub.git',$source)}
Run git -Arguments @('-C',$source,'fetch','origin',$revision)
Run git -Arguments @('-C',$source,'checkout','--detach',$revision)
Copy-Item (Join-Path $PSScriptRoot 'app/nginx.conf') (Join-Path $source 'podman-nginx.conf') -Force
@"
.git
local.properties
**/node_modules
target
frontend/dist
"@ | Set-Content (Join-Path $source '.containerignore') -Encoding ascii
$appfile=Join-Path $PSScriptRoot 'app/Containerfile'
Run podman -Arguments @('build','--target','backend','-f',$appfile,'-t','localhost/familyhub-e2e-backend:local',$source)
Run podman -Arguments @('build','--target','frontend','-f',$appfile,'-t','localhost/familyhub-e2e-frontend:local',$source)
Run podman -Arguments @('build','-f',(Join-Path $PSScriptRoot 'Containerfile'),'-t','localhost/familyhub-e2e-desktop:local',$PSScriptRoot)
Run podman -Arguments @('volume','create','familyhub-e2e-data')
Run podman -Arguments @('volume','create','familyhub-e2e-reports')
Run podman -Arguments @('pod','create','--name','familyhub-e2e','-p','127.0.0.1:6080:6080','-p','127.0.0.1:18080:8081')
Run podman -Arguments @('run','-d','--pod','familyhub-e2e','--name','familyhub-e2e-db','-v','familyhub-e2e-data:/data/db','docker.io/library/mongo:8.0','--bind_ip','127.0.0.1')
$ready=$false
for($i=0;$i -lt 90;$i++){
    & podman exec familyhub-e2e-db mongosh --quiet --eval 'quit(db.adminCommand({ping:1}).ok === 1 ? 0 : 1)' 2>$null | Out-Null
    if($LASTEXITCODE -eq 0){$ready=$true;break}; Start-Sleep -Seconds 2
}
if(-not $ready){throw 'MongoDB nicht bereit: podman logs familyhub-e2e-db'}
Run podman -Arguments @('run','-d','--pod','familyhub-e2e','--name','familyhub-e2e-backend','-e','SPRING_MONGODB_URI=mongodb://127.0.0.1:27017/familyhub_e2e','-e','FAMILYHUB_SAMPLEDATA_ENABLED=false','-e','JAVA_TOOL_OPTIONS=-Duser.timezone=Europe/Berlin','localhost/familyhub-e2e-backend:local')
Run podman -Arguments @('run','-d','--pod','familyhub-e2e','--name','familyhub-e2e-frontend','localhost/familyhub-e2e-frontend:local')
$ready=$false
for($i=0;$i -lt 90;$i++){
    try{if((Invoke-WebRequest -UseBasicParsing http://127.0.0.1:18080/api/health -TimeoutSec 2).StatusCode -eq 200){$ready=$true;break}}catch{}
    Start-Sleep -Seconds 2
}
if(-not $ready){throw 'Backend nicht bereit: podman logs familyhub-e2e-backend'}
Run podman -Arguments @('run','-d','--pod','familyhub-e2e','--name','familyhub-e2e-desktop','--shm-size','1g','-v','familyhub-e2e-reports:/tests/reports','localhost/familyhub-e2e-desktop:local')
Write-Host 'Testlabor: http://localhost:6080 | Testanwendung: http://localhost:18080'
Write-Host 'Desktop oeffnen und Enter im Terminal druecken, um die Tests zu starten.'
