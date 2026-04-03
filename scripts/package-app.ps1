$ErrorActionPreference = "Stop"

param(
    [string]$Type = "app-image",
    [string]$Destination = ""
)

$RootDir = (Resolve-Path (Join-Path $PSScriptRoot "..")).Path
$AppName = "NextChess"
$ArtifactPrefix = "next-chess-desktop-java-"
$MainClass = "com.github.keithwegner.chess.Main"

if ([string]::IsNullOrWhiteSpace($Destination)) {
    $Destination = Join-Path $RootDir "dist/jpackage"
} elseif (-not [System.IO.Path]::IsPathRooted($Destination)) {
    $Destination = Join-Path $RootDir $Destination
}

Set-Location $RootDir

Write-Host "Building application JAR..."
mvn -B -DskipTests package

$JarPath = Get-ChildItem (Join-Path $RootDir "target") -Filter "${ArtifactPrefix}*.jar" | Sort-Object Name | Select-Object -First 1
if (-not $JarPath) {
    throw "Could not find packaged application JAR under target/."
}

$MainJar = $JarPath.Name
$AppVersion = $MainJar.Substring($ArtifactPrefix.Length)
$AppVersion = $AppVersion.Substring(0, $AppVersion.Length - 4)

if (Test-Path $Destination) {
    Remove-Item -Recurse -Force $Destination
}
New-Item -ItemType Directory -Path $Destination | Out-Null

$JpackageArgs = @(
    "--type", $Type,
    "--input", (Join-Path $RootDir "target"),
    "--dest", $Destination,
    "--name", $AppName,
    "--main-jar", $MainJar,
    "--main-class", $MainClass,
    "--app-version", $AppVersion,
    "--vendor", "Keith Wegner",
    "--description", "Interactive desktop chess analysis app in Java 17 + Maven."
)

if ($IsMacOS) {
    $JpackageArgs += @("--mac-package-identifier", "com.github.keithwegner.chess")
}

Write-Host "Running jpackage ($Type)..."
jpackage @JpackageArgs

Write-Host "Package created in $Destination"
