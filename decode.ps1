$text="登录已过期，请重新登�?";
$bytes=[System.Text.Encoding]::UTF8.GetBytes($text);
[System.Text.Encoding]::GetEncoding("GBK").GetString($bytes)
