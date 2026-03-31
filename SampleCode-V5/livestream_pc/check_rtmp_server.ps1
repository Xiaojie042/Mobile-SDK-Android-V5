$ErrorActionPreference = "Stop"

$ports = @(1935, 1985, 8080)

foreach ($port in $ports) {
    $result = Test-NetConnection -ComputerName 127.0.0.1 -Port $port -WarningAction SilentlyContinue
    [PSCustomObject]@{
        Port = $port
        Listening = $result.TcpTestSucceeded
    }
}
