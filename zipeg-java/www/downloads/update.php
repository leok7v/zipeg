<?php

$guid = $_GET['guid'];
$os = $_GET['os'];
$version = $_GET['version'];
$xcount = $_GET['xcount'];
$ucount = $_GET['ucount'];
$data = $_SERVER['QUERY_STRING'];

$link = mysql_connect('localhost', 'zipeg', 'dnt-nmw') or die('Could not connect: ' . mysql_error());
mysql_select_db('zipeg') or die('Could not select database');

$query = sprintf("replace into updates (guid,os,version,xcount,ucount,data) values ('%s','%s','%s','%s','%s','%s')",
  mysql_real_escape_string($guid),
  mysql_real_escape_string($os),
  mysql_real_escape_string($version),
  mysql_real_escape_string($xcount),
  mysql_real_escape_string($ucount),
  mysql_real_escape_string($data));

$result = mysql_query($query) or die('Query failed: ' . mysql_error());

//echo "<table>\n";
//while ($line = mysql_fetch_array($result, MYSQL_ASSOC)) {
//   echo "\t<tr>\n";
//   foreach ($line as $col_value) {
//       echo "\t\t<td>$col_value</td>\n";
//   }
//   echo "\t</tr>\n";
//}
//echo "</table>\n";

mysql_free_result($result);
mysql_close($link);

?>
