<?php
$conn = mysql_connect('localhost', 'zipeg', 'leo8pold') 
	or die('connect failed');
mysql_select_db('zipeg');
$n = mysql_real_escape_string($_GET['name']);
$v = mysql_real_escape_string($_GET['value']);
mysql_query("insert into map(name, value) values ('$n', '$v')") or die('insert failed');
mysql_close($conn)
?>
