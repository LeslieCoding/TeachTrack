Option Explicit

Dim shell, fileSystem, project, command
Set shell = CreateObject("WScript.Shell")
Set fileSystem = CreateObject("Scripting.FileSystemObject")
project = fileSystem.GetParentFolderName(fileSystem.GetParentFolderName(WScript.ScriptFullName))

shell.Environment("Process")("GCM_INTERACTIVE") = "never"
shell.Environment("Process")("GIT_TERMINAL_PROMPT") = "0"
command = "pythonw.exe """ & project & "\scripts\agent_mailbox.py"" sync"
shell.Run command, 0, True
