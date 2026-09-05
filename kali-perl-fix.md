# Kali Linux (ARM64) — Perl Symlink Fix Note

**Repo:** [dev-boffin-io/proot-forge](https://github.com/dev-boffin-io/proot-forge)
**Target hardware:** ReTerminal (or similar ARM64/aarch64 device)
**Filesystem:** F2FS root
**OS:** Minimal Kali Linux

---

## Issue

During a package operation, `tar` failed to create symlinks with a permission error:

```
tar: can't link 'usr/bin/perl' -> 'usr/bin/perl5.40.1': Permission denied
tar: can't link 'usr/bin/perlbug' -> ...: Permission denied
tar: can't link 'usr/bin/perlthanks' -> ...: Permission denied
```

As a result, the `/usr/bin/perl` symlink was broken, and many tools that depend on Perl stopped working.

## Root Cause

The files `/usr/bin/perl5.40.1` and `/usr/bin/perlthanks` had the **immutable attribute (`+i`)** set (visible via `lsattr`), which prevented the package manager from creating or updating the symlinks.

## Fix Steps

```bash
# 1. Check current state
ls -l /usr/bin/perl /usr/bin/perl5.40.1 /usr/bin/perlthanks
mount | grep ' on / '
lsattr /usr/bin/perl5.40.1 /usr/bin/perlthanks
lsattr /usr/bin | grep perl

# 2. Remove the immutable attribute
chattr -i /usr/bin/perl5.40.1 /usr/bin/perlthanks

# 3. Reinstall Perl packages
apt update
apt install --reinstall perl perl-base perl-modules-5.40

# 4. Final checks
dpkg --configure -a
apt -f install
perl -v
ls -l /usr/bin/perl
```

## Result

```
This is perl 5, version 40, subversion 1 (v5.40.1) ...
/usr/bin/perl -> perl5.40.1   (correctly linked)
```

## Tips for the Future

- On a minimal Kali install, silence the welcome message with: `touch ~/.hushlogin`
- On F2FS / locked images or immutable filesystems, `chattr -i` may be required.
- Always run the following after any manual fix:
  ```bash
  apt update && apt -f install && dpkg --configure -a
  ```
