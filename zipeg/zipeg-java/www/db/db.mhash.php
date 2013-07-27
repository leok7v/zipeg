<?php
	function hmac ($key, $data) {
		// RFC 2104 HMAC implementation for php.
		// Creates an md5 HMAC.
		// Eliminates the need to install mhash to compute a HMAC
		// Hacked by Lance Rushing
		$b = 64; // byte length for md5
		if (strlen($key) > $b) {
			$key = pack("H*",md5($key));
		}
		$key  = str_pad($key, $b, chr(0x00));
		$ipad = str_pad('', $b, chr(0x36));
		$opad = str_pad('', $b, chr(0x5c));
		$k_ipad = $key ^ $ipad ;
		$k_opad = $key ^ $opad;
		return md5($k_opad  . pack("H*",md5($k_ipad . $data)));
	}
	
	// bin2hex(mhash(MHASH_MD5, "Jefe", "what do ya want for nothing?"));
	$sign = hmac("Jefe", "what do ya want for nothing?");
	echo "\"Jefe\", \"what do ya want for nothing?\"";
	echo $sign;
	$sign = hmac("foo", "bar");
	echo "\"foo\", \"bar\"" . "<br>\r\n";
	echo $sign . "<br>\r\n";
  	echo "time()=" . time() . "<br>\r\n";
  	echo "time()=" . (int)floor(time() / (3600 * 48)) . " double days<br>\r\n";
/*
	http://us2.php.net/function.mhash
	http://www.softwaresecretweapons.com/jspwiki/md5_for_ajax

	"Jefe", "what do ya want for nothing?"
	750c783e6ab0b503eaa86e310a5db738
	750c783e6ab0b503eaa86e310a5db738

	"foo", "bar"
	0c7a250281315ab863549f66cd8a3a53
	0c7a250281315ab863549f66cd8a3a53

	"bar", "foo"
	31b6db9e5eb4addb42f1a6ca07367adc
*/
?>
