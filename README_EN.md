<div align="center">

# 🖼️ Smart Photo Frame

**Turn your dusty old tablets and phones into an elegant, local-first digital art frame.**

🌐 **[English](README_EN.md)** • **[简体中文](README.md)**

[![GitHub release](https://img.shields.io/github/v/release/ccisnice/smart-photo-frame?style=flat-square&color=blue)](https://github.com/ccisnice/smart-photo-frame/releases)
[![License: GPL v3](https://img.shields.io/badge/License-GPLv3-green.svg?style=flat-square)](https://www.gnu.org/licenses/gpl-3.0)
[![Platform](https://img.shields.io/badge/Platform-Android%20%7C%20iOS%20%7C%20Web%20%7C%20Docker-orange?style=flat-square)](#)
[![Afdian](https://img.shields.io/badge/Donate-Afdian-946ce6.svg?style=flat-square)](https://afdian.com/a/cash1985)
[![Ko-fi](https://img.shields.io/badge/Ko--fi-Buy%20me%20a%20coffee-ff5e5b.svg?style=flat-square)](https://ko-fi.com/chenxu1985)
[![PRs Welcome](https://img.shields.io/badge/PRs-welcome-brightgreen.svg?style=flat-square)](https://github.com/ccisnice/smart-photo-frame/pulls)

<br/>

<img src="./assets/promo_cover.jpg" width="600" alt="Smart Photo Frame Live Demo" style="border-radius: 12px; box-shadow: 0 8px 30px rgba(0,0,0,0.25);" />

<br/><br/>

[📥 Download Android APK](https://github.com/ccisnice/smart-photo-frame/releases/latest) • [✨ Highlights](#-highlights) • [📱 Quick Start](#-quick-start) • [🐳 Docker Deployment](#-docker--nas-deployment) • [☕ Support the Author](#-support--donate)

</div>

---

### 💡 Why Smart Photo Frame?

Most households have **retired iPads or forgotten Android tablets** tucked away in drawers. Selling them yields almost nothing, yet throwing them away feels wasteful. Commercial digital photo frames often cost $100-$300, display ads, and compromise family privacy through compulsory cloud sync.

**Smart Photo Frame is built to revive your old devices:**
- **Zero extra hardware to buy**.
- **Zero complex servers required**.
- **Instant QR code photo casting** directly from any smartphone.
- **100% peer-to-peer local network transmission** — zero cloud risk, total privacy for your family!

---

## ✨ Highlights

- 🚀 **Standalone Native Microserver (Zero PC Dependency)**:
  The Android app includes a built-in, lightweight HTTP/streaming server. The tablet *is* the host. No dedicated computer or external server needed.
- 📱 **Frictionless QR Code Upload**:
  Tap the top-right corner of the frame to display a dedicated QR code. Anyone on the same Wi-Fi can scan it using their native camera to instantly upload photos/videos via a web browser — **no app installation required for senders**.
- ⚡ **Client-Side 2K Smart Compression**:
  When uploading massive 10MB–20MB photos from modern smartphones, the sender's browser automatically compresses them to 2K Retina resolution, **slashing file size by 90%–95%** while preserving sharp detail. Fast transmission and minimal storage strain on older tablets.
- 🎨 **Dynamic Ambient Gaussian Blur Glow**:
  Say goodbye to hideous black bars when portrait photos or videos play on a landscape tablet! GPU-accelerated shaders sample edge colors to generate smooth, aesthetic blurred ambient wings.
- 🔄 **360° Full Sensor Auto-Orientation**:
  Seamlessly flips between landscape, portrait, and upside-down orientations in milliseconds using hardware gravity sensors.
- 🕶️ **Pure Immersive Frame Mode**:
  System status bars (clock, battery, Wi-Fi) and navigation buttons are completely hidden. Dual wake-lock (Native Android + Web Screen Wake Lock API) prevents screen timeout indefinitely while plugged in.
- 🔒 **100% Local-First Privacy**:
  Zero cloud accounts, zero tracking, zero external telemetry. All media remains sandboxed on your local devices.

---

## 📱 Quick Start

### Option A: Android Tablet / Old Android Phone (Recommended ⭐⭐⭐⭐⭐)
1. Go to **[Releases](https://github.com/ccisnice/smart-photo-frame/releases/latest)** and download `SmartPhotoFrame-v1.1.1-release.apk` (or `智能相框-v1.1.1-正式签名版.apk`).
2. Install and launch the app on your tablet. It will start the local photo server automatically.
3. Tap the QR code icon in the top-right corner, scan with any smartphone on the same Wi-Fi, and start casting photos!

### Option B: Apple iPad (iOS / iPadOS)
1. Open iPad Safari and navigate to your local host URL (e.g., hosted on your Mac/PC or NAS).
2. Tap the Safari Share icon -> **"Add to Home Screen"**.
3. Launch from the Home Screen for a borderless, full-screen PWA photo frame experience.

### Option C: Docker / NAS Deployment (Synology / QNAP / unRAID / fnOS)
For households with a 24/7 home server or NAS:
```bash
docker run -d \
  --name photo-frame \
  -p 8000:8000 \
  -v /path/to/your/photos:/app/media \
  --restart unless-stopped \
  ccisnice/smart-photo-frame:latest
```

---

## 🛠️ Tech Stack

- **Cross-Platform Core**: Capacitor 8.x + Modern Vanilla HTML5 / CSS3 / ES6+
- **Local Microserver**: NanoHTTPD (Android Native) / Async Python 3 HTTP daemon
- **Image Pipeline**: Canvas 2K Bi-linear Resampling + GPU CSS Filters
- **Streaming Protocol**: HTTP 206 Partial Content (Smooth chunked playback for iOS Safari & WebViews)

---

## ☕ Support & Donate

If you find this open-source tool helpful and it gives your retired tablet a new life, consider buying the author a cup of coffee!

<div align="center">
  <p>
    <a href="https://ko-fi.com/chenxu1985" target="_blank">
      <img src="https://img.shields.io/badge/☕_Buy_Me_a_Coffee-Support_on_Ko--fi-ff5e5b?style=for-the-badge" alt="Support on Ko-fi" />
    </a>
    &nbsp;&nbsp;
    <a href="https://afdian.com/a/cash1985" target="_blank">
      <img src="https://img.shields.io/badge/⚡_Support_on_Afdian-Donate_Now-946ce6?style=for-the-badge" alt="Support on Afdian" />
    </a>
  </p>
  <br/>
  <img src="./assets/sponsor.png" width="260" alt="WeChat Sponsor QR" style="border-radius: 16px; box-shadow: 0 4px 20px rgba(0,0,0,0.15);" />
  <br/>
  <p><strong>Support via Ko-fi (PayPal/Card) • Afdian (Alipay/WeChat) • WeChat QR ☕</strong></p>
</div>

> Please leave your GitHub ID in the transfer note so I can add you to our **Sponsors list**! ❤️

---

## 📄 License

Distributed under the [GPL-3.0 License](LICENSE).
