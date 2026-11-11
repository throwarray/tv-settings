package com.tvsettings.app

import android.annotation.SuppressLint
import android.app.Activity
import android.os.Bundle
import android.webkit.JavascriptInterface
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.activity.ComponentActivity

/** Picks mock-location coordinates on an OpenStreetMap map. Leaflet is loaded from unpkg. */
class MapPickerActivity : ComponentActivity() {
    @SuppressLint("SetJavaScriptEnabled")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val (lat, lon) = MockLocation.coordinates(this)
        val web = WebView(this)
        web.settings.javaScriptEnabled = true
        web.webViewClient = WebViewClient()
        web.addJavascriptInterface(Bridge(), "Android")
        web.loadDataWithBaseURL("https://tvsettings.local/", page(lat, lon), "text/html", "utf-8", null)
        setContentView(web)
    }

    private inner class Bridge {
        @JavascriptInterface
        fun pick(lat: Double, lon: Double) {
            MockLocation.setCoordinates(this@MapPickerActivity, lat, lon)
            runOnUiThread {
                setResult(Activity.RESULT_OK)
                finish()
            }
        }
    }

    // Buttons pan and zoom so the whole map works with a D-pad; the pin marks the centre.
    private fun page(lat: Double, lon: Double) = """
<!doctype html><html><head><meta name="viewport" content="width=device-width,initial-scale=1">
<link rel="stylesheet" href="https://unpkg.com/leaflet@1.9.4/dist/leaflet.css">
<script src="https://unpkg.com/leaflet@1.9.4/dist/leaflet.js"></script>
<style>
html,body{margin:0;height:100%;background:#000;color:#fff;font-family:sans-serif}
#map{position:absolute;top:0;bottom:64px;left:0;right:0}
#bar{position:absolute;bottom:0;left:0;right:0;height:64px;display:flex;gap:8px;align-items:center;justify-content:center;background:#000}
button{background:#151515;color:#fff;border:2px solid transparent;border-radius:8px;padding:0 14px;height:44px;font-size:16px}
button:focus{outline:none;border-color:#0A84FF}
#pin{position:absolute;left:50%;top:50%;width:14px;height:14px;margin:-7px 0 0 -7px;border-radius:50%;background:#fff;border:2px solid #000;pointer-events:none;z-index:999}
</style></head><body>
<div id="map"></div><div id="pin"></div>
<div id="bar">
<button onclick="pan(-1,0)">&#9664;</button><button onclick="pan(0,-1)">&#9650;</button><button onclick="pan(0,1)">&#9660;</button><button onclick="pan(1,0)">&#9654;</button>
<button onclick="map.zoomIn()">+</button><button onclick="map.zoomOut()">&minus;</button>
<button id="ok" onclick="done()">Use this spot</button>
</div>
<script>
var map = L.map('map', {zoomControl:false}).setView([$lat,$lon], 13);
L.tileLayer('https://tile.openstreetmap.org/{z}/{x}/{y}.png', {maxZoom:19, attribution:'&copy; OpenStreetMap contributors'}).addTo(map);
function pan(dx,dy){ map.panBy([dx*120, dy*120], {animate:false}); }
function done(){ var c = map.getCenter(); Android.pick(c.lat, c.lng); }
document.getElementById('ok').focus();
</script></body></html>
"""
}
