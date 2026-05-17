# context-builder.ps1
# GUI context-file builder -- scrollable tree, mouse checkboxes, encoding-safe output.
# Respects .gitignore files found in any scanned directory.
#
# Usage: .\context-builder.ps1 [-Path <dir>] [-Out <file.txt>] [-Hidden]
# Keys : ENTER -> Generate   F5 -> Refresh   ESC -> Close
#
# Example:
#   .\scripts\context-builder.ps1 -Path "C:\Users\lhacenmed\AndroidStudioProjects\Khatmah\app\"

param (
    [string]$Path   = ".",
    [string]$Out    = "context.txt",
    [switch]$Hidden
)

Add-Type -AssemblyName System.Windows.Forms
Add-Type -AssemblyName System.Drawing
[System.Windows.Forms.Application]::EnableVisualStyles()

$TICK3 = '`' + '`' + '`'   # three backticks -- cannot use them directly in a here-string

# --- Anchor flag presets (AnchorStyles flags must be combined with -bor) ------
$ANC_LTR = [System.Windows.Forms.AnchorStyles]::Left  -bor
        [System.Windows.Forms.AnchorStyles]::Top   -bor
        [System.Windows.Forms.AnchorStyles]::Right
$ANC_TR  = [System.Windows.Forms.AnchorStyles]::Top   -bor
        [System.Windows.Forms.AnchorStyles]::Right

# --- Theme -------------------------------------------------------------------
$CLR = @{
    Bg      = [Drawing.Color]::FromArgb( 22,  22,  26)
    Panel   = [Drawing.Color]::FromArgb( 30,  30,  35)
    Surface = [Drawing.Color]::FromArgb( 44,  44,  52)
    Border  = [Drawing.Color]::FromArgb( 58,  58,  68)
    Fg      = [Drawing.Color]::FromArgb(218, 218, 222)
    Dim     = [Drawing.Color]::FromArgb(112, 112, 124)
    Dir     = [Drawing.Color]::FromArgb( 96, 165, 250)
    File    = [Drawing.Color]::FromArgb(200, 200, 205)
    Green   = [Drawing.Color]::FromArgb( 74, 222, 128)
    Accent  = [Drawing.Color]::FromArgb( 96, 165, 250)
    AccFg   = [Drawing.Color]::White
}

$FONT = @{
    Ui  = [Drawing.Font]::new('Segoe UI', 9.5)
    Sm  = [Drawing.Font]::new('Segoe UI', 8.5)
    Hdr = [Drawing.Font]::new('Segoe UI Semibold', 10)
}

# --- Encoding-safe reader ----------------------------------------------------
# Reads raw bytes and honours BOM; defaults to UTF-8 for all modern source files.
# Bypasses PowerShell's ambient encoding pipeline entirely.
function Read-FileText {
    param([string]$FilePath)
    $bytes = [IO.File]::ReadAllBytes($FilePath)
    $enc =
    if     ($bytes.Count -ge 3 -and $bytes[0] -eq 0xEF -and $bytes[1] -eq 0xBB -and $bytes[2] -eq 0xBF) { [Text.Encoding]::UTF8           }
    elseif ($bytes.Count -ge 2 -and $bytes[0] -eq 0xFF -and $bytes[1] -eq 0xFE) { [Text.Encoding]::Unicode          }
    elseif ($bytes.Count -ge 2 -and $bytes[0] -eq 0xFE -and $bytes[1] -eq 0xFF) { [Text.Encoding]::BigEndianUnicode }
    else                                                                          { [Text.Encoding]::UTF8             }
    return $enc.GetString($bytes).TrimEnd()
}

# --- .gitignore support ------------------------------------------------------
# Converts a single raw .gitignore pattern line into a regex + metadata object.
# Returned object has: Pattern (regex string), Negate (bool), DirOnly (bool),
# and Base (the folder path the .gitignore lives in -- added by Read-GitIgnore).
function ConvertTo-GitIgnoreRegex {
    param([string]$Raw)

    $negate  = $Raw.StartsWith('!')
    if ($negate)  { $Raw = $Raw.Substring(1) }
    $dirOnly = $Raw.EndsWith('/')
    if ($dirOnly) { $Raw = $Raw.TrimEnd('/') }

    # A pattern containing a slash (after trimming the trailing one) is
    # anchored to the directory containing the .gitignore file.
    $rooted = $Raw.Contains('/')

    # Convert glob syntax to regex (operate on the Regex.Escape'd string).
    $r = [Text.RegularExpressions.Regex]::Escape($Raw)
    $r = $r -replace '\\\*\\\*/', '(?:.+/)?'   # **/  -> zero-or-more path segments
    $r = $r -replace '\\\*\\\*',  '.*'          # **   -> anything
    $r = $r -replace '\\\*',      '[^/]*'        # *    -> any chars within one segment
    $r = $r -replace '\\\?',      '[^/]'         # ?    -> single char within one segment

    # Rooted patterns are anchored at the base dir; unrooted can match at any depth.
    $r = if ($rooted) { '^' + $r + '(/|$)' } else { '(?:^|/)' + $r + '(/|$)' }

    return [PSCustomObject]@{
        Pattern = $r
        Negate  = $negate
        DirOnly = $dirOnly
    }
}

# Reads and parses the .gitignore in $Folder (if present).
# Returns an array of pattern objects tagged with the Base folder path.
function Read-GitIgnore {
    param([string]$Folder)
    $file = Join-Path $Folder '.gitignore'
    if (-not (Test-Path -LiteralPath $file)) { return @() }
    $lines = Get-Content -LiteralPath $file -Encoding UTF8 -ErrorAction SilentlyContinue
    if (-not $lines) { return @() }
    return $lines |
            Where-Object { $_ -and -not $_.TrimStart().StartsWith('#') } |
            ForEach-Object {
                $p = ConvertTo-GitIgnoreRegex $_.Trim()
                # Tag the pattern with the folder it came from so relative paths
                # can be computed correctly even for inherited (parent) rules.
                $p | Add-Member -NotePropertyName Base -NotePropertyValue $Folder -PassThru
            }
}

# Returns $true when $FullPath should be excluded based on the given pattern list.
# Patterns are tested in order; the last matching rule wins (git semantics).
# Negation (!) un-ignores a previously ignored path.
function Test-GitIgnored {
    param([string]$FullPath, [bool]$IsDir, [array]$Patterns)
    if (-not $Patterns -or $Patterns.Count -eq 0) { return $false }
    $result = $false
    foreach ($p in $Patterns) {
        if ($p.DirOnly -and -not $IsDir) { continue }
        # Compute the path relative to the folder where the .gitignore lives.
        $rel = $FullPath.Substring($p.Base.Length).TrimStart('\', '/').Replace('\', '/')
        if ($rel -match $p.Pattern) { $result = -not $p.Negate }
    }
    return $result
}

# --- Tree population ---------------------------------------------------------
# Eagerly builds all TreeNodes in one pass inside BeginUpdate/EndUpdate.
# $InheritedPatterns carries .gitignore rules from ancestor directories so that
# a rule defined higher up the tree is honoured in subdirectories too.
function Add-ChildNodes {
    param(
        [System.Windows.Forms.TreeNodeCollection]$Nodes,
        [string]$Folder,
        [string]$Root,
        [array]$InheritedPatterns = @()
    )

    # Combine rules from ancestor directories with any .gitignore in this folder.
    $localPatterns = Read-GitIgnore $Folder
    $allPatterns   = @($InheritedPatterns) + @($localPatterns)

    $opts  = @{ LiteralPath = $Folder; Force = $Hidden.IsPresent; ErrorAction = 'SilentlyContinue' }
    $items = Get-ChildItem @opts |
            Sort-Object @{ Expression = { $_.PSIsContainer }; Descending = $true }, Name

    foreach ($item in $items) {
        # Always hide the .git metadata directory itself.
        if ($item.Name -eq '.git') { continue }

        # Skip anything matched by an active .gitignore rule.
        if ($allPatterns.Count -gt 0 -and (Test-GitIgnored $item.FullName $item.PSIsContainer $allPatterns)) { continue }

        $rel  = '/' + $item.FullName.Substring($Root.Length).TrimStart('\', '/').Replace('\', '/')
        $node = [System.Windows.Forms.TreeNode]::new($item.Name)
        $node.Tag = [PSCustomObject]@{
            Full  = $item.FullName
            Rel   = $rel
            IsDir = $item.PSIsContainer
            Name  = $item.Name
        }
        $node.ForeColor = if ($item.PSIsContainer) { $CLR.Dir } else { $CLR.File }
        # Recurse, passing the combined pattern list so child dirs inherit all rules.
        if ($item.PSIsContainer) { Add-ChildNodes $node.Nodes $item.FullName $Root $allPatterns }
        [void]$Nodes.Add($node)
    }
}

# --- Selection helpers -------------------------------------------------------

$script:_checking    = $false   # re-entrancy guard for AfterCheck cascade
$script:_initialized = $false   # true after first tree load; gates default-expand vs restore

function Invoke-CheckCascade {
    param([System.Windows.Forms.TreeNode]$Node, [bool]$State)
    foreach ($child in $Node.Nodes) {
        $child.Checked = $State
        if ($child.Nodes.Count -gt 0) { Invoke-CheckCascade $child $State }
    }
}

function Set-AllChecked {
    param([System.Windows.Forms.TreeNodeCollection]$Nodes, [bool]$State)
    foreach ($node in $Nodes) {
        $node.Checked = $State
        if ($node.Nodes.Count -gt 0) { Set-AllChecked $node.Nodes $State }
    }
}

function Get-CheckedCount {
    param([System.Windows.Forms.TreeNodeCollection]$Nodes)
    $n = 0
    foreach ($node in $Nodes) {
        if ($node.Tag -and !$node.Tag.IsDir -and $node.Checked) { $n++ }
        if ($node.Nodes.Count -gt 0) { $n += Get-CheckedCount $node.Nodes }
    }
    return $n
}

function Get-SelectedFiles {
    param([System.Windows.Forms.TreeNodeCollection]$Nodes, [System.Collections.Generic.List[PSCustomObject]]$Out)
    foreach ($node in $Nodes) {
        if ($node.Tag -and !$node.Tag.IsDir -and $node.Checked) { [void]$Out.Add($node.Tag) }
        if ($node.Nodes.Count -gt 0) { Get-SelectedFiles $node.Nodes $Out }
    }
}

# --- Refresh state helpers ---------------------------------------------------
# Snapshots checked/expanded paths before a rebuild, then restores them after.
# OrdinalIgnoreCase HashSets give O(1) lookups on Windows paths.

function Get-CheckedPaths {
    param([System.Windows.Forms.TreeNodeCollection]$Nodes, [System.Collections.Generic.HashSet[string]]$Paths)
    foreach ($node in $Nodes) {
        # Persist checked state for both files and directories
        if ($node.Tag -and $node.Checked) { [void]$Paths.Add($node.Tag.Full) }
        if ($node.Nodes.Count -gt 0) { Get-CheckedPaths $node.Nodes $Paths }
    }
}

function Get-ExpandedPaths {
    param([System.Windows.Forms.TreeNodeCollection]$Nodes, [System.Collections.Generic.HashSet[string]]$Paths)
    foreach ($node in $Nodes) {
        if ($node.Tag -and $node.Tag.IsDir -and $node.IsExpanded) {
            [void]$Paths.Add($node.Tag.Full)
        }
        if ($node.Nodes.Count -gt 0) { Get-ExpandedPaths $node.Nodes $Paths }
    }
}

# Called with $script:_checking = $true so .Checked assignments do not cascade.
function Set-RestoredState {
    param(
        [System.Windows.Forms.TreeNodeCollection]$Nodes,
        [System.Collections.Generic.HashSet[string]]$CheckedPaths,
        [System.Collections.Generic.HashSet[string]]$ExpandedPaths
    )
    foreach ($node in $Nodes) {
        if ($node.Tag) {
            if ($CheckedPaths.Contains($node.Tag.Full))                        { $node.Checked = $true }
            if ($node.Tag.IsDir -and $ExpandedPaths.Contains($node.Tag.Full)) { $node.Expand()        }
        }
        if ($node.Nodes.Count -gt 0) { Set-RestoredState $node.Nodes $CheckedPaths $ExpandedPaths }
    }
}

# --- Context writer ----------------------------------------------------------
# Builds the context string, writes it to disk, and returns it for clipboard use.
function Write-Context {
    param([System.Collections.Generic.List[PSCustomObject]]$Files, [string]$OutPath)

    $sb = [Text.StringBuilder]::new()
    for ($i = 0; $i -lt $Files.Count; $i++) {
        $f   = $Files[$i]
        $ext = [IO.Path]::GetExtension($f.Name).TrimStart('.').ToLower()
        if (!$ext) { $ext = 'text' }

        try   { $content = Read-FileText $f.Full }
        catch { $content = "[Error reading file: $_]" }

        [void]$sb.AppendLine($f.Rel)
        [void]$sb.AppendLine("$TICK3$ext")
        [void]$sb.AppendLine($(if ($content) { $content } else { '' }))
        [void]$sb.AppendLine($TICK3)
        if ($i -lt $Files.Count - 1) { [void]$sb.AppendLine('---') }
    }

    $text = $sb.ToString()
    [IO.File]::WriteAllText($OutPath, $text, [Text.Encoding]::UTF8)
    return $text
}

# --- Resolve root path -------------------------------------------------------
$resolved = Resolve-Path -LiteralPath $Path -ErrorAction SilentlyContinue
if (-not $resolved) {
    [System.Windows.Forms.MessageBox]::Show("Path not found:`n$Path", 'Context Builder', 'OK', 'Error') | Out-Null
    exit 1
}
$root     = $resolved.Path
$rootName = Split-Path -Leaf $root

# --- Form --------------------------------------------------------------------
$form = New-Object System.Windows.Forms.Form
$form.Text          = "Context Builder -- $rootName"
$form.Size          = New-Object Drawing.Size(820, 660)
$form.MinimumSize   = New-Object Drawing.Size(560, 420)
$form.BackColor     = $CLR.Bg
$form.ForeColor     = $CLR.Fg
$form.Font          = $FONT.Ui
$form.StartPosition = 'CenterScreen'
$form.KeyPreview    = $true

# --- Top bar: output path ----------------------------------------------------
$topBar = New-Object System.Windows.Forms.Panel
$topBar.Dock      = 'Top'
$topBar.Height    = 58
$topBar.BackColor = $CLR.Panel

$lblOut = New-Object System.Windows.Forms.Label
$lblOut.Text      = 'OUTPUT FILE'
$lblOut.AutoSize  = $true
$lblOut.Font      = $FONT.Sm
$lblOut.ForeColor = $CLR.Dim
$lblOut.Location  = New-Object Drawing.Point(16, 10)

$txtOut = New-Object System.Windows.Forms.TextBox
$txtOut.Text        = $Out
$txtOut.Font        = $FONT.Ui
$txtOut.BackColor   = $CLR.Surface
$txtOut.ForeColor   = $CLR.Fg
$txtOut.BorderStyle = 'FixedSingle'
$txtOut.Location    = New-Object Drawing.Point(16, 28)
$txtOut.Anchor      = $ANC_LTR

$btnBrowse = New-Object System.Windows.Forms.Button
$btnBrowse.Text      = 'Browse...'
$btnBrowse.Font      = $FONT.Sm
$btnBrowse.Width     = 82
$btnBrowse.Height    = 27
$btnBrowse.BackColor = $CLR.Surface
$btnBrowse.ForeColor = $CLR.Fg
$btnBrowse.FlatStyle = 'Flat'
$btnBrowse.Cursor    = 'Hand'
$btnBrowse.Anchor    = $ANC_TR
$btnBrowse.FlatAppearance.BorderColor = $CLR.Border

$topBar.Controls.AddRange(@($lblOut, $txtOut, $btnBrowse))

$layoutTop = {
    [int]$w  = $topBar.ClientSize.Width
    [int]$bw = $btnBrowse.Width
    $txtOut.Width       = $w - 16 - 8 - $bw - 16
    $btnBrowse.Location = New-Object Drawing.Point(($w - 16 - $bw), 27)
}
# Guard: skip layout during premature resize events fired before the form's
# Win32 handle is created (e.g. during Controls.AddRange). At that stage
# WinForms control properties return Object[] instead of Int32, breaking
# PowerShell arithmetic. IsHandleCreated becomes true right before Add_Shown.
$topBar.Add_Resize({ if ($form.IsHandleCreated) { & $layoutTop } })

# --- TreeView ----------------------------------------------------------------
$tree = New-Object System.Windows.Forms.TreeView
$tree.Dock        = 'Fill'
$tree.CheckBoxes  = $true
$tree.BackColor   = $CLR.Bg
$tree.ForeColor   = $CLR.Fg
$tree.BorderStyle = 'None'
$tree.Font        = $FONT.Ui
$tree.ShowLines   = $true
$tree.HotTracking = $true
$tree.ItemHeight  = 23
$tree.Scrollable  = $true
$tree.Indent      = 16

# --- Bottom bar --------------------------------------------------------------
$botSep = New-Object System.Windows.Forms.Panel
$botSep.Dock      = 'Bottom'
$botSep.Height    = 1
$botSep.BackColor = $CLR.Border

$botBar = New-Object System.Windows.Forms.Panel
$botBar.Dock      = 'Bottom'
$botBar.Height    = 54
$botBar.BackColor = $CLR.Panel

$lblStatus = New-Object System.Windows.Forms.Label
$lblStatus.Text      = '0 files selected'
$lblStatus.AutoSize  = $true
$lblStatus.Font      = $FONT.Sm
$lblStatus.ForeColor = $CLR.Dim
$lblStatus.Location  = New-Object Drawing.Point(16, 20)

$btnRefresh = New-Object System.Windows.Forms.Button
$btnRefresh.Text      = 'Refresh'
$btnRefresh.Font      = $FONT.Sm
$btnRefresh.Width     = 72
$btnRefresh.Height    = 30
$btnRefresh.BackColor = $CLR.Surface
$btnRefresh.ForeColor = $CLR.Dim
$btnRefresh.FlatStyle = 'Flat'
$btnRefresh.Cursor    = 'Hand'
$btnRefresh.Anchor    = $ANC_TR
$btnRefresh.FlatAppearance.BorderColor = $CLR.Border

$btnNone = New-Object System.Windows.Forms.Button
$btnNone.Text      = 'None'
$btnNone.Font      = $FONT.Sm
$btnNone.Width     = 64
$btnNone.Height    = 30
$btnNone.BackColor = $CLR.Surface
$btnNone.ForeColor = $CLR.Fg
$btnNone.FlatStyle = 'Flat'
$btnNone.Cursor    = 'Hand'
$btnNone.Anchor    = $ANC_TR
$btnNone.FlatAppearance.BorderColor = $CLR.Border

$btnAll = New-Object System.Windows.Forms.Button
$btnAll.Text      = 'All'
$btnAll.Font      = $FONT.Sm
$btnAll.Width     = 64
$btnAll.Height    = 30
$btnAll.BackColor = $CLR.Surface
$btnAll.ForeColor = $CLR.Fg
$btnAll.FlatStyle = 'Flat'
$btnAll.Cursor    = 'Hand'
$btnAll.Anchor    = $ANC_TR
$btnAll.FlatAppearance.BorderColor = $CLR.Border

$btnGen = New-Object System.Windows.Forms.Button
$btnGen.Text      = 'Generate ->'
$btnGen.Font      = $FONT.Hdr
$btnGen.Width     = 126
$btnGen.Height    = 34
$btnGen.BackColor = $CLR.Accent
$btnGen.ForeColor = $CLR.AccFg
$btnGen.FlatStyle = 'Flat'
$btnGen.Cursor    = 'Hand'
$btnGen.Enabled   = $false
$btnGen.Anchor    = $ANC_TR
$btnGen.FlatAppearance.BorderSize = 0

$botBar.Controls.AddRange(@($lblStatus, $btnRefresh, $btnNone, $btnAll, $btnGen))

$layoutBot = {
    # Build right-to-left: Generate -> All -> None -> Refresh, 8px gaps, 16px right margin
    [int]$x = $botBar.ClientSize.Width - 16
    $btnGen.Location     = New-Object Drawing.Point(($x - $btnGen.Width),     10)
    $x -= $btnGen.Width + 8
    $btnAll.Location     = New-Object Drawing.Point(($x - $btnAll.Width),     12)
    $x -= $btnAll.Width + 8
    $btnNone.Location    = New-Object Drawing.Point(($x - $btnNone.Width),    12)
    $x -= $btnNone.Width + 8
    $btnRefresh.Location = New-Object Drawing.Point(($x - $btnRefresh.Width), 12)
}
# Same guard as $layoutTop -- prevents premature-resize arithmetic failures.
$botBar.Add_Resize({ if ($form.IsHandleCreated) { & $layoutBot } })

# --- Flash timers ------------------------------------------------------------
# Briefly turns the Refresh button green after a successful rebuild.
$flashTimer          = New-Object System.Windows.Forms.Timer
$flashTimer.Interval = 500
$flashTimer.Add_Tick({
    $flashTimer.Stop()
    $btnRefresh.ForeColor = $CLR.Dim
})

# Briefly turns the Generate button green after a successful generate + copy.
$genFlashTimer          = New-Object System.Windows.Forms.Timer
$genFlashTimer.Interval = 1200
$genFlashTimer.Add_Tick({
    $genFlashTimer.Stop()
    $btnGen.BackColor = $CLR.Accent
    $btnGen.Text      = 'Generate ->'
})

# --- Status refresh ----------------------------------------------------------
$refreshStatus = {
    $n    = Get-CheckedCount $tree.Nodes
    $noun = if ($n -eq 1) { 'file' } else { 'files' }
    $lblStatus.Text      = "$n $noun selected"
    $lblStatus.ForeColor = if ($n -gt 0) { $CLR.Green } else { $CLR.Dim }
    $btnGen.Enabled      = ($n -gt 0)
}

# --- Tree refresh ------------------------------------------------------------
# Unified entry point for both first load and on-demand refresh.
# On first load ($script:_initialized = $false) top-level dirs are expanded by default.
# On refresh the prior checked + expanded state is fully restored; deleted items
# are naturally absent from the new tree and therefore not re-checked.
function Invoke-TreeRefresh {
    # Snapshot state before clearing (empty sets on first load -- that's intentional)
    $checkedPaths  = [System.Collections.Generic.HashSet[string]]::new([StringComparer]::OrdinalIgnoreCase)
    $expandedPaths = [System.Collections.Generic.HashSet[string]]::new([StringComparer]::OrdinalIgnoreCase)
    Get-CheckedPaths  $tree.Nodes $checkedPaths
    Get-ExpandedPaths $tree.Nodes $expandedPaths

    $btnRefresh.Enabled = $false
    $form.Cursor        = [System.Windows.Forms.Cursors]::WaitCursor

    # _checking suppresses AfterCheck cascades while nodes are set programmatically
    $script:_checking = $true
    try {
        $tree.BeginUpdate()
        $tree.Nodes.Clear()
        Add-ChildNodes $tree.Nodes $root $root   # InheritedPatterns defaults to @()

        if (-not $script:_initialized) {
            # First load: expand top-level directories by default
            foreach ($n in $tree.Nodes) { if ($n.Tag -and $n.Tag.IsDir) { $n.Expand() } }
        } else {
            # Subsequent refresh: restore prior expand + checked state
            Set-RestoredState $tree.Nodes $checkedPaths $expandedPaths
        }
        $tree.EndUpdate()
    } finally {
        $script:_checking   = $false
        $btnRefresh.Enabled = $true
        $form.Cursor        = [System.Windows.Forms.Cursors]::Default
    }

    & $refreshStatus

    # Visual acknowledgement (skip silent first load)
    if ($script:_initialized) {
        $btnRefresh.ForeColor = $CLR.Green
        $flashTimer.Stop()
        $flashTimer.Start()
    }
    $script:_initialized = $true
}

# --- Events ------------------------------------------------------------------

$tree.Add_AfterCheck({
    param($s, $e)
    if ($script:_checking) { return }
    $script:_checking = $true
    try {
        if ($e.Node.Tag -and $e.Node.Tag.IsDir) { Invoke-CheckCascade $e.Node $e.Node.Checked }
        & $refreshStatus
    } finally {
        $script:_checking = $false
    }
})

$btnBrowse.Add_Click({
    $dlg = New-Object System.Windows.Forms.SaveFileDialog
    $dlg.Title            = 'Save context file as...'
    $dlg.Filter           = 'Text files (*.txt)|*.txt|All files (*.*)|*.*'
    $dlg.FileName         = [IO.Path]::GetFileName($txtOut.Text)
    $dlg.InitialDirectory = if ([IO.Path]::IsPathRooted($txtOut.Text)) {
        [IO.Path]::GetDirectoryName($txtOut.Text)
    } else { (Get-Location).Path }
    if ($dlg.ShowDialog() -eq 'OK') { $txtOut.Text = $dlg.FileName }
})

$btnAll.Add_Click({
    $script:_checking = $true
    try   { Set-AllChecked $tree.Nodes $true }
    finally { $script:_checking = $false }
    & $refreshStatus
})

$btnNone.Add_Click({
    $script:_checking = $true
    try   { Set-AllChecked $tree.Nodes $false }
    finally { $script:_checking = $false }
    & $refreshStatus
})

$btnRefresh.Add_Click({ Invoke-TreeRefresh })

$generate = {
    $outPath = $txtOut.Text.Trim()
    if (!$outPath) {
        [System.Windows.Forms.MessageBox]::Show('Please specify an output file path.', 'Context Builder') | Out-Null
        return
    }
    if (![IO.Path]::IsPathRooted($outPath)) { $outPath = Join-Path (Get-Location) $outPath }

    $files = [System.Collections.Generic.List[PSCustomObject]]::new()
    Get-SelectedFiles $tree.Nodes $files

    if ($files.Count -eq 0) {
        [System.Windows.Forms.MessageBox]::Show('No files selected.', 'Context Builder') | Out-Null
        return
    }

    $btnGen.Enabled = $false
    $btnGen.Text    = 'Working...'
    $form.Cursor    = [System.Windows.Forms.Cursors]::WaitCursor
    [System.Windows.Forms.Application]::DoEvents()

    try {
        $text = Write-Context $files $outPath
        [System.Windows.Forms.Clipboard]::SetText($text)

        # Green flash confirms both save and copy without a blocking dialog
        $btnGen.BackColor = $CLR.Green
        $btnGen.Text      = 'Copied!'
        $genFlashTimer.Stop()
        $genFlashTimer.Start()

        [System.Windows.Forms.MessageBox]::Show(
                "Saved to:`n$outPath`n`n$($files.Count) file(s) bundled.`nContent copied to clipboard.",
                'Done!', 'OK', 'Information'
        ) | Out-Null
    } catch {
        [System.Windows.Forms.MessageBox]::Show("Error writing file:`n$_", 'Context Builder', 'OK', 'Error') | Out-Null
    } finally {
        $btnGen.Enabled = $true
        $form.Cursor    = [System.Windows.Forms.Cursors]::Default
    }
}

$btnGen.Add_Click($generate)

$form.Add_KeyDown({
    param($s, $e)
    if ($e.KeyCode -eq [System.Windows.Forms.Keys]::Return -and $btnGen.Enabled) {
        & $generate
        $e.Handled = $true
    }
    if ($e.KeyCode -eq [System.Windows.Forms.Keys]::F5) {
        Invoke-TreeRefresh
        $e.Handled = $true
    }
    if ($e.KeyCode -eq [System.Windows.Forms.Keys]::Escape) { $form.Close() }
})

# --- Assemble & run ----------------------------------------------------------
$form.Controls.AddRange(@($tree, $topBar, $botSep, $botBar))

$form.Add_Shown({
    & $layoutTop
    & $layoutBot
    Invoke-TreeRefresh
    $tree.Focus()
})

[System.Windows.Forms.Application]::Run($form)