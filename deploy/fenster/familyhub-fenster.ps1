# FamilyHub-Startfenster für Windows (WPF, in Windows eingebaut). Zwei Knöpfe, Starten und Beenden; die
# eigentliche Arbeit macht familyhub.ps1 im Ordner deploy (Docker prüfen, .env, Image, warten usw.).
# Normalerweise per Doppelklick auf deploy\FamilyHub.bat. Gegenstück zu FamilyHub.app (macOS).
# Gespeichert als UTF-8 mit BOM, sonst zeigt Windows PowerShell 5.1 die Umlaute falsch an.
$ErrorActionPreference = 'Stop'

try {
    Add-Type -AssemblyName PresentationFramework, PresentationCore, WindowsBase, System.Xml

    $deployDir = Split-Path -Parent $PSScriptRoot
    $script = Join-Path $deployDir 'familyhub.ps1'
    $logFile = Join-Path $env:TEMP 'familyhub-fenster.log'
    $errFile = Join-Path $env:TEMP 'familyhub-fenster.err'
    $iconFile = Join-Path $PSScriptRoot 'familyhub.png'

    # Helles oder dunkles Windows-Design (0 = dunkel)
    $dark = $false
    try {
        $dark = (Get-ItemPropertyValue 'HKCU:\Software\Microsoft\Windows\CurrentVersion\Themes\Personalize' `
            -Name AppsUseLightTheme -ErrorAction Stop) -eq 0
    } catch { }
    $c = if ($dark) {
        @{ Bg = '#1F1F1F'; Text = '#F5F5F5'; Sub = '#B4B4B4'; Muted = '#7A7A7A'; LogBg = '#141414'; Border = '#3A3A3A' }
    } else {
        @{ Bg = '#FFFFFF'; Text = '#111827'; Sub = '#4B5563'; Muted = '#9CA3AF'; LogBg = '#F8FAFC'; Border = '#E5E7EB' }
    }

    [xml]$xaml = @"
<Window xmlns="http://schemas.microsoft.com/winfx/2006/xaml/presentation"
        xmlns:x="http://schemas.microsoft.com/winfx/2006/xaml"
        Title="FamilyHub" Width="460" SizeToContent="Height" WindowStyle="None" AllowsTransparency="True"
        Background="Transparent" ResizeMode="CanMinimize" WindowStartupLocation="CenterScreen"
        FontFamily="Segoe UI" UseLayoutRounding="True">
  <Window.Resources>
    <!-- Knopf ohne Windows-Rahmen: Farbe aus Background/BorderBrush, abgerundet, blass wenn gesperrt -->
    <ControlTemplate x:Key="RoundButton" TargetType="Button">
      <Border x:Name="bg" Background="{TemplateBinding Background}" BorderBrush="{TemplateBinding BorderBrush}"
              BorderThickness="{TemplateBinding BorderThickness}" CornerRadius="11">
        <ContentPresenter HorizontalAlignment="Center" VerticalAlignment="Center"/>
      </Border>
      <ControlTemplate.Triggers>
        <Trigger Property="IsMouseOver" Value="True"><Setter TargetName="bg" Property="Opacity" Value="0.88"/></Trigger>
        <Trigger Property="IsPressed" Value="True"><Setter TargetName="bg" Property="Opacity" Value="0.75"/></Trigger>
        <Trigger Property="IsEnabled" Value="False"><Setter TargetName="bg" Property="Opacity" Value="0.4"/></Trigger>
      </ControlTemplate.Triggers>
    </ControlTemplate>
    <ControlTemplate x:Key="LinkButton" TargetType="Button">
      <TextBlock x:Name="t" Text="{Binding Content, RelativeSource={RelativeSource TemplatedParent}}" Foreground="#3B82F6" FontSize="13" Cursor="Hand"/>
      <ControlTemplate.Triggers>
        <Trigger Property="IsMouseOver" Value="True"><Setter TargetName="t" Property="TextDecorations" Value="Underline"/></Trigger>
      </ControlTemplate.Triggers>
    </ControlTemplate>
    <ControlTemplate x:Key="TitleButton" TargetType="Button">
      <Border x:Name="b" Background="Transparent" CornerRadius="6" Width="30" Height="26">
        <TextBlock Text="{Binding Content, RelativeSource={RelativeSource TemplatedParent}}" Foreground="White" FontSize="13" HorizontalAlignment="Center" VerticalAlignment="Center"/>
      </Border>
      <ControlTemplate.Triggers>
        <Trigger Property="IsMouseOver" Value="True"><Setter TargetName="b" Property="Background" Value="#33FFFFFF"/></Trigger>
      </ControlTemplate.Triggers>
    </ControlTemplate>
  </Window.Resources>

  <Border Margin="12" CornerRadius="14" Background="$($c.Bg)" BorderBrush="$($c.Border)" BorderThickness="1">
    <Border.Effect><DropShadowEffect BlurRadius="18" ShadowDepth="3" Opacity="0.35"/></Border.Effect>
    <StackPanel>
      <!-- Kopf mit Farbverlauf, zum Verschieben des Fensters -->
      <Grid x:Name="Header" Height="118">
        <Border CornerRadius="14,14,0,0">
          <Border.Background>
            <LinearGradientBrush StartPoint="0,0" EndPoint="1,1">
              <GradientStop Color="#2563EB" Offset="0"/>
              <GradientStop Color="#14B8A6" Offset="1"/>
            </LinearGradientBrush>
          </Border.Background>
        </Border>
        <StackPanel Orientation="Horizontal" Margin="26,0,0,0" VerticalAlignment="Center">
          <Border Width="62" Height="62" CornerRadius="15" Background="#E6FFFFFF" Padding="3">
            <Border CornerRadius="13" ClipToBounds="True">
              <Image x:Name="Icon" Stretch="UniformToFill"/>
            </Border>
          </Border>
          <StackPanel Margin="16,0,0,0" VerticalAlignment="Center">
            <TextBlock Text="FamilyHub" Foreground="White" FontSize="25" FontWeight="SemiBold"/>
            <TextBlock Text="Der Familienplaner auf diesem Computer" Foreground="#E6FFFFFF" FontSize="13" Margin="1,2,0,0"/>
          </StackPanel>
        </StackPanel>
        <StackPanel Orientation="Horizontal" HorizontalAlignment="Right" VerticalAlignment="Top" Margin="0,8,10,0">
          <Button x:Name="MinButton" Content="—" Template="{StaticResource TitleButton}" ToolTip="Minimieren"/>
          <Button x:Name="CloseButton" Content="✕" Template="{StaticResource TitleButton}" ToolTip="Schließen" Margin="2,0,0,0"/>
        </StackPanel>
      </Grid>

      <StackPanel Margin="26,22,26,18">
        <StackPanel Orientation="Horizontal">
          <Ellipse x:Name="Dot" Width="13" Height="13" Fill="$($c.Muted)" VerticalAlignment="Center"/>
          <TextBlock x:Name="StatusText" Text="Status wird geprüft …" Foreground="$($c.Text)" FontSize="16"
                     FontWeight="SemiBold" Margin="10,0,0,0"/>
        </StackPanel>
        <TextBlock x:Name="HintText" Foreground="$($c.Sub)" FontSize="12.5" TextWrapping="Wrap" Margin="23,6,0,0" MinHeight="34"/>

        <Grid Margin="0,14,0,0">
          <Grid.ColumnDefinitions>
            <ColumnDefinition/><ColumnDefinition Width="12"/><ColumnDefinition/>
          </Grid.ColumnDefinitions>
          <Button x:Name="StartButton" Grid.Column="0" Height="44" Template="{StaticResource RoundButton}"
                  Foreground="White" FontSize="15" FontWeight="SemiBold" BorderThickness="0" IsDefault="True">
            <Button.Background>
              <LinearGradientBrush StartPoint="0,0" EndPoint="1,0">
                <GradientStop Color="#2563EB" Offset="0"/>
                <GradientStop Color="#0D9488" Offset="1"/>
              </LinearGradientBrush>
            </Button.Background>
            <TextBlock>&#x25B6;  Starten</TextBlock>
          </Button>
          <Button x:Name="StopButton" Grid.Column="2" Height="44" Template="{StaticResource RoundButton}"
                  Background="#1AE5484D" BorderBrush="#E5484D" BorderThickness="1.5" Foreground="#E5484D"
                  FontSize="15" FontWeight="SemiBold">
            <TextBlock>&#x25A0;  Beenden</TextBlock>
          </Button>
        </Grid>

        <Grid Margin="2,16,2,0">
          <Button x:Name="BrowserLink" Content="Im Browser öffnen ›" Template="{StaticResource LinkButton}" HorizontalAlignment="Left"/>
          <Button x:Name="DetailsLink" Content="Details anzeigen" Template="{StaticResource LinkButton}" HorizontalAlignment="Right"/>
        </Grid>

        <TextBox x:Name="LogBox" Visibility="Collapsed" Height="170" Margin="0,14,0,0" IsReadOnly="True"
                 FontFamily="Consolas" FontSize="11.5" TextWrapping="Wrap" VerticalScrollBarVisibility="Auto"
                 Background="$($c.LogBg)" Foreground="$($c.Text)" BorderBrush="$($c.Border)" Padding="6"/>
      </StackPanel>
    </StackPanel>
  </Border>
</Window>
"@

    $win = [Windows.Markup.XamlReader]::Load((New-Object System.Xml.XmlNodeReader $xaml))
    $ui = @{}
    foreach ($name in 'Header', 'Icon', 'MinButton', 'CloseButton', 'Dot', 'StatusText', 'HintText', 'StartButton',
            'StopButton', 'BrowserLink', 'DetailsLink', 'LogBox') {
        $ui[$name] = $win.FindName($name)
    }
    if (Test-Path $iconFile) {
        $bitmap = New-Object Windows.Media.Imaging.BitmapImage (New-Object Uri $iconFile)
        $ui.Icon.Source = $bitmap
        $win.Icon = $bitmap
    }

    # --- Zustand ---------------------------------------------------------------------------------------------

    $state = @{ Name = 'unknown'; Error = ''; Job = $null; Action = ''; Ticks = 0 }

    function Get-EnvSetting([string]$key) {
        $file = Join-Path $deployDir '.env'
        if (-not (Test-Path $file)) { return '' }
        $line = Get-Content $file | Where-Object { $_ -match "^$key=" } | Select-Object -Last 1
        if ($line) { return ($line -replace "^$key=", '').Trim() }
        return ''
    }
    function Get-Port { $p = Get-EnvSetting 'FAMILYHUB_PORT'; if ($p) { return $p } return '8080' }
    function Get-Url { $u = Get-EnvSetting 'FAMILYHUB_URL'; if ($u) { return $u } return "http://localhost:$(Get-Port)" }

    $brush = { param($hex) (New-Object Windows.Media.BrushConverter).ConvertFromString($hex) }

    function Update-View {
        $busy = $state.Name -in 'starting', 'stopping'
        $s = switch ($state.Name) {
            'running' { 'Läuft', '#16A34A', (Get-Url) }
            'stopped' { 'Beendet', $c.Muted, 'Mit „Starten“ geht es los. Docker Desktop oder Podman muss installiert sein.' }
            'starting' { 'Startet …', '#D97706', 'Beim ersten Mal dauert das einige Minuten. Der Browser öffnet sich danach von selbst.' }
            'stopping' { 'Wird beendet …', '#D97706', 'Die Daten bleiben erhalten.' }
            'error' { 'Fehler', '#DC2626', $state.Error }
            default { 'Status wird geprüft …', $c.Muted, '' }
        }
        $ui.StatusText.Text = $s[0]
        $ui.Dot.Fill = & $brush $s[1]
        $ui.HintText.Text = $s[2]
        $ui.StartButton.IsEnabled = (-not $busy) -and $state.Name -ne 'running'
        $ui.StopButton.IsEnabled = (-not $busy) -and $state.Name -ne 'stopped'
        $ui.BrowserLink.Visibility = if ($state.Name -eq 'running') { 'Visible' } else { 'Collapsed' }
    }

    function Show-Details([bool]$open) {
        $ui.LogBox.Visibility = if ($open) { 'Visible' } else { 'Collapsed' }
        $ui.DetailsLink.Content = if ($open) { 'Details ausblenden' } else { 'Details anzeigen' }
    }

    function Read-Log {
        $text = ''
        foreach ($f in $logFile, $errFile) {
            if (Test-Path $f) { $text += [IO.File]::ReadAllText($f) }
        }
        return $text
    }

    function Invoke-Action([string]$action) {
        if ($state.Job) { return }
        if (-not (Test-Path $script)) {
            $state.Name = 'error'; $state.Error = 'familyhub.ps1 nicht gefunden. Der Ordner fenster muss im Ordner deploy liegen.'
            Update-View; return
        }
        Remove-Item $logFile, $errFile -ErrorAction SilentlyContinue
        $state.Action = $action
        $state.Name = if ($action -eq 'start') { 'starting' } else { 'stopping' }
        $state.Error = ''
        Update-View
        $state.Job = Start-Process powershell -PassThru -WindowStyle Hidden `
            -ArgumentList '-NoProfile', '-ExecutionPolicy', 'Bypass', '-File', "`"$script`"", $action `
            -RedirectStandardOutput $logFile -RedirectStandardError $errFile
        $null = $state.Job.Handle  # damit ExitCode nach dem Ende verfügbar ist
    }

    function Complete-Job {
        $code = $state.Job.ExitCode
        $state.Job = $null
        $log = Read-Log
        $ui.LogBox.Text = $log
        if ($code -ne 0) {
            $m = [regex]::Matches($log, 'Fehler: ([^\r\n]+)')
            $state.Error = if ($m.Count) { $m[$m.Count - 1].Groups[1].Value } else { 'Unbekannter Fehler, siehe Details.' }
            $state.Name = 'error'
            Show-Details $true
        } else {
            $state.Name = if ($state.Action -eq 'start') { 'running' } else { 'stopped' }
        }
        Update-View
    }

    # Kurze Abfrage; "Verbindung abgelehnt" kommt sofort, das Fenster hängt also nicht
    function Test-Health {
        try {
            $request = [Net.WebRequest]::Create("http://localhost:$(Get-Port)/api/health")
            $request.Timeout = 800
            $response = $request.GetResponse()
            $body = (New-Object IO.StreamReader $response.GetResponseStream()).ReadToEnd()
            $response.Close()
            return $body -match '"UP"'
        } catch { return $false }
    }

    # --- Ereignisse ------------------------------------------------------------------------------------------

    $ui.Header.Add_MouseLeftButtonDown({ $win.DragMove() })
    $ui.MinButton.Add_Click({ $win.WindowState = 'Minimized' })
    $ui.CloseButton.Add_Click({ $win.Close() })
    $ui.StartButton.Add_Click({ Invoke-Action 'start' })
    $ui.StopButton.Add_Click({ Invoke-Action 'stop' })
    $ui.BrowserLink.Add_Click({ Start-Process (Get-Url) })
    $ui.DetailsLink.Add_Click({ Show-Details ($ui.LogBox.Visibility -ne 'Visible') })

    $timer = New-Object Windows.Threading.DispatcherTimer
    $timer.Interval = [TimeSpan]::FromMilliseconds(500)
    $timer.Add_Tick({
        $state.Ticks++
        if ($state.Job) {
            $ui.LogBox.Text = Read-Log
            $ui.LogBox.ScrollToEnd()
            if ($state.Job.HasExited) { Complete-Job }
        } elseif ($state.Ticks % 6 -eq 1) {
            $up = Test-Health
            if ($state.Name -ne 'error' -or $up) { $state.Name = if ($up) { 'running' } else { 'stopped' } }
            Update-View
        }
    })

    Update-View
    $timer.Start()
    $null = $win.ShowDialog()
    $timer.Stop()
} catch {
    Add-Type -AssemblyName PresentationFramework
    [Windows.MessageBox]::Show("Das FamilyHub-Fenster ließ sich nicht öffnen:`n`n$($_.Exception.Message)`n`nZeile $($_.InvocationInfo.ScriptLineNumber)",
        'FamilyHub', 'OK', 'Error') | Out-Null
}
