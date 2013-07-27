<?php
$lo = mysql_real_escape_string($_GET['lo']);
$hi = mysql_real_escape_string($_GET['hi']);
$conn = mysql_connect('localhost', 'zipeg', 'leo8pold') or die('connect failed' . mysql_error());
mysql_select_db('zipeg') or die('select_db failed' . mysql_error());
$result = mysql_query("select name, value from map where ($lo <= id) and (id < $hi) ");
while ($row = mysql_fetch_row($result)) {
    $n = $row[0];
    $v = $row[1];
    echo "\"$n\"," . "\"$v\"\r\n";
}
echo mysql_error();
mysql_close($conn)
?>
