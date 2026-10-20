# Contributing to SkyCraft

Thanks for helping. SkyCraft has two halves that talk through shared memory: a Skyrim SKSE plugin
(C++, `skse/`) and a Minecraft Fabric mod (Java, `fabric/`), with the shared layout in
`protocol/skycraft_protocol.h`. Read the [README](README.md), [DESIGN.md](docs/DESIGN.md) (how the halves
fit together) and [FEATURES.md](docs/FEATURES.md) (what each feature does, where its code is, and how
to test it) before changing either half.

## Building

### The Minecraft half (any OS)

You need JDK 25. Gradle and Minecraft are downloaded by the build itself.

```sh
cd fabric
./gradlew build          # compiles, runs the JUnit tests, writes build/libs/skycraft-<version>.jar
./gradlew test           # the tests alone
./gradlew runClient      # a development Minecraft with the mod
```

Without Skyrim, `tools/fake_skyrim.py` stands in for it: it creates the shared memory and plays
Skyrim's side, so `runClient` connects to it (`tools/test_mc.sh <out dir>` runs both and prints what
happened). `tools/smoke_server.py <jar>` starts a real Fabric dedicated server with the jar (Windows,
since the mod binds kernel32).

### The Skyrim half (Windows)

You need Visual Studio 2026 or 2022 with **Desktop development with C++**, CMake 3.25 or newer, and Git.

```bat
git submodule update --init --recursive
git clone https://github.com/microsoft/vcpkg .tools\vcpkg
.tools\vcpkg\bootstrap-vcpkg.bat

cd skse
cmake --preset default
cmake --build --preset release
```

The DLL is `skse\build\RelWithDebInfo\SkyCraft.dll`. If
`%LOCALAPPDATA%\ModOrganizer\Skyrim Special Edition\mods\SkyCraft` exists, each build is copied there
for Mod Organizer 2 (`SKYCRAFT_DEPLOY_DIR` in `skse/CMakePresets.json`).

The plugin's pure logic (no game types) has tests in `skse/tests` that build with any compiler:

```sh
g++ -std=c++20 -I skse/src -I protocol skse/tests/StripsTest.cpp -o strips_test && ./strips_test
```

or `cmake -DSKYCRAFT_TESTS=ON` and `ctest`.

### Both, packaged

`powershell -ExecutionPolicy Bypass -File tools\package.ps1` builds both halves and writes the release
files to `dist\` (`-NoBuild` packages what's already built).

## Branches and pull requests

- `dev` is where work comes together. **Every push to `dev` publishes a public test prerelease**, so
  nothing goes there without a review. `main` holds releases.
- Start a branch from `dev`, one branch per change, and open a pull request against `dev`. Keep it to
  one feature or fix; say what it does in the game and how you tested it (the template asks).
- Commit messages: a short title, naming the area first when there is one ("Loot: drops land above the
  ground"), a blank line, then what the change does and why. The release notes group commits by that
  area.
- Match the code around you: tabs, comments that say what the code does in the game and why, Mojang
  names on the Minecraft side, `SkyCraft.LOG` with a `SkyCraft-<area>:` prefix, `logger::info` (rate
  limited) on the Skyrim side.
- A new feature gets a switch in `fabric/src/main/java/dev/skycraft/config/Features.java` (players see
  it in `skycraft-features.txt`), a row in the feature map in `docs/FEATURES.md`, and a line in the
  README's "What works" if players would care. Numbers players may want to change go in a commented
  config file (`config/Config.read`).
- The protocol header and `fabric/src/main/java/dev/skycraft/link/Proto.java` change together. An
  addition that an older half can ignore goes in "within version 13" with a comment saying so, and a
  table in FEATURES.md's "Protocol 13" section.
- Logic that can be tested without the games goes in classes of its own with tests: JUnit 5 under
  `fabric/src/test`, plain C++ under `skse/tests`.

## What CI checks

Pushing a branch to this repository runs **Check branch** (`.github/workflows/check.yml`):

- every push: the Fabric mod's build and JUnit tests;
- when the push changed `skse/` or `protocol/`: the plugin's pure logic tests (Linux) and the plugin's
  Windows build;
- when the push changed `.github/probe.txt`: prints the Minecraft API it lists (see the workflow's
  comments), for writing code against Minecraft's classes without its jars;
- integration branches, manual runs and commits with `[smoke]` in their message: a real Fabric server
  started with the built jar.

[docs/CI.md](docs/CI.md) explains each job and what it costs. A pull request from a fork doesn't run
the checks by itself. A maintainer runs them by pushing your branch here, so build and test locally
first.

## How contributions are credited

- **Your authorship is kept.** A pull request is merged, or your commits are cherry-picked, with the
  author and `Co-Authored-By` lines you recorded. Nobody rewrites them under someone else's name.
- **`Co-Authored-By`** lines credit everyone who worked on a commit, one per person, at the end of the
  message:
  ```
  Co-Authored-By: Your Name <you@example.com>
  ```
  If a tool or bot made the commit for you, put your own line in so the credit reaches you.
- **The credits:** [CREDITS.md](CREDITS.md) and the README's credits section name each contributor and
  what they did, and `fabric/src/main/resources/fabric.mod.json` lists them under `contributors`, so
  Minecraft's mod lists (Mod Menu and the like) show them. Big contributions are named in the
  feature's FEATURES.md row too.
- **Release notes** thank every commit author and co-author in that build automatically
  (`tools/release_notes.py`).

For example, Higor Machado ([waTools-br](https://github.com/waTools-br)) fixed loot and gear drop state
that was never cleared, lifted drops out of blocks, and added coin and odds-and-ends drops for people,
in their fork (commit `f10a604` on `waTools-br/SkyCraft` `dev-higor`). It was cherry-picked here as
they recorded it, and they were added to `fabric.mod.json`'s contributors, the README's credits and the
kill drops row in FEATURES.md (commit `0b97668`).

## Reporting a problem

Say what you did and what happened, and attach `Documents\My Games\Skyrim Special Edition\SKSE\SkyCraft.log`
and Minecraft's `logs\latest.log` (in the SkyCraft instance's `.minecraft` folder). `bDiagnostics = 1`
in `Data\SKSE\Plugins\SkyCraft.ini` makes SkyCraft's log much more detailed.

## License

SkyCraft is [MIT](LICENSE). By contributing, you agree that your contribution is released under the
same license.
