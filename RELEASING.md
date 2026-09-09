# Releasing to Maven Central

One-time setup, then every release is a git tag.

## One-time setup

### 1. Central Portal account + namespace

1. Sign in at <https://central.sonatype.com> with the **GitHub account `mszajner`**.
2. Register the **`io.github.mszajner`** namespace. Because the name matches your
   GitHub login, the Portal verifies it automatically (it may ask you to create a
   throw-away public repo named after the verification code — follow the prompt).
3. Under *Account → Generate User Token*, create a token. Note the
   **username** and **password** it gives you.

### 2. GPG signing key

```bash
gpg --gen-key                                  # RSA 4096, real name + the project email
gpg --list-secret-keys --keyid-format long     # note the long KEYID
gpg --keyserver keyserver.ubuntu.com --send-keys <KEYID>   # publish the public key
gpg --armor --export-secret-keys <KEYID>       # copy this whole block for the CI secret
```

### 3. GitHub repository secrets

`Settings → Secrets and variables → Actions`:

| Secret | Value |
|---|---|
| `CENTRAL_USERNAME` | Portal token username |
| `CENTRAL_PASSWORD` | Portal token password |
| `GPG_PRIVATE_KEY` | the full `-----BEGIN PGP PRIVATE KEY BLOCK-----` export |
| `GPG_PASSPHRASE` | passphrase for that key |

For **local** releases instead, put the Portal token in `~/.m2/settings.xml`:

```xml
<settings>
  <servers>
    <server>
      <id>central</id>
      <username>TOKEN_USERNAME</username>
      <password>TOKEN_PASSWORD</password>
    </server>
  </servers>
</settings>
```

## Cutting a release

```bash
# 1. drop -SNAPSHOT
./mvnw -B versions:set -DnewVersion=0.1.0 -DgenerateBackupPoms=false

# 2. finalise CHANGELOG.md (move [Unreleased] items under a new ## [0.1.0] - <date>)

# 3. commit + tag
git commit -am "Release 0.1.0"
git tag v0.1.0
git push origin main v0.1.0        # the tag triggers .github/workflows/release.yml

# 4. bump to the next snapshot
./mvnw -B versions:set -DnewVersion=0.2.0-SNAPSHOT -DgenerateBackupPoms=false
git commit -am "Back to snapshot"
git push
```

The `release` workflow runs `./mvnw -Prelease deploy`, which:

- attaches `-sources.jar` and `-javadoc.jar`,
- GPG-signs every artifact,
- uploads the bundle via `central-publishing-maven-plugin` and (with
  `autoPublish=true`) publishes it once validation passes.

`beanquery-demo` is excluded (`maven.deploy.skip` / `skipPublishing`).

### Releasing from your machine

```bash
./mvnw -Prelease deploy
```

(needs the `~/.m2/settings.xml` server above, a local GPG key, and
`gpg-agent` able to prompt for the passphrase — or pass
`-Dgpg.passphrase=...`).
