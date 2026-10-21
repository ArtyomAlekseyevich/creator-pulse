# Release signing key

`creatorpulse-release.jks` signs every release build. Android only installs an update over an existing
install if it is signed with the same key, so **back this file and `../keystore.properties` up somewhere
safe** (password manager, encrypted drive). Both are git-ignored on purpose.

For GitHub Actions the key is provided through repository secrets:

- `KEYSTORE_BASE64` – `base64 -w0 keystore/creatorpulse-release.jks`
- `KEYSTORE_PASSWORD`, `KEY_ALIAS`, `KEY_PASSWORD` – the values from `keystore.properties`
