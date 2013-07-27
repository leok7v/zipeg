String.prototype.endsWith = function(s) {
    var reg = new RegExp(s + "$");
    return reg.test(this);
}

function syncMenu(hr) {
    try {
        if (hr != null) {
            var list = document.getElementsByTagName("a");
            for (var i = 0; i < list.length; i++) {
                removeClass(list[i], "active");
                if (list[i].href.endsWith(hr)) {
                    list[i].className += " active";
                    list[i].blur();
                    //alert("active[" + i + "]= class:" + list[i].className + " href=" + list[i].href + " hr=" + hr);
                }
            }
        }
    } catch (e) {
        alert(e);
    }
}

function removeClass(e, name) {
    var nlist = new Array();
    var clist = e.className.split(" ");
    for (var i = 0; i < clist.length; i++) {
        if (clist[i] != name) {
            nlist.push(clist[i]);
        }
    }
    e.className = nlist.join(" ");
}

function getPlatformHome(url) {
    var agt = navigator.userAgent.toLowerCase();
    var is_mac = (agt.indexOf("mac") != -1);
    var is_win = (agt.indexOf("win") != -1);
    if (is_mac) {
        url = "mac." + url;
    }
    else if (is_win) {
        url = "win." + url;
    }
    //alert("getPlatformHome()=" + url);
    return url;
}

function frameLoad() {
    syncMenu(document.getElementById("content").src);
}

var initMain = {
    init: function() {
        frames['contentFrame'].location.href = getPlatformHome("home.html");
        //alert("iframe.src=" + frames['contentFrame'].location.href);
        var links = document.getElementsByTagName('a');
        for (var i = 0; i < links.length; i++) {
            if (links[i].target == "contentFrame") {
                links[i].onclick = this.onclick;
                links[i].onfocus = this.onfocus;
            }
        }
    },
    onclick: function() {
        //alert("old href=" + this.href);
        syncMenu(this.href);
        if (this.href.endsWith("home.html") || this.href.endsWith("blank.html")) {
            frames['contentFrame'].location.href = getPlatformHome("home.html");
            document.location.href = "http://www.zipeg.com";
            return false;
        } else  if (this.href.endsWith("screenshots.html")) {
            frames['contentFrame'].location.href = getPlatformHome("screenshots.html");
            this.href = getPlatformHome("screenshots.html");
            return false;
        }
        return true;
    },
    onfocus: function() {
        this.blur();
        /* remove focus dotted rectangle */
    }
}

function bookmark(alt, img, url, title) {
    var t = "";
    if (title && title != "") {
        t = title + escape(document.title);
    }
    var s = "<a target=_blank href='" + url + escape(document.location.href) + t +
            "'><img border=0 src=" + img + " alt='" + alt + "' title='" + alt + "' />" +
            "</a>&nbsp;";
    document.write(s);
}
