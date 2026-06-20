param(
    [string]$DbHost = "127.0.0.1",
    [int]$DbPort = 3306,
    [string]$DbName = "volunteer_service",
    [string]$DbUser = "root",
    [string]$DbPassword = "",
    [switch]$InitDb,
    [switch]$WithDemo
)

$ErrorActionPreference = "Stop"
[Console]::OutputEncoding = [System.Text.UTF8Encoding]::new()
$OutputEncoding = [System.Text.UTF8Encoding]::new()

$RootDir = (Resolve-Path -LiteralPath (Join-Path $PSScriptRoot "..")).Path
$BackendEnv = Join-Path $RootDir "backend\.env"
$FrontendEnv = Join-Path $RootDir "frontend\.env"

if ([string]::IsNullOrWhiteSpace($DbPassword)) {
    $secure = Read-Host "MySQL password for $DbUser@$DbHost" -AsSecureString
    $ptr = [Runtime.InteropServices.Marshal]::SecureStringToBSTR($secure)
    try {
        $DbPassword = [Runtime.InteropServices.Marshal]::PtrToStringBSTR($ptr)
    } finally {
        [Runtime.InteropServices.Marshal]::ZeroFreeBSTR($ptr)
    }
}

function Copy-IfMissing {
    param(
        [string]$Source,
        [string]$Target
    )
    if (-not (Test-Path -LiteralPath $Target)) {
        Copy-Item -LiteralPath $Source -Destination $Target
        Write-Host "created $($Target.Substring($RootDir.Length + 1))"
    } else {
        Write-Host "kept existing $($Target.Substring($RootDir.Length + 1))"
    }
}

function Set-EnvValue {
    param(
        [string]$File,
        [string]$Key,
        [string]$Value
    )
    $line = "$Key=$Value"
    $content = Get-Content -LiteralPath $File -Encoding UTF8
    $found = $false
    $updated = foreach ($item in $content) {
        if ($item -match "^$([regex]::Escape($Key))=") {
            $found = $true
            $line
        } else {
            $item
        }
    }
    if (-not $found) {
        $updated = @($updated) + @("", $line)
    }
    Set-Content -LiteralPath $File -Value $updated -Encoding UTF8
}

function New-JwtSecret {
    $bytes = New-Object byte[] 32
    [Security.Cryptography.RandomNumberGenerator]::Fill($bytes)
    return -join ($bytes | ForEach-Object { $_.ToString("x2") })
}

function Invoke-MysqlScript {
    param(
        [string]$ScriptPath,
        [bool]$UseDatabase
    )
    if (-not (Get-Command mysql -ErrorAction SilentlyContinue)) {
        throw "mysql command not found. Install MySQL client or run SOURCE manually."
    }
    $oldPassword = $env:MYSQL_PWD
    $env:MYSQL_PWD = $DbPassword
    try {
        $args = @("--default-character-set=utf8mb4", "-h", $DbHost, "-P", [string]$DbPort, "-u", $DbUser)
        if ($UseDatabase) {
            $args += $DbName
        }
        Get-Content -Raw -Encoding UTF8 -LiteralPath $ScriptPath | & mysql @args
        if ($LASTEXITCODE -ne 0) {
            throw "mysql exited with code $LASTEXITCODE"
        }
    } finally {
        $env:MYSQL_PWD = $oldPassword
    }
}

Copy-IfMissing -Source (Join-Path $RootDir "backend\.env.example") -Target $BackendEnv
Copy-IfMissing -Source (Join-Path $RootDir "frontend\.env.example") -Target $FrontendEnv

Set-EnvValue -File $BackendEnv -Key "SPRING_DATASOURCE_URL" -Value "jdbc:mysql://${DbHost}:${DbPort}/${DbName}?createDatabaseIfNotExist=true&useSSL=false&allowPublicKeyRetrieval=true&serverTimezone=Asia/Shanghai&useUnicode=true&characterEncoding=UTF-8"
Set-EnvValue -File $BackendEnv -Key "SPRING_DATASOURCE_USERNAME" -Value $DbUser
Set-EnvValue -File $BackendEnv -Key "SPRING_DATASOURCE_PASSWORD" -Value $DbPassword
Set-EnvValue -File $BackendEnv -Key "SPRING_JPA_HIBERNATE_DDL_AUTO" -Value "none"
Set-EnvValue -File $BackendEnv -Key "VMS_BOOTSTRAP_ENABLED" -Value "false"
Set-EnvValue -File $BackendEnv -Key "VMS_JWT_SECRET" -Value (New-JwtSecret)
Set-EnvValue -File $BackendEnv -Key "VMS_FILE_STORAGE_DIR" -Value "./uploads"

Set-EnvValue -File $FrontendEnv -Key "VITE_BACKEND_ORIGIN" -Value "http://127.0.0.1:8080"

New-Item -ItemType Directory -Force -Path (Join-Path $RootDir "backend\uploads") | Out-Null

if ($InitDb) {
    Write-Host "importing backend/src/main/resources/sql/init.sql"
    Invoke-MysqlScript -ScriptPath (Join-Path $RootDir "backend\src\main\resources\sql\init.sql") -UseDatabase $false
    if ($WithDemo) {
        Write-Host "importing backend/src/main/resources/sql/demo-data.sql"
        Invoke-MysqlScript -ScriptPath (Join-Path $RootDir "backend\src\main\resources\sql\demo-data.sql") -UseDatabase $true
    }
}

Write-Host "setup complete"
Write-Host "backend env: backend\.env"
Write-Host "frontend env: frontend\.env"
Write-Host "demo data SQL: SOURCE backend/src/main/resources/sql/demo-data.sql;"
