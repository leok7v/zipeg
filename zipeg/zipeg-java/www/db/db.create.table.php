<?php
$conn = mysql_connect('localhost', 'zipeg', 'leo8pold') 
	or die('connect failed');
mysql_select_db('zipeg');
mysql_query('create table map(id int not null auto_increment,name varchar(32) not null,value varchar(255), primary key(id), key(name));') or die('creating table failed');
mysql_close($conn)
?>
