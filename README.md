# Proot Forge
**Proot Forge** is a sleek, Material 3-inspired terminal emulator designed as a modern alternative to the legacy [Jackpal Terminal](https://github.com/jackpal/Android-Terminal-Emulator). Built on [Termux's](https://github.com/termux/termux-app) robust TerminalView

Download the latest APK from the [Releases Section](https://github.com/dev-boffin-io/proot-forge/releases/latest).

# Features
- [x] Basic Terminal
- [x] Virtual Keys
- [x] Multiple Sessions
- [x] Debian, Kali Linux and Kali NetHunter sessions
- [x] Configurable Keyboard Shortcuts (Paste, Session Management)

# Known Issues
- On first boot on Kali rootfs, you may see:
  ```
  tar: can't link 'usr/bin/perl' -> 'usr/bin/perl5.40.1': Permission denied
  ```
  This happens because `perl5.40.1` and `perlthanks` ship with the immutable
  attribute (`+i`) set, which blocks symlink creation on extraction and can
  leave `/usr/bin/perl` unlinked.

  Full root-cause analysis and step-by-step fix: [kali-perl-fix.md](./kali-perl-fix.md)

  Quick fix from inside the terminal:
  ```bash
  chattr -i /usr/bin/perl5.40.1 /usr/bin/perlthanks
  apt update
  apt install --reinstall perl perl-base perl-modules-5.40
  ```

# Screenshots
<div>
  <img src="/fastlane/metadata/android/en-US/images/phoneScreenshots/01.png" width="32%" />
  <img src="/fastlane/metadata/android/en-US/images/phoneScreenshots/02.jpg" width="32%" />
  <img src="/fastlane/metadata/android/en-US/images/phoneScreenshots/03.jpg" width="32%" />
</div>

## Community
> [!TIP]
Join the Proot Forge community to stay updated and engage with other users:
- [Telegram](https://t.me/reTerminal)
