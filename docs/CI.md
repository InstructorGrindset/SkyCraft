# Continuous integration

Two workflows, both in `.github/workflows/`:

- **Check branch** (`check.yml`): every push to any branch but `dev` and `main`. It builds and tests what a
  push changed and publishes nothing.
- **Build test release** (`build.yml`): every push to `dev`. It builds both halves and publishes a test
  prerelease named `v<version>-build<run number>`, with the jar, the DLL and its PDB, and release notes
  listing the commits since the previous release (`tools/release_notes.py`).

GitHub stopped starting runs in October 2026 because the private repository had used its free minutes.
Check branch now runs only what each push needs, and its Windows jobs can run on the owner's own PC, where
they don't use the free minutes.

## What runs when

| Job | Runner | Runs | What it does | Time (measured 2026-10-14) |
|---|---|---|---|---|
| `changes` | Linux | every push | Works out what the push changed and which jobs below it needs | 5 s |
| `fabric` | Linux | every push | `gradlew build`: compiles the Fabric mod and runs its JUnit tests. When the push changed `.github/probe.txt`, it also prints the probed Minecraft API from the jars the build set up | about 1 min |
| `skse-tests` | Linux | the push changed `skse/` or `protocol/` | Compiles and runs the plugin's pure logic tests (`skse/tests`) and checks that the protocol header compiles, with g++ | under 1 min |
| `skse` | Windows | the push changed `skse/` or `protocol/` | Builds `SkyCraft.dll` with Visual Studio, CommonLibSSE-NG and vcpkg | 18 min on GitHub's runner |
| `smoke` | Windows | integration branches (a name containing `integration`), a manual run, or a head commit whose message contains `[smoke]` | Starts a real Fabric 26.3 dedicated server with the built jar (`tools/smoke_server.py`) and checks that the mixins apply, the worlds load and the effects work | 1.5 min |

**What a push changed.** `changes` compares the push with the commit the branch was at before it (the
push event's `before`). A new branch has no `before`, so it's compared with where it left `dev`. If a
newer push cancels a run (see below), the cancelled push never got its checks, so the next run compares
with the last run of the branch that finished. If nothing on the branch has finished yet, it compares
with `dev`. The job's log lists the changed files and which jobs it chose.

**A manual run** (Actions > Check branch > Run workflow, pick the branch) runs everything. After a pause
(for example the minutes ran out and runs failed to start), start one manual run on each branch you care
about.

**`[smoke]`**: put it anywhere in the last commit's message to get the smoke test on that push.

**Cancelling.** A newer push to the same branch cancels the Check branch run that's still going for an
older push. Only the newest commit gets checked.

**Probe entries** that don't exist print an error and the rest still print. The probe never fails the
check.

## Minutes

GitHub bills each job rounded up to a whole minute, and a Windows minute counts as two. A private
repository on GitHub Free gets 2,000 minutes a month (Pro: 3,000). Public repositories and self-hosted
runners don't use them.

Per push to a branch, from the job times above. The old workflow ran `changes`, `fabric`, `probe` and
`smoke` on every push, and `skse` on every push of a branch that differed from `dev` in `skse/` or
`protocol/`, which was every agent branch:

| Push | Before | Now |
|---|---|---|
| Changes only Java, docs or data, on an agent branch | changes 1 + fabric 2 + probe 1 + smoke 4 + skse 36 = **44** | changes 1 + fabric 2 = **3** |
| The same, on a branch that never touched `skse/` or `protocol/` | 1 + 2 + 1 + 4 = **8** | **3** |
| Changes `.github/probe.txt` | 44 | **3** (the probe is a step in `fabric`) |
| Changes `skse/` or `protocol/` | 44 | 1 + 2 + skse-tests 1 + 36 = **40** |
| Any push to an integration branch | 44 | 3 + smoke 4 = **7**, or **44** if it changed `skse/` or `protocol/` |
| Any push, with `SKYCRAFT_WINDOWS_RUNNER = self` | | Windows jobs cost nothing: at most **4** |

So a typical agent push went from about 44 minutes to 3, and only pushes that change the plugin still
cost about 40. 2,000 minutes used to last about 45 pushes. Now they last about 650 Java pushes, or about
50 plugin pushes.

Build test release (every push to `dev`) costs what it did: changes 1 + fabric 2 + skse 36 (only when
the push changed `skse/` or `protocol/`) + release 1, so 4 or 40 minutes. With the self-hosted runner
it's 4.

To see what was used: your profile picture > **Settings** > **Billing and licensing** > **Usage**, and
filter by Actions. Each run's page also has a **Usage** link.

## Caching

- **Gradle** (`gradle/actions/setup-gradle`): the wrapper, Minecraft, Fabric and Loom's processed jars.
  By default only runs on the repository's default branch (`main`) may save this cache, and nothing runs
  on `main`, so nothing was ever saved. Check branch now lets every branch save its own copy, so a
  branch's second push onwards starts warm. **Recommended:** make `dev` the default branch (Settings >
  General > Default branch > the switch icon > `dev` > Update). Then Build test release saves the cache
  on `dev`, every branch starts from it, and pull requests aim at `dev` by default. The cache holds 10 GB
  a repository and evicts the oldest entries itself.
- **vcpkg** (GitHub's Windows runner): the built packages are cached under a key that includes vcpkg's port
  versions. GitHub updates the runner's vcpkg about weekly. The old key named only `vcpkg.json`, so after
  an update the cache was never saved again and every run rebuilt the packages that had changed.
- **On the self-hosted PC** nothing goes through GitHub's cache. vcpkg's packages stay in
  `<runner>\_work\_tool\skycraft-vcpkg-cache`, and the plugin's build folder stays in
  `<runner>\_work\_tool\skycraft-skse-build`. A build compiles only what changed, so after the first one
  (about 20 minutes) a plugin build takes a minute or two. Delete the folders to start clean. If configuring
  fails in the kept folder (say after a Visual Studio update), the job deletes it and configures again.
  Build test release on the PC still builds in the checkout's `skse/build` (its artifact paths), so it
  compiles everything each time, but the vcpkg packages are kept.
- **Artifacts** count against the account's storage (GitHub Free: 500 MB). The smoke test's jar is kept 3
  days. Build test release uploads the jar, DLL and PDB on every `dev` push and keeps them 90 days by
  default, but the release keeps its own copies. Shorten that under Settings > Actions > General >
  **Artifact and log retention** (for example to 7 days).

## Running the Windows jobs on your own PC

A self-hosted runner is a small program from GitHub. It waits for jobs and runs them on your PC. Every
Windows job (Check branch's `skse` and `smoke`, Build test release's `skse`) uses it when the
repository variable `SKYCRAFT_WINDOWS_RUNNER` is `self`. Otherwise they use GitHub's `windows-latest`.

The runner runs whatever the workflows in this repository say, with the rights of the Windows account it
runs as. Only people with write access can push and so start jobs, and these workflows have no
`pull_request` triggers. Don't add one that runs on the self-hosted runner if the repository is ever
public: anyone could then run code on your PC with a pull request.

### 1. What the PC needs

Windows 10 or 11, 64-bit, about 15 GB free, and:

1. **Git for Windows** (<https://git-scm.com/download/win>, the defaults are fine). Then, in an
   administrator PowerShell: `git config --system core.longpaths true`.
2. **PowerShell 7** (`winget install Microsoft.PowerShell`). The jobs use `pwsh`, not Windows PowerShell 5.
3. **Python 3** (<https://www.python.org/downloads/windows/>). Tick **Add python.exe to PATH**. Then in
   Settings > Apps > Advanced app settings > **App execution aliases**, turn off the two Python entries
   (the Microsoft Store stubs). Only the smoke test needs it.
4. **Visual Studio 2026 or 2022** (Community is fine) with the **Desktop development with C++** workload.
   Its CMake tools come with it, and the job finds them. A separate CMake on the PATH is used first if
   there is one.
5. **vcpkg**, in an administrator PowerShell:
   ```powershell
   git clone https://github.com/microsoft/vcpkg C:\vcpkg
   C:\vcpkg\bootstrap-vcpkg.bat -disableMetrics
   [Environment]::SetEnvironmentVariable('VCPKG_INSTALLATION_ROOT', 'C:\vcpkg', 'Machine')
   ```
   That's the variable GitHub's own runners set. Run `git -C C:\vcpkg pull` now and then to get newer
   ports.

Java isn't needed: the jobs download their own (`setup-java`).

### 2. Add the runner

1. On GitHub, open the repository > **Settings** > **Actions** > **Runners** > **New self-hosted runner**.
2. Pick **Windows** and **x64**. The page shows the commands for your runner, with the current version and
   a one-time token that's valid for an hour. Copy them from that page, not from here. They look like this:
   ```powershell
   mkdir C:\actions-runner; cd C:\actions-runner
   Invoke-WebRequest -Uri https://github.com/actions/runner/releases/download/v<version>/actions-runner-win-x64-<version>.zip -OutFile actions-runner-win-x64-<version>.zip
   if((Get-FileHash -Path actions-runner-win-x64-<version>.zip -Algorithm SHA256).Hash.ToUpper() -ne '<hash>'.ToUpper()){ throw 'Computed checksum did not match' }
   Add-Type -AssemblyName System.IO.Compression.FileSystem ; [System.IO.Compression.ZipFile]::ExtractToDirectory("$PWD/actions-runner-win-x64-<version>.zip", "$PWD")
   ./config.cmd --url https://github.com/<owner>/<repository> --token <token>
   ```
   Run them in an **administrator** PowerShell. Keep the folder short (`C:\actions-runner`): the plugin's
   build paths are long, and Windows still trips over paths longer than 260 characters.
3. `config.cmd` asks some questions:
   - runner group, runner name, extra labels, work folder: press Enter for each. The default labels
     `self-hosted`, `Windows` and `X64` are what the workflows ask for.
   - **Would you like to run the runner as service? (Y/N)**: **Y**. Then it starts with Windows, and you
     don't have to be logged in.
   - **User account to use for the service**: Enter keeps `NT AUTHORITY\NETWORK SERVICE`. That account
     can't write to `C:\vcpkg`, which vcpkg needs. Either type your own account (`.\YourName` for a local
     account, or `YourName@outlook.com` for a Microsoft account) and its password, or give NETWORK SERVICE
     write access: `icacls C:\vcpkg /grant "NETWORK SERVICE:(OI)(CI)M"` in an administrator prompt.
4. Back on **Settings** > **Actions** > **Runners**, the runner is listed as **Idle**.

To run it only when you want (no service), answer N, and start `C:\actions-runner\run.cmd` when you want
the PC to take jobs. Close the window to stop it.

### 3. Point the Windows jobs at it

1. **Settings** > **Secrets and variables** > **Actions** > the **Variables** tab > **New repository
   variable**.
2. Name `SKYCRAFT_WINDOWS_RUNNER`, value `self`, **Add variable**.
3. Start a manual Check branch run (Actions > Check branch > Run workflow) and watch its `skse` and
   `smoke` jobs run on your PC (the job's "Set up job" step names the runner).

To go back to GitHub's runners, delete the variable or change its value to anything else. Do that when
the PC will be off for a while: until then Windows jobs wait in the queue for it (up to a day, then
they fail). Linux jobs always run on GitHub.

The runner updates itself. To remove it: Settings > Actions > Runners > the runner > **Remove** shows a
removal command (`./config.cmd remove --token <token>`), run in `C:\actions-runner` from an administrator
prompt. That also removes the service.

Builds on the PC never copy the DLL into Mod Organizer. That only happens with the `default` CMake
preset, and CI doesn't use it.

## A spending limit

Without a payment method, GitHub stops starting runs when the free minutes run out, which is what
happened in October 2026. With a payment method on the account, minutes over the free amount are
charged, and a budget decides how far that goes:

1. Your profile picture > **Settings** > **Billing and licensing** > **Budgets and alerts** (older pages
   call it **Spending limit**).
2. **New budget** (or **Edit** on the Actions one). Pick **Actions**, an amount (for example $0 to never
   pay, or a few dollars to let CI carry on past the free minutes), and tick **Stop usage when budget
   limit is reached**. Add an email alert at 75% or 90% to hear about it first.
3. Save.

For a repository owned by an organization, it's the organization's Settings > Billing and licensing.
GitHub's pricing page gives the current price per minute. A Windows minute costs twice a Linux one.
