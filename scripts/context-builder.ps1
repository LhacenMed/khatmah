# context-builder.ps1
# GUI context-file builder -- scrollable tree, mouse checkboxes, encoding-safe output.
# Live directory watching: tree auto-updates on file/folder creates, deletes, renames.
#
# Usage: .\context-builder.ps1 [-Path <dir>] [-Out <file.txt>] [-Hidden]
# Keys : ENTER -> Generate   ESC -> Close
#
# Example:
#   .\scripts\context-builder.ps1 -Path "C:\Users\lhacenmed\AndroidStudioProjects\Khatmah\"

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
    Bg        = [Drawing.Color]::FromArgb( 22,  22,  26)
    Panel     = [Drawing.Color]::FromArgb( 30,  30,  35)
    Surface   = [Drawing.Color]::FromArgb( 44,  44,  52)
    Border    = [Drawing.Color]::FromArgb( 58,  58,  68)
    Fg        = [Drawing.Color]::FromArgb(218, 218, 222)
    Dim       = [Drawing.Color]::FromArgb(112, 112, 124)
    Dir       = [Drawing.Color]::FromArgb( 96, 165, 250)
    File      = [Drawing.Color]::FromArgb(200, 200, 205)
    Green     = [Drawing.Color]::FromArgb( 74, 222, 128)
    Accent    = [Drawing.Color]::FromArgb( 96, 165, 250)
    AccFg     = [Drawing.Color]::White
    LivePulse = [Drawing.Color]::FromArgb(250, 204,  21)   # indicator flash on fs-change batch
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

# --- Tree population ---------------------------------------------------------
# Eagerly builds all TreeNodes in one pass inside BeginUpdate/EndUpdate.
function Add-ChildNodes {
    param([System.Windows.Forms.TreeNodeCollection]$Nodes, [string]$Folder, [string]$Root)

    $opts  = @{ LiteralPath = $Folder; Force = $Hidden.IsPresent; ErrorAction = 'SilentlyContinue' }
    $items = Get-ChildItem @opts |
            Sort-Object @{ Expression = { $_.PSIsContainer }; Descending = $true }, Name

    foreach ($item in $items) {
        $rel  = '/' + $item.FullName.Substring($Root.Length).TrimStart('\', '/').Replace('\', '/')
        $node = [System.Windows.Forms.TreeNode]::new($item.Name)
        $node.Tag = [PSCustomObject]@{
            Full  = $item.FullName
            Rel   = $rel
            IsDir = $item.PSIsContainer
            Name  = $item.Name
        }
        $node.ForeColor = if ($item.PSIsContainer) { $CLR.Dir } else { $CLR.File }
        if ($item.PSIsContainer) { Add-ChildNodes $node.Nodes $item.FullName $Root }
        [void]$Nodes.Add($node)
    }
}

# --- Selection helpers -------------------------------------------------------

$script:_checking = $false   # re-entrancy guard for AfterCheck cascade

function Cascade-Check {
    param([System.Windows.Forms.TreeNode]$Node, [bool]$State)
    foreach ($child in $Node.Nodes) {
        $child.Checked = $State
        if ($child.Nodes.Count -gt 0) { Cascade-Check $child $State }
    }
}

function Set-AllChecked {
    param([System.Windows.Forms.TreeNodeCollection]$Nodes, [bool]$State)
    foreach ($node in $Nodes) {
        $node.Checked = $State
        if ($node.Nodes.Count -gt 0) { Set-AllChecked $node.Nodes $State }
    }
}

function Count-Checked {
    param([System.Windows.Forms.TreeNodeCollection]$Nodes)
    $n = 0
    foreach ($node in $Nodes) {
        if ($node.Tag -and !$node.Tag.IsDir -and $node.Checked) { $n++ }
        if ($node.Nodes.Count -gt 0) { $n += Count-Checked $node.Nodes }
    }
    return $n
}

function Collect-Selected {
    param([System.Windows.Forms.TreeNodeCollection]$Nodes, [System.Collections.Generic.List[PSCustomObject]]$Out)
    foreach ($node in $Nodes) {
        if ($node.Tag -and !$node.Tag.IsDir -and $node.Checked) { [void]$Out.Add($node.Tag) }
        if ($node.Nodes.Count -gt 0) { Collect-Selected $node.Nodes $Out }
    }
}

# --- Live-watch helpers ------------------------------------------------------
# FileSystemWatcher fires on thread-pool threads. We enqueue raw events into a
# ConcurrentQueue and drain them on the UI thread via Control.BeginInvoke so
# that all TreeView mutations remain single-threaded.

$script:fsQueue = [System.Collections.Concurrent.ConcurrentQueue[PSCustomObject]]::new()

# Recursive node lookup by full filesystem path (case-insensitive).
function Find-NodeByPath {
    param([System.Windows.Forms.TreeNodeCollection]$Nodes, [string]$FullPath)
    foreach ($node in $Nodes) {
        if ($node.Tag -and $node.Tag.Full -ieq $FullPath) { return $node }
        if ($node.Nodes.Count -gt 0) {
            $hit = Find-NodeByPath $node.Nodes $FullPath
            if ($null -ne $hit) { return $hit }
        }
    }
    return $null
}

# Returns the insertion index that keeps dirs before files, each group alpha-sorted.
function Get-InsertIndex {
    param([System.Windows.Forms.TreeNodeCollection]$Nodes, [bool]$IsDir, [string]$Name)
    for ($i = 0; $i -lt $Nodes.Count; $i++) {
        $n = $Nodes[$i]
        if (-not $n.Tag) { continue }
        if ($IsDir -and -not $n.Tag.IsDir) { return $i }
        if ($IsDir -eq $n.Tag.IsDir -and [string]::Compare($Name, $n.Tag.Name, $true) -lt 0) { return $i }
    }
    return $Nodes.Count
}

# Recursively rewrite Tag.Full / Tag.Rel after a parent directory is renamed.
function Update-ChildPaths {
    param([System.Windows.Forms.TreeNode]$Node, [string]$OldBase, [string]$NewBase)
    foreach ($child in $Node.Nodes) {
        if ($child.Tag) {
            $nf = $NewBase + $child.Tag.Full.Substring($OldBase.Length)
            $child.Tag = [PSCustomObject]@{
                Full  = $nf
                Rel   = '/' + $nf.Substring($root.Length).TrimStart('\', '/').Replace('\', '/')
                IsDir = $child.Tag.IsDir
                Name  = $child.Tag.Name
            }
        }
        if ($child.Nodes.Count -gt 0) { Update-ChildPaths $child $OldBase $NewBase }
    }
}

# Snapshot checked-file paths into a HashSet before a full-tree rebuild.
function Collect-CheckedPaths {
    param([System.Windows.Forms.TreeNodeCollection]$Nodes, [System.Collections.Generic.HashSet[string]]$Out)
    foreach ($node in $Nodes) {
        if ($node.Tag -and -not $node.Tag.IsDir -and $node.Checked) { [void]$Out.Add($node.Tag.Full) }
        if ($node.Nodes.Count -gt 0) { Collect-CheckedPaths $node.Nodes $Out }
    }
}

# Re-apply checked state after a full-tree rebuild using the saved HashSet.
function Restore-CheckedPaths {
    param([System.Windows.Forms.TreeNodeCollection]$Nodes, [System.Collections.Generic.HashSet[string]]$Saved)
    foreach ($node in $Nodes) {
        if ($node.Tag -and -not $node.Tag.IsDir -and $Saved.Contains($node.Tag.Full)) {
            $node.Checked = $true
        }
        if ($node.Nodes.Count -gt 0) { Restore-CheckedPaths $node.Nodes $Saved }
    }
}

# Insert a new node for a path that just appeared on disk, in sorted position.
function Process-Created {
    param([string]$FullPath)
    if (-not (Test-Path -LiteralPath $FullPath -ErrorAction SilentlyContinue)) { return }
    $item = Get-Item -LiteralPath $FullPath -Force:($Hidden.IsPresent) -ErrorAction SilentlyContinue
    if (-not $item) { return }

    $parentPath  = [IO.Path]::GetDirectoryName($FullPath)
    $parentNodes = if ($parentPath -ieq $root) {
        $tree.Nodes
    } else {
        $p = Find-NodeByPath $tree.Nodes $parentPath
        if ($null -eq $p) { return }   # parent not currently visible in tree
        $p.Nodes
    }

    foreach ($n in $parentNodes) {                                          # skip duplicates
        if ($n.Tag -and $n.Tag.Full -ieq $FullPath) { return }
    }

    $rel  = '/' + $item.FullName.Substring($root.Length).TrimStart('\', '/').Replace('\', '/')
    $node = [System.Windows.Forms.TreeNode]::new($item.Name)
    $node.Tag = [PSCustomObject]@{
        Full  = $item.FullName
        Rel   = $rel
        IsDir = $item.PSIsContainer
        Name  = $item.Name
    }
    $node.ForeColor = if ($item.PSIsContainer) { $CLR.Dir } else { $CLR.File }

    $tree.BeginUpdate()
    $parentNodes.Insert((Get-InsertIndex $parentNodes $item.PSIsContainer $item.Name), $node)
    if ($item.PSIsContainer) { Add-ChildNodes $node.Nodes $item.FullName $root }   # populate if dir already has contents
    $tree.EndUpdate()
    & $refreshStatus
}

# Remove the node for a path that was deleted from disk.
function Process-Deleted {
    param([string]$FullPath)
    $node = Find-NodeByPath $tree.Nodes $FullPath
    if ($null -ne $node) {
        $tree.BeginUpdate()
        $node.Remove()
        $tree.EndUpdate()
        & $refreshStatus
    }
}

# Rename / move a node in-place; falls back to Process-Created if old path is unknown.
function Process-Renamed {
    param([string]$OldFull, [string]$NewFull)
    $node = Find-NodeByPath $tree.Nodes $OldFull
    if ($null -eq $node) { Process-Created $NewFull; return }   # moved in from outside

    $newName = [IO.Path]::GetFileName($NewFull)
    $newRel  = '/' + $NewFull.Substring($root.Length).TrimStart('\', '/').Replace('\', '/')

    $tree.BeginUpdate()
    if ($node.Tag.IsDir) { Update-ChildPaths $node $OldFull $NewFull }   # fix all descendant paths
    $node.Text = $newName
    $node.Tag  = [PSCustomObject]@{
        Full  = $NewFull
        Rel   = $newRel
        IsDir = $node.Tag.IsDir
        Name  = $newName
    }
    $tree.EndUpdate()
    & $refreshStatus
}

# Drain the fs-event queue -- always called on the UI thread via BeginInvoke.
function Flush-FsQueue {
    $ev      = $null
    $changed = $false
    while ($script:fsQueue.TryDequeue([ref]$ev)) {
        switch ($ev.Type) {
            'Created' { Process-Created $ev.Full              }
            'Deleted' { Process-Deleted $ev.Full              }
            'Renamed' { Process-Renamed $ev.OldFull $ev.Full  }
        }
        $changed = $true
    }
    if ($changed) {
        $lblLive.Text      = '↻ Updated'
        $lblLive.ForeColor = $CLR.LivePulse
        $flashTimer.Stop()
        $flashTimer.Start()
    }
}

# --- Context writer ----------------------------------------------------------
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
    [IO.File]::WriteAllText($OutPath, $sb.ToString(), [Text.Encoding]::UTF8)
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

# Live-watch indicator: steady green while watching, pulses yellow on each change batch.
$lblLive = New-Object System.Windows.Forms.Label
$lblLive.Text      = '● Watching'
$lblLive.AutoSize  = $true
$lblLive.Font      = $FONT.Sm
$lblLive.ForeColor = $CLR.Green
$lblLive.Location  = New-Object Drawing.Point(16, 20)   # overridden in layoutBot
$lblLive.Cursor    = 'Default'

$ttip = New-Object System.Windows.Forms.ToolTip
$ttip.SetToolTip($lblLive, 'Watching for file system changes — tree updates automatically')

# Resets the live indicator back to steady green after the pulse duration.
$flashTimer = New-Object System.Windows.Forms.Timer
$flashTimer.Interval = 900
$flashTimer.Add_Tick({
    $flashTimer.Stop()
    $lblLive.Text      = '● Watching'
    $lblLive.ForeColor = $CLR.Green
})

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

$botBar.Controls.AddRange(@($lblStatus, $lblLive, $btnNone, $btnAll, $btnGen))

$layoutBot = {
    [int]$w  = $botBar.ClientSize.Width
    [int]$wG = $btnGen.Width
    [int]$wA = $btnAll.Width
    [int]$wN = $btnNone.Width
    $btnGen.Location  = New-Object Drawing.Point(($w - 16 - $wG),                    10)
    $btnAll.Location  = New-Object Drawing.Point(($w - 24 - $wG - $wA),              12)
    $btnNone.Location = New-Object Drawing.Point(($w - 32 - $wG - $wA - $wN),        12)
    # live indicator: right-aligned just before the action buttons
    $lblLive.Location = New-Object Drawing.Point(($w - 40 - $wG - $wA - $wN - $lblLive.PreferredWidth), 20)
}
# Same guard as $layoutTop -- prevents premature-resize arithmetic failures.
$botBar.Add_Resize({ if ($form.IsHandleCreated) { & $layoutBot } })

# --- Status refresh ----------------------------------------------------------
$refreshStatus = {
    $n    = Count-Checked $tree.Nodes
    $noun = if ($n -eq 1) { 'file' } else { 'files' }
    $lblStatus.Text      = "$n $noun selected"
    $lblStatus.ForeColor = if ($n -gt 0) { $CLR.Green } else { $CLR.Dim }
    $btnGen.Enabled      = ($n -gt 0)
}

# --- Events ------------------------------------------------------------------

$tree.Add_AfterCheck({
    param($s, $e)
    if ($script:_checking) { return }
    $script:_checking = $true
    try {
        if ($e.Node.Tag -and $e.Node.Tag.IsDir) { Cascade-Check $e.Node $e.Node.Checked }
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

$generate = {
    $outPath = $txtOut.Text.Trim()
    if (!$outPath) {
        [System.Windows.Forms.MessageBox]::Show('Please specify an output file path.', 'Context Builder') | Out-Null
        return
    }
    if (![IO.Path]::IsPathRooted($outPath)) { $outPath = Join-Path (Get-Location) $outPath }

    $files = [System.Collections.Generic.List[PSCustomObject]]::new()
    Collect-Selected $tree.Nodes $files

    if ($files.Count -eq 0) {
        [System.Windows.Forms.MessageBox]::Show('No files selected.', 'Context Builder') | Out-Null
        return
    }

    $btnGen.Enabled = $false
    $btnGen.Text    = 'Working...'
    $form.Cursor    = [System.Windows.Forms.Cursors]::WaitCursor
    [System.Windows.Forms.Application]::DoEvents()

    try {
        Write-Context $files $outPath
        [System.Windows.Forms.MessageBox]::Show(
                "Saved to:`n$outPath`n`n$($files.Count) file(s) bundled.",
                'Done!', 'OK', 'Information'
        ) | Out-Null
    } catch {
        [System.Windows.Forms.MessageBox]::Show("Error writing file:`n$_", 'Context Builder', 'OK', 'Error') | Out-Null
    } finally {
        $btnGen.Text    = 'Generate ->'
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
    if ($e.KeyCode -eq [System.Windows.Forms.Keys]::Escape) { $form.Close() }
})

# --- Assemble & run ----------------------------------------------------------
$form.Controls.AddRange(@($tree, $topBar, $botSep, $botBar))

$form.Add_Shown({
    & $layoutTop
    & $layoutBot

    $tree.BeginUpdate()
    Add-ChildNodes $tree.Nodes $root $root
    foreach ($n in $tree.Nodes) { if ($n.Tag -and $n.Tag.IsDir) { $n.Expand() } }
    $tree.EndUpdate()

    & $refreshStatus
    $tree.Focus()

    # --- Start live directory watcher (structural events only: create / delete / rename) ---
    # NotifyFilter is intentionally narrow -- content changes don't affect tree structure.
    $script:watcher = New-Object System.IO.FileSystemWatcher
    $script:watcher.Path                  = $root
    $script:watcher.IncludeSubdirectories = $true
    $script:watcher.NotifyFilter          = [IO.NotifyFilters]::FileName -bor
            [IO.NotifyFilters]::DirectoryName
    $script:watcher.SynchronizingObject   = $null   # fire on thread-pool; we marshal via BeginInvoke

    # Created and Deleted share one handler; ChangeType.ToString() yields 'Created' or 'Deleted'.
    $fsHandler = [System.IO.FileSystemEventHandler]{
        param($s, $e)
        $script:fsQueue.Enqueue([PSCustomObject]@{ Type = $e.ChangeType.ToString(); Full = $e.FullPath; OldFull = $null })
        [void]$form.BeginInvoke([System.Windows.Forms.MethodInvoker]{ Flush-FsQueue })
    }

    $fsRenameHandler = [System.IO.RenamedEventHandler]{
        param($s, $e)
        $script:fsQueue.Enqueue([PSCustomObject]@{ Type = 'Renamed'; Full = $e.FullPath; OldFull = $e.OldFullPath })
        [void]$form.BeginInvoke([System.Windows.Forms.MethodInvoker]{ Flush-FsQueue })
    }

    # Buffer overflow (too many rapid events): snapshot checked paths, rebuild tree, restore state.
    $fsErrorHandler = [System.IO.ErrorEventHandler]{
        param($s, $e)
        [void]$form.BeginInvoke([System.Windows.Forms.MethodInvoker]{
            $saved = [System.Collections.Generic.HashSet[string]]::new([StringComparer]::OrdinalIgnoreCase)
            Collect-CheckedPaths $tree.Nodes $saved
            $script:watcher.EnableRaisingEvents = $false
            $tree.BeginUpdate()
            $tree.Nodes.Clear()
            Add-ChildNodes $tree.Nodes $root $root
            foreach ($n in $tree.Nodes) { if ($n.Tag -and $n.Tag.IsDir) { $n.Expand() } }
            $script:_checking = $true
            Restore-CheckedPaths $tree.Nodes $saved
            $script:_checking = $false
            $tree.EndUpdate()
            & $refreshStatus
            $script:watcher.EnableRaisingEvents = $true
        })
    }

    $script:watcher.add_Created($fsHandler)
    $script:watcher.add_Deleted($fsHandler)
    $script:watcher.add_Renamed($fsRenameHandler)
    $script:watcher.add_Error($fsErrorHandler)
    $script:watcher.EnableRaisingEvents = $true
})

$form.Add_FormClosed({
    $script:watcher.EnableRaisingEvents = $false
    $script:watcher.Dispose()
    $flashTimer.Dispose()
})

[System.Windows.Forms.Application]::Run($form)