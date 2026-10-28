# Switch 2 Waker

Wake a sleeping **Nintendo Switch 2 from Home Assistant** - no Pro Controller 2 needed, no getting off the couch to press the power button.

A cheap ESP32 (an **M5Stack Atom Echo** or any ESP32 dev board) learns your Joy-Con 2, takes over its Bluetooth address and broadcasts the same BLE packet the Joy-Con sends when you press **HOME**. The console wakes up as if you pressed the button yourself.

**What's in the box**

| | |
|---|---|
| 🔌 **Firmware** | ESPHome, flash from the browser, one-click Joy-Con pairing from HA |
| 📘 **Blueprint: wake triggers** | webhook (for the TV app) + voice sentences for Assist |
| 📺 **Blueprint: TV input** | turns the TV on and switches it to the Switch's HDMI after waking |
| 📱 **Google TV / Android TV app** | a "Switch 2" tile next to Netflix: wakes the console and jumps to its HDMI input |

> Credit: the wake packet was reverse-engineered by [alexvnesta/switch2controller](https://github.com/alexvnesta/switch2controller). This project packages it for Home Assistant.

---

## 1. Flash the firmware

**Easiest:** open **[https://amdydesign.github.io/switch2-waker](https://amdydesign.github.io/switch2-waker)** in Chrome/Edge, plug in the ESP32 over USB, click **Install**, then enter your Wi-Fi. Home Assistant discovers the device automatically (Settings → Devices → ESPHome).

Supported out of the box:
- **M5Stack Atom Echo** - button = wake, LED glows blue while broadcasting (great reuse for an old voice satellite)
- **Generic ESP32** (ESP32-WROOM / DevKitC) - BOOT button = wake, on-board LED on GPIO2

<details><summary>Using the ESPHome dashboard / your own YAML instead</summary>

Click **Adopt** in the ESPHome dashboard, or add the package to your config:

```yaml
substitutions:
  name: switch2-waker
  friendly_name: Switch 2 Waker
  name_add_mac_suffix: "false"

packages:
  switch2_waker: github://amdydesign/switch2-waker/atom-echo.yaml@main   # or esp32-generic.yaml

api:
  encryption:
    key: !secret switch2_api_key
```

Any ESP32 with classic BLE works (ESP32, S3, C3, C6). Device configs only add the board, a button and an LED - see [`esp32-generic.yaml`](esp32-generic.yaml).
</details>

## 2. Teach it your Joy-Con (once)

1. Put the Switch 2 to sleep.
2. Detach a Joy-Con 2, hold it near the ESP32 and press **HOME**.
3. In Home Assistant, the **Last Nintendo packet** sensor should show `... [0x81 OK]`.
4. Press **Save captured Joy-Con**. The device restarts with the Joy-Con's address.
5. Attach the Joy-Con back to the console.

Now **Wake Switch** (HA button or the physical button) wakes the console. 🎉

> The ESP32 uses the Joy-Con's address only for advertising - it never connects to the console. Keep the ESP32 within a few meters of the Switch.

## 3. Blueprints

[![Import wake triggers blueprint](https://my.home-assistant.io/badges/blueprint_import.svg)](https://my.home-assistant.io/redirect/blueprint_import/?blueprint_url=https%3A%2F%2Fgithub.com%2Famdydesign%2Fswitch2-waker%2Fblob%2Fmain%2Fblueprints%2Fautomation%2Fswitch2_wake_triggers.yaml) **Wake triggers** - webhook for the TV app + Assist voice sentences (default: *"wake up the switch"*, *"turn on the nintendo switch"*).

[![Import TV input blueprint](https://my.home-assistant.io/badges/blueprint_import.svg)](https://my.home-assistant.io/redirect/blueprint_import/?blueprint_url=https%3A%2F%2Fgithub.com%2Famdydesign%2Fswitch2-waker%2Fblob%2Fmain%2Fblueprints%2Fautomation%2Fswitch2_tv_input.yaml) **Switch TV input** - after the wake packet goes out (from any source), optionally turns the TV on and runs your "select HDMI" actions.

Input switching examples:

```yaml
# Most TVs (LG, Samsung, Sony, Philips, ...)
- action: media_player.select_source
  target: {entity_id: media_player.living_room_tv}
  data: {source: HDMI 1}

# Android / Google TV via the ADB integration
- action: androidtv.adb_command
  target: {entity_id: media_player.tv_adb}
  data:
    command: am start -a android.intent.action.VIEW -d content://android.media.tv/passthrough/<input id>
# find <input id> with: adb shell dumpsys tv_input   (e.g. com.tcl.tvinput%2F.TvPassThroughService%2FHW15)
```

## 4. Google TV / Android TV app (optional)

A tiny app (~30 KB) that adds a **Switch 2** tile to your home screen. Selecting it:
1. calls the Home Assistant webhook (from the *wake triggers* blueprint),
2. switches the TV to the Switch's HDMI input **instantly** (no ADB needed),
3. closes itself.

**Install:** download [`switch2-waker.apk`](https://github.com/amdydesign/switch2-waker/releases/latest/download/switch2-waker.apk) and sideload it (e.g. *Send Files to TV*, *Downloader*, or `adb install switch2-waker.apk`).

**Setup:** open the **Switch 2 setup** tile → enter your HA address (e.g. `http://192.168.1.10:8123`) and the webhook ID from the blueprint → pick the HDMI input → **Save & test**. Then move the **Switch 2** tile to your favourites row.

> The webhook only works from your local network, and the app stores no Home Assistant token. Built from [`android/`](android) with plain `aapt2` + `javac` + `d8` (no Gradle).

---

## Entities

| Entity | |
|---|---|
| `button` Wake Switch | broadcasts the wake packet for 2 s |
| `binary_sensor` Waking | on while broadcasting - use as an automation trigger |
| `button` Save captured Joy-Con / Forget Joy-Con | pairing |
| `sensor` Last Nintendo packet | last BLE packet seen from a Joy-Con (`[0x81 OK]` = wake packet) |
| `text` Joy-Con address / Wake packet | saved values (you can also paste them manually) |
| `sensor` BT address, BLE adverts per minute | diagnostics |

## Troubleshooting

- **Nothing in "Last Nintendo packet"** - the Joy-Con must be *detached* and the console *asleep*; press HOME a few times close to the ESP32.
- **Wake does nothing** - check that *BT address* equals the saved Joy-Con address. Keep the ESP32 closer to the console.
- **Cheap "CSR 4.0" USB dongles** can't change their Bluetooth address (most are Barrot clones), which is why this uses an ESP32.

## How it works

A sleeping Switch 2 scans for a 31-byte BLE advertisement with Nintendo's company ID `0x0553`, the controller's product ID, state byte `0x81` and the console's own BT address - sent **from the address of a paired controller**. ESP-IDF lets us override the BT MAC with `esp_iface_mac_addr_set(mac, ESP_MAC_BT)` before the BLE stack starts, so the ESP32 becomes indistinguishable from the Joy-Con for that one packet.

## License

MIT. Not affiliated with Nintendo. "Nintendo Switch" and "Joy-Con" are trademarks of Nintendo.
