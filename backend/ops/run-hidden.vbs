Option Explicit

Dim shell, fileSystem, backend, mode, command
Set shell = CreateObject("WScript.Shell")
Set fileSystem = CreateObject("Scripting.FileSystemObject")
backend = fileSystem.GetParentFolderName(fileSystem.GetParentFolderName(WScript.ScriptFullName))

If WScript.Arguments.Count <> 1 Then WScript.Quit 1
mode = LCase(WScript.Arguments.Item(0))
Select Case mode
    Case "backend"
        command = "powershell.exe -NoProfile -ExecutionPolicy Bypass -WindowStyle Hidden -File """ & backend & "\ops\run-local.ps1"""
    Case "tunnel"
        command = """D:\SoftWare\cloudflared\cloudflared.exe"" --config ""D:\SoftWare\cloudflared\config.yml"" tunnel run teachtrack"
    Case Else
        WScript.Quit 1
End Select

shell.Run command, 0, False
