<a id="readme-top"></a>

<div align="center">
  <b>English</b> &nbsp;|&nbsp; <a href="README.ar.md">العربية</a>
</div>

<!-- PROJECT SHIELDS -->
<div align="center">

[![Stargazers][stars-shield]][stars-url]
[![Forks][forks-shield]][forks-url]
[![Issues][issues-shield]][issues-url]

</div>

<!-- PROJECT LOGO -->
<br />
<div align="center">
  <a href="https://github.com/YOUR_GITHUB_USERNAME/ps3mio">
    <img src="docs/images/logo.png" alt="ps3mio logo" width="200" height="200">
  </a>

  <h3 align="center">ps3mio</h3>

  <p align="center">
    Stremio for the PlayStation 3 (and Android, for testing).
    <br />
    <br />
    <a href="https://github.com/YOUR_GITHUB_USERNAME/ps3mio/actions">Builds</a>
    &middot;
    <a href="https://github.com/YOUR_GITHUB_USERNAME/ps3mio/issues">Report Bug</a>
    &middot;
    <a href="https://github.com/YOUR_GITHUB_USERNAME/ps3mio/issues">Request Feature</a>
  </p>
</div>

<!-- TABLE OF CONTENTS -->
<details>
  <summary>Table of Contents</summary>
  <ol>
    <li><a href="#about-the-project">About The Project</a>
      <ul><li><a href="#built-with">Built With</a></li></ul>
    </li>
    <li><a href="#getting-started">Getting Started</a>
      <ul>
        <li><a href="#builds">Builds</a></li>
        <li><a href="#project-layout">Project layout</a></li>
      </ul>
    </li>
    <li><a href="#roadmap">Roadmap</a></li>
    <li><a href="#star-history">Star History</a></li>
    <li><a href="#license">License</a></li>
    <li><a href="#acknowledgments">Acknowledgments</a></li>
  </ol>
</details>

<!-- ABOUT THE PROJECT -->
## About The Project

[![ps3mio screenshot][product-screenshot]](#about-the-project)

ps3mio brings the Stremio experience to the PlayStation 3. The app is built on the
[Movian](https://github.com/andoma/movian) engine, which is light enough for the old PS3 hardware: Movian provides
the video player (with the PS3 hardware decoder), BitTorrent streaming and networking, while everything you see is Stremio:

* A Stremio-style interface: Board, Discover, Library, Search, Addons and Settings
* Log in with your Stremio account (link code or e-mail) and use your own addons
* Continue Watching, seasons and episodes, and a list of streams with seeders and file size
* Subtitles from your addons, including WebVTT
* A loading screen with the title logo that fills in as the stream buffers

The interface, the Stremio logic and the addon support are built into the app. They are developed and built together
as one project.

<p align="right">(<a href="#readme-top">back to top</a>)</p>

### Built With

* [![Movian][Movian-badge]][Movian-url]
* [![JavaScript][JS-badge]][JS-url]
* [![GitHub Actions][GHA-badge]][GHA-url]

<p align="right">(<a href="#readme-top">back to top</a>)</p>

<!-- GETTING STARTED -->
## Getting Started

### Builds

Builds run from the **Actions** tab of the repository.

| Workflow | Output | When it runs |
|---|---|---|
| Engine - Android APK | `ps3mio-engine.apk` | Automatically on every push that touches `engine/` |
| Engine - PS3 PKG | `ps3mio.pkg` and `ps3mio_geohot.pkg` | Manually |

The Android build is the same engine, player and interface as the PS3 build, so it is used to test changes quickly.

### Project layout

* `engine/skin/` - the ps3mio interface (navigation bar, Board, Discover, Library, title page, loading screen)
* `engine/plugin/` - the built-in Stremio logic (API, addons, settings)
* `engine/ps3mio_patch.py` - turns a Movian source tree into ps3mio
* `docs/` - architecture notes

<p align="right">(<a href="#readme-top">back to top</a>)</p>

<!-- ROADMAP -->
## Roadmap

- [x] Stremio interface on top of the Movian engine
- [x] Stremio account login and addon sync
- [x] WebVTT subtitles from addons
- [x] Arabic and English text font, with Arabic letter joining and right-to-left order
- [x] 10 second buffer before playback starts and after every stall
- [ ] Player menu redesigned to match Stremio
- [ ] Subtitle settings (size and colors) inside the Stremio settings page
- [ ] Testing on a real PS3

See the [open issues](https://github.com/YOUR_GITHUB_USERNAME/ps3mio/issues) for known problems.

<p align="right">(<a href="#readme-top">back to top</a>)</p>

<!-- STAR HISTORY -->
## Star History

<a href="https://star-history.com/akram-vzx/ps3mio&Date">
  <picture>
    <source media="(prefers-color-scheme: dark)" srcset="https://api.star-history.com/svg?repos=YOUR_GITHUB_USERNAME/ps3mio&type=Date&theme=dark" />
    <source media="(prefers-color-scheme: light)" srcset="https://api.star-history.com/svg?repos=YOUR_GITHUB_USERNAME/ps3mio&type=Date" />
    <img alt="Star History Chart" src="https://api.star-history.com/svg?repos=YOUR_GITHUB_USERNAME/ps3mio&type=Date" />
  </picture>
</a>

<p align="right">(<a href="#readme-top">back to top</a>)</p>

<!-- LICENSE -->
## License

ps3mio is built on Movian, which is distributed under the GPL-3.0 license. See the Movian repository for details.

<p align="right">(<a href="#readme-top">back to top</a>)</p>

<!-- ACKNOWLEDGMENTS -->
## Acknowledgments

* [Movian](https://github.com/andoma/movian) by Andreas Öman and contributors
* [Stremio](https://www.stremio.com) - the interface and addon protocol that ps3mio follows
* [Best-README-Template](https://github.com/othneildrew/Best-README-Template)
* [Img Shields](https://shields.io) and [Star History](https://star-history.com)

<p align="right">(<a href="#readme-top">back to top</a>)</p>

<!-- MARKDOWN LINKS & IMAGES -->
[forks-shield]: https://img.shields.io/github/forks/YOUR_GITHUB_USERNAME/ps3mio.svg?style=for-the-badge
[forks-url]: https://github.com/YOUR_GITHUB_USERNAME/ps3mio/network/members
[stars-shield]: https://img.shields.io/github/stars/YOUR_GITHUB_USERNAME/ps3mio.svg?style=for-the-badge
[stars-url]: https://github.com/YOUR_GITHUB_USERNAME/ps3mio/stargazers
[issues-shield]: https://img.shields.io/github/issues/YOUR_GITHUB_USERNAME/ps3mio.svg?style=for-the-badge
[issues-url]: https://github.com/YOUR_GITHUB_USERNAME/ps3mio/issues
[product-screenshot]: docs/images/screenshot.png
[Movian-badge]: https://img.shields.io/badge/Movian-engine-7b5bf5?style=for-the-badge
[Movian-url]: https://github.com/andoma/movian
[JS-badge]: https://img.shields.io/badge/JavaScript-222222?style=for-the-badge&logo=javascript&logoColor=F7DF1E
[JS-url]: https://developer.mozilla.org/docs/Web/JavaScript
[GHA-badge]: https://img.shields.io/badge/GitHub_Actions-2088FF?style=for-the-badge&logo=githubactions&logoColor=white
[GHA-url]: https://github.com/features/actions
