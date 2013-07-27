function enableTooltips(id) {
    if (!document.getElementById || !document.getElementsByTagName) return;
    var h = document.createElement("span");
    h.id = "bubbleTooltip";
    h.setAttribute("id", "bubbleTooltip");
    h.style.position = "absolute";
    document.getElementsByTagName("body")[0].appendChild(h);
    var links =  id == null ? document.getElementsByTagName("a") :
                              document.getElementById(id).getElementsByTagName("a");
    for (var i = 0; i < links.length; i++) {
        prepare(links[i]);
    }
}

function prepare(el) {
    var winW = document.body.clientWidth;
    var winH = document.body.clientHeight;
    var tooltip,t,b,s,l;
    t = el.getAttribute("bubble");
    if (t == null || t.length == 0) {
        return;
    }
    el.removeAttribute("bubble");
    tooltip = createChild("span", "tooltip");
    s = createChild("span", "top");
    var div = document.createElement("div");
    s.appendChild(div);
    div.innerHTML = t;
    tooltip.appendChild(s);
    el.tooltip = tooltip;
    el.onmouseover = showTooltip;
}

function showTooltip(e) {
    var d = document.getElementById("bubbleTooltip");
    if (d.childNodes.length == 0) {
        document.getElementById("bubbleTooltip").appendChild(this.tooltip);
        setOpacity(this.tooltip);
        setLocation(e);
        this.tooltip.onmouseout = hideTooltip;
        this.tooltip.onkeydown = hideTooltip;
        this.tooltip.onclick = hideTooltip;
    }
}

var fadeout;

function hideTooltip(e) {
    var d = document.getElementById("bubbleTooltip");
    if (d.childNodes.length == 0) {
        return;
    }
    d.removeChild(d.firstChild);
    fadeout = null;
}

function setOpacity(el) {
   fadeout = el;
   incOpacity(25);
}

function incOpacity(o) {
    if (fadeout == null) {
        return;
    }
    setTransparency(fadeout, o);
    o += 5;
    if (o < 98) {
        self.setTimeout('incOpacity(' + o + ');', 20);
    } else {
        fadeout = null;
    }
}

function setTransparency(e, o) {
    var style = e.style;
    var p = (o / 100.0);
    style.filter = "alpha(opacity:" + o + ")";
    style.KHTMLOpacity = p;
    style.MozOpacity = p;
    style.opacity = p;
}


function createChild(t, c) {
    var x = document.createElement(t);
    x.className = c;
    x.style.display = "block";
    return(x);
}

function setLocation(e) {
    var posx = 0, posy = 0;
    if (e == null) e = window.event;
    var dx = window.pageXOffset;
    var dy = window.pageYOffset;
    var winW = 990; // window.innerWidth - dx;
    var winH = 600; // window.innerHeight - dy;
    if (e.pageX || e.pageY) {
        posx = e.pageX;
        posy = e.pageY;
    } else if (e.clientX || e.clientY) {
        if (document.documentElement.scrollTop) {
            posx = e.clientX + document.documentElement.scrollLeft;
            posy = e.clientY + document.documentElement.scrollTop;
        } else {
            posx = e.clientX + document.body.scrollLeft;
            posy = e.clientY + document.body.scrollTop;
        }
    }
    var bubble = document.getElementById("bubbleTooltip");
    var w = bubble.clientWidth;
    var h = bubble.clientHeight;

    // IE7 only do alpha for elements with specified pixel width:
    var tooltip = bubble.firstChild;
    tooltip.style.width = w + "px";

    var y = posy - h / 2;
    var x = posx - w / 2;
    if (x + w > winW) {
        x = winW - w;
    }
    if (y + h > winH) {
        y = winH - h;
    }
    if (x < dx) {
        x = dx;
    }
    if (y < dy) {
        y = dy;
    }
    if (x < 10) {
        x = 10;
    }
    if (y < 42) {
        y = 40;
    }
    bubble.style.top = y + "px";
    bubble.style.left = x + "px";
}