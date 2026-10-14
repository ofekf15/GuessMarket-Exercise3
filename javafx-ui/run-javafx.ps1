$ErrorActionPreference = 'Stop'
$javac = 'C:\temp\jdk25\jdk-25.0.4+7\bin\javac.exe'
$java = 'C:\temp\jdk25\jdk-25.0.4+7\bin\java.exe'
$javafx = Join-Path $PSScriptRoot '..\.javafx-sdk-25.0.4\javafx-sdk-25.0.4\lib'
$jaxb = Join-Path $PSScriptRoot '..\jaxb\jaxb-ri-4.0.5\mod\*'
$build = Join-Path $PSScriptRoot 'build\classes'

New-Item -ItemType Directory -Force -Path $build | Out-Null
$sources = Get-ChildItem -Path (Join-Path $PSScriptRoot '..\dto\src'),
    (Join-Path $PSScriptRoot '..\engine\src'),
    (Join-Path $PSScriptRoot 'src') -Recurse -Filter '*.java' |
    ForEach-Object { $_.FullName }
& $javac --module-path $javafx --add-modules javafx.controls,javafx.fxml `
    -cp $jaxb -d $build $sources
Copy-Item -Path (Join-Path $PSScriptRoot 'resources\*') -Destination $build -Recurse -Force
& $java --module-path $javafx --add-modules javafx.controls,javafx.fxml `
    -cp "$build;$jaxb" il.ac.mta.gm.ui.GuessMarketApplication
