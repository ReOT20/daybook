#!/usr/bin/env python3
"""Verify the current version on a private Android emulator and offline headless browsers."""
import argparse
import fnmatch
import importlib.metadata
import json
import os
import shutil
import signal
import subprocess
import sys
import tempfile
import time
import xml.etree.ElementTree as ET
from pathlib import Path

from artifact import sign_test_copy, verify
import android

REPO = Path(__file__).resolve().parents[2]
SIGNING_ENV = ("DAYBOOK_KEYSTORE", "DAYBOOK_KEYSTORE_PASSWORD", "DAYBOOK_KEY_ALIAS", "DAYBOOK_KEY_PASSWORD")
TESTS = ("*Catalog*Test", "*Language*Test", "*LocalizationTest", "*BoardPinTest", "*SaveCompatibilityTest",
         "*HomeOrderTest", "*HomeTrainingModeTest", "*TodaysThreeTest", "*NextStepsTest",
         "*LevelsTest", "*LevelProgressTest", "*LevelsUiTest",
         "*StateSerializationTest", "*ShareCardTest", "*GuideTest", "*TierNotesTest", "*AchievementsTest", "*FinishPraiseTest", "*BoardTextSizeTest",
         "*RussianWordsTest", "*RussianWordGamesTest", "*RussianCrosswordRulesTest", "*RussianCrosswordTutorialTest")


def prerequisites(software):
    missing = []
    sdk = Path(os.environ.get("ANDROID_HOME", ""))
    java = Path(os.environ.get("JAVA_HOME", ""))
    for path in [java / "bin/java", java / "bin/keytool", sdk / "build-tools/36.0.0/apksigner",
                 sdk / "build-tools/36.0.0/zipalign", sdk / "build-tools/36.0.0/aapt2",
                 sdk / "platform-tools/adb", sdk / "cmdline-tools/latest/bin/avdmanager",
                 sdk / "platforms/android-37.0/android.jar", sdk / "emulator/emulator",
                 sdk / "system-images/android-34/google_apis/x86_64/system.img"]:
        if not path.is_file():
            missing.append(str(path))
    if (java / "bin/java").is_file():
        version = subprocess.run([str(java / "bin/java"), "-version"], capture_output=True, text=True).stderr
        if 'version "17.' not in version:
            missing.append("JAVA_HOME must select Java 17")
    for name in ("playwright", "Pillow", "numpy", "Babel"):
        try:
            importlib.metadata.version(name)
        except importlib.metadata.PackageNotFoundError:
            missing.append(f"{name} (install tools/verify-version/requirements.txt)")
    if shutil.disk_usage(tempfile.gettempdir()).free < 10 * 1024 ** 3:
        missing.append("10 GiB free in TMPDIR for the AVD (set TMPDIR to a private disk-backed directory)")
    if sys.platform.startswith("linux") and not software and not os.access("/dev/kvm", os.R_OK | os.W_OK):
        missing.append("accessible /dev/kvm (or explicitly pass --software-emulator for slow CPU emulation)")
    if not missing:
        from playwright.sync_api import sync_playwright
        with sync_playwright() as pw:
            for engine in (pw.chromium, pw.webkit):
                if not Path(engine.executable_path).is_file():
                    missing.append(f"Playwright {engine.name}: run python -m playwright install chromium webkit")
    return sdk.resolve(), java.resolve(), missing


def test_results(directory, patterns):
    suites = [ET.parse(path).getroot() for path in directory.glob("TEST-*.xml")]
    for pattern in patterns:
        matched = [suite for suite in suites if fnmatch.fnmatch(suite.attrib["name"], pattern)]
        if not matched or not sum(int(s.attrib["tests"]) - int(s.attrib.get("skipped", 0)) for s in matched):
            raise RuntimeError(f"no executed JVM test class matched {pattern}; cannot claim this contract passed")
    if any(int(s.attrib.get("failures", 0)) or int(s.attrib.get("errors", 0)) for s in suites):
        raise RuntimeError("JUnit reports contain a failure")
    return {key: sum(int(s.attrib.get(key, 0)) for s in suites) for key in ("tests", "failures", "errors", "skipped")}


def main():
    def interrupted(signum, _frame):
        raise KeyboardInterrupt(f"signal {signum}")
    signal.signal(signal.SIGTERM, interrupted)
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--apk", type=Path, help="verify this production-signed APK against android/release-key.sha256")
    parser.add_argument("--out", type=Path, help="logs, screenshots and report directory (default: fresh /tmp directory)")
    parser.add_argument("--full", action="store_true", help="also run all JVM tests and 365-day JVM/browser parity")
    parser.add_argument("--software-emulator", action="store_true", help="explicitly allow slow emulation without KVM")
    parser.add_argument("--preflight", action="store_true", help="check prerequisites only; no builds, server or emulator")
    args = parser.parse_args()
    sdk, java, missing = prerequisites(args.software_emulator)
    if args.apk and not args.apk.is_file():
        missing.append(f"no APK: {args.apk}")
    if missing:
        print("Missing verification prerequisites:\n  " + "\n  ".join(missing), file=sys.stderr)
        print("See docs/VERIFY_VERSION.md. No checks were skipped or reported as passing.", file=sys.stderr)
        return 2
    if args.preflight:
        print("Prerequisites found. No runtime checks performed.")
        return 0
    out = args.out.resolve() if args.out else Path(tempfile.mkdtemp(prefix="daybook-version-"))
    out.mkdir(parents=True, exist_ok=True)
    if (out / "report.json").exists():
        parser.error("--out already contains report.json; choose a fresh directory to avoid stale evidence")
    env = dict(os.environ, JAVA_HOME=str(java), ANDROID_HOME=str(sdk))
    env.pop("ANDROID_SDK_ROOT", None)
    for key in SIGNING_ENV:
        env.pop(key, None)  # This command never reads the production signing key.
    report = {"result": "incomplete", "source_commit": subprocess.check_output(
        ["git", "rev-parse", "HEAD"], cwd=REPO, text=True).strip(),
        "working_tree_dirty": bool(subprocess.check_output(["git", "status", "--porcelain"], cwd=REPO)),
        "full": args.full, "stages": [], "output": str(out),
        "python_packages": {name: importlib.metadata.version(name) for name in ("playwright", "Pillow", "numpy", "Babel")}}

    def save_report():
        (out / "report.json").write_text(json.dumps(report, ensure_ascii=False, indent=2) + "\n")

    def stage(name, action):
        item = {"name": name, "result": "running"}
        report["stages"].append(item)
        save_report()
        print(f"verify-version: {name}", flush=True)
        start = time.monotonic()
        try:
            item["details"] = action()
            item["result"] = "passed"
        except (Exception, KeyboardInterrupt) as error:
            item["result"], item["error"] = "failed", str(error)
            raise
        finally:
            item["seconds"] = round(time.monotonic() - start, 1)
            save_report()

    def run(name, command, extra=None):
        log = out / f"{name}.log"
        with log.open("w") as handle:
            subprocess.run([str(a) for a in command], cwd=REPO, env=dict(env, **(extra or {})),
                           check=True, stdout=handle, stderr=subprocess.STDOUT)
        return {"log": log.name}

    gradle = [REPO / "gradlew", "--no-daemon", "--max-workers=4"]
    try:
        with tempfile.TemporaryDirectory(prefix="daybook-verification-private-") as private:
            work = Path(private)
            stage("harness-self-checks", lambda: run("harness-self-checks", [sys.executable, "-m", "unittest", "discover",
                  "-s", REPO / "tools/verify-version", "-p", "test_*.py"]))
            stage("catalog-contracts-and-fonts", lambda: run("catalog-contracts-and-fonts", [sys.executable, "-m", "unittest", "discover",
                  "-s", REPO / "tools/i18n", "-p", "test_*.py"]))
            stage("catalog-completeness", lambda: run("catalog-completeness", [sys.executable, "tools/i18n/build.py", "--check"]))
            test_command = gradle + [":app:testDebugUnitTest", "--rerun"]
            if not args.full:
                for pattern in TESTS:
                    test_command += ["--tests", pattern]
            def jvm_tests():
                result = run("jvm-tests", test_command)
                result.update(test_results(REPO / "app/build/test-results/testDebugUnitTest", TESTS))
                return result
            stage("jvm-tests", jvm_tests)
            tasks = [":web:wasmJsBrowserDistribution"] + ([] if args.apk else [":app:assembleRelease"])
            stage("build-artifacts", lambda: run("build-artifacts", gradle + tasks))
            apk = args.apk.resolve() if args.apk else out / "daybook-TEST-ONLY.apk"
            if not args.apk:
                stage("disposable-test-signature", lambda: str(sign_test_copy(
                    REPO / "app/build/outputs/apk/release/app-release-unsigned.apk", sdk, java, work, apk)))
            stage("apk-signature-installability", lambda: verify(apk, sdk, REPO, bool(args.apk)))
            stage("android-runtime-and-saves", lambda: check_android(sdk, work, out, apk, args.software_emulator))
            stage("browser-locales-offline", lambda: run("browser-locales-offline",
                  [sys.executable, "tools/verify-version/web.py", "--out", out / "web"]))
            if args.full:
                dump = out / "jvm-parity.txt"
                stage("jvm-year-dump", lambda: run("jvm-year-dump", gradle + [":app:testDebugUnitTest", "--rerun",
                      "--tests", "*WebParityDumpTest*"], {"DAYBOOK_PARITY_DAYS": "365", "DAYBOOK_PARITY_DUMP": str(dump)}))
                stage("browser-year-parity", lambda: run("browser-year-parity",
                      [sys.executable, "tools/web-parity/check.py", "--jvm", dump]))
            report["result"] = "passed"
    except (Exception, KeyboardInterrupt) as error:
        report["result"], report["error"] = "failed", str(error)
        print(f"verify-version: FAIL: {error}\nEvidence: {out}", file=sys.stderr)
        return 1
    finally:
        save_report()
    print(f"verify-version: PASS. Evidence: {out}")
    if not args.apk:
        print("APK has a disposable TEST signature. Production certificate continuity was not checked.")
    print("Screenshots are evidence for visual review; automatic checks do not prove every label fits.")
    return 0


def check_android(sdk, work, out, apk, software):
    with android.emulator(sdk, work, out, software) as device:
        try:
            return android.check(device, apk, REPO)
        except Exception:
            try:
                device.screenshot("failure")
            except Exception:
                pass  # Preserve the original failure if the emulator itself is gone.
            raise


if __name__ == "__main__":
    raise SystemExit(main())
