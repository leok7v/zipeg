<?php
    if (!empty($_POST['subject']) && !empty($_POST['email']) &&
        !empty($_POST['name']) && !empty($_POST['body']) &&
	 stristr($_POST['body'], '<a href =') === FALSE &&
	 stristr($_POST['body'], '<a href=') === FALSE) {
        mail("support@zipeg.com", $_POST['subject'], $_POST['body'],
             'From: ' . $_POST['name'] . ' <' . $_POST['email'] . '>');
?>
<html>
    <head>
        <title>Zipeg</title>
        <meta HTTP-EQUIV="REFRESH" content="3; url=index.html">
    </head>
    <body><br><br><br><br>
    <center><h2>Your email has been sent. Thank you.</h2></center>
    <a href="index.html">Back</a>
    </body>
    <html>
<?php
} else {
?>
<html>
    <head>
        <title>Zipeg</title>
        <meta HTTP-EQUIV="REFRESH" content="10; url=support.html">
    </head>
    <body><br><br><br><br>
    <font color=red><center><h2>
    Your must fill all the fields<br>
    and provide your email address.<br>
    How can we get back to you, otherwise?<br>
    </h2></center></font>
    <a href="support.html">Back</a>
    </body>
    <html>
<?php
}
?>
