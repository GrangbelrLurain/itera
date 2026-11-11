<#
.SYNOPSIS
    AeroCore Universal Verification Pipeline (check_all.ps1)
    One-click automated validation across all polyglot tiers:
    1) OpenAPI Contract (TS type generation)
    2) Java GDS Adapter, simulated (javac & GdsAdapterTest)
    3) Java Backend API Service (Gradle: unit + HTTP tests, OpenAPI contract drift check)
    4) Tauri Desktop Shell Native Commands (cargo test)
    5) Next.js Web Check & Build
    6) (-E2E) Playwright end-to-end tests on the backend's memory profile
#>

[CmdletBinding()]
param(
    # Also run the Playwright end-to-end tests (needs a JDK, Chromium installed once with
    # `pnpm --filter @itera/e2e install:browsers`; adds about a minute).
    [switch]$E2E
)

$ErrorActionPreference = "Continue"
$rootDir = $PSScriptRoot
if (-not $rootDir) {
    $rootDir = Get-Location
}
Set-Location $rootDir

Write-Host "==========================================================================" -ForegroundColor Cyan
Write-Host "  ✈️  AeroCore Enterprise Polyglot Verification Pipeline                " -ForegroundColor Cyan
Write-Host "  Root: $rootDir" -ForegroundColor DarkGray
Write-Host "==========================================================================" -ForegroundColor Cyan

$pipelineStart = Get-Date
$results = @()

function Run-Step {
    param (
        [string]$Name,
        [string]$Language,
        [scriptblock]$Action
    )

    Write-Host "`n--------------------------------------------------------------------------" -ForegroundColor DarkCyan
    Write-Host "▶ Running: $Name [$Language]" -ForegroundColor Yellow
    Write-Host "--------------------------------------------------------------------------" -ForegroundColor DarkCyan

    $stepStart = Get-Date
    $success = $false
    $errMessage = ""

    try {
        & $Action
        if ($LASTEXITCODE -eq 0 -or $LASTEXITCODE -eq $null) {
            $success = $true
        } else {
            $errMessage = "Process exited with code $LASTEXITCODE"
        }
    } catch {
        $errMessage = $_.Exception.Message
    }

    $elapsed = [Math]::Round(((Get-Date) - $stepStart).TotalSeconds, 2)

    if ($success) {
        Write-Host "✔ $Name PASSED (${elapsed}s)" -ForegroundColor Green
        $script:results += [PSCustomObject]@{
            Step = $Name
            Language = $Language
            Status = "PASS"
            Duration = "${elapsed}s"
            Error = "-"
        }
    } else {
        Write-Host "✘ $Name FAILED (${elapsed}s): $errMessage" -ForegroundColor Red
        $script:results += [PSCustomObject]@{
            Step = $Name
            Language = $Language
            Status = "FAIL"
            Duration = "${elapsed}s"
            Error = $errMessage
        }
    }
}

# 6. API contract: the generated client types must match contracts/*.openapi.json
Run-Step -Name "OpenAPI Contract Types Sync" -Language "OpenAPI" -Action {
    Set-Location $rootDir
    pnpm --filter "@itera/api-client" gen:check
    if ($LASTEXITCODE -eq 0) {
        pnpm --filter "@itera/api-client" check
    }
}

# 6-1. Presentation domain (labels, formatters, form mappers): unit tests
Run-Step -Name "Presentation Domain Unit Tests" -Language "TypeScript (Vitest)" -Action {
    Set-Location $rootDir
    pnpm --filter "@itera/domain" check
    if ($LASTEXITCODE -eq 0) {
        pnpm --filter "@itera/domain" test
    }
}

# 6-2. UI package: types and lint (the components themselves are checked through the app build)
Run-Step -Name "UI Package Check" -Language "TypeScript (Tailwind)" -Action {
    Set-Location $rootDir
    pnpm --filter "@itera/ui" check
}

# 6-3. Module boundaries (dependency-cruiser): layers of the packages and of apps/admin
Run-Step -Name "Module Boundaries" -Language "TypeScript (dep-cruiser)" -Action {
    Set-Location $rootDir
    pnpm check:boundaries
}

# 7. Java GDS Adapter (simulated) tests
Run-Step -Name "Java GDS Adapter Layer" -Language "Java (simulated GDS)" -Action {
    Set-Location "$rootDir\services\gds-adapter"
    if (-not (Test-Path "bin")) {
        New-Item -ItemType Directory -Path "bin" | Out-Null
    }
    $javaFiles = (Get-ChildItem -Recurse -Filter "*.java").FullName
    javac -d bin $javaFiles
    if ($LASTEXITCODE -eq 0) {
        java -cp bin aerocore.gds.GdsAdapterTest
    }
}

# 8. Java Backend API (Spring Boot): unit + HTTP tests and the generated OpenAPI contracts check
Run-Step -Name "Java Backend API REST Service" -Language "Java (Spring Boot)" -Action {
    Set-Location (Join-Path $rootDir "services/backend-api")
    # JAVA_HOME may point to a missing JDK on dev machines; fall back to the JDK that `java` resolves to.
    if (-not $env:JAVA_HOME -or -not (Test-Path (Join-Path $env:JAVA_HOME "bin/javac.exe"))) {
        $jdkHome = (& java -XshowSettings:properties -version 2>&1 | Select-String "java.home = (.+)").Matches[0].Groups[1].Value.Trim()
        $env:JAVA_HOME = $jdkHome
    }
    .\gradlew.bat test --console=plain
}

# 9. Tauri Desktop Shell Native Commands Tests
Run-Step -Name "Tauri Desktop Shell Native Commands" -Language "Rust (Tauri v2)" -Action {
    Set-Location "$rootDir\apps\desktop-shell"
    cargo test
}

# 10. Next.js Web Check & Build
Run-Step -Name "Next.js Web Check & Build" -Language "TypeScript (Next.js)" -Action {
    Set-Location (Join-Path $rootDir "apps/admin")
    pnpm check
    if ($LASTEXITCODE -eq 0 -or $LASTEXITCODE -eq $null) {
        pnpm build
    }
}

# 11. End-to-end (opt-in): backend memory profile + admin web + Playwright
if ($E2E) {
    Run-Step -Name "End-to-End Tests (Playwright)" -Language "TypeScript (Playwright)" -Action {
        Set-Location $rootDir
        pnpm --filter "@itera/e2e" check
        if ($LASTEXITCODE -eq 0 -or $LASTEXITCODE -eq $null) {
            Set-Location (Join-Path $rootDir "services/backend-api")
            & .\gradlew.bat bootJar -q
        }
        if ($LASTEXITCODE -eq 0 -or $LASTEXITCODE -eq $null) {
            Set-Location (Join-Path $rootDir "e2e")
            pnpm exec playwright test
        }
    }
}

Set-Location $rootDir
$totalDuration = [Math]::Round(((Get-Date) - $pipelineStart).TotalSeconds, 2)

# Print Summary Report
Write-Host "`n==========================================================================" -ForegroundColor Cyan
Write-Host "  📊  AeroCore Verification Pipeline Summary Report                      " -ForegroundColor Cyan
Write-Host "==========================================================================" -ForegroundColor Cyan

$allPass = $true
foreach ($res in $results) {
    if ($res.Status -eq "PASS") {
        Write-Host "  [✔ PASS] $($res.Step.PadRight(35)) | $($res.Language.PadRight(20)) | $($res.Duration)" -ForegroundColor Green
    } else {
        Write-Host "  [✘ FAIL] $($res.Step.PadRight(35)) | $($res.Language.PadRight(20)) | $($res.Duration) | $($res.Error)" -ForegroundColor Red
        $allPass = $false
    }
}

Write-Host "--------------------------------------------------------------------------" -ForegroundColor DarkGray
Write-Host "Total Execution Time: ${totalDuration}s" -ForegroundColor White

if ($allPass) {
    Write-Host "`n🎉 ALL PIPELINE TESTS PASSED WITH 0 ERRORS! (Ready for Production)" -ForegroundColor Green
    exit 0
} else {
    Write-Host "`n❌ SOME TESTS FAILED. Please inspect the log above." -ForegroundColor Red
    exit 1
}
