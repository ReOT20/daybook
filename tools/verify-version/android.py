"""A private AVD and adb server. Never operate on an existing emulator or physical device."""
import importlib.util
import json
import os
import re
import socket
import subprocess
import sys
import time
import xml.etree.ElementTree as ET
from contextlib import contextmanager
from pathlib import Path

from artifact import PACKAGE

IMAGE = "system-images;android-34;google_apis;x86_64"  # Same image family as the release save gate.
RUSSIAN_PUZZLES = ("words-ru", "wordwheel-ru", "mini-ru", "codeword-ru", "hexwords-ru", "dwindle-ru", "cipher-ru")


def retained_fixture_batch(directory):
    """One frozen Standard save per content edition, independent of save-check batch order."""
    wanted = set(RUSSIAN_PUZZLES) | {puzzle.removesuffix("-ru") for puzzle in RUSSIAN_PUZZLES}
    selected = {}
    for path in sorted(directory.glob("*.json")):
        for entry in json.loads(path.read_text()):
            puzzle, tier, _seed = entry["key"].split("|")
            if puzzle in wanted and tier == "STANDARD" and puzzle not in selected:
                expected_language = "ru" if puzzle in RUSSIAN_PUZZLES else "en"
                if entry["game"]["state"].get("contentLanguage", "en") != expected_language:
                    raise RuntimeError(f"retained fixture {puzzle} has the wrong content language")
                selected[puzzle] = entry
    if missing := wanted - selected.keys():
        raise RuntimeError(f"missing retained Standard save fixtures: {', '.join(sorted(missing))}")
    return [selected[puzzle] for puzzle in sorted(wanted)]


def seed_retained_fixtures(device, saves, batch):
    # The full minified gate has already checked these fixtures. Only replace its progress file;
    # keep launch preferences and direct every device operation through the private transport.
    raw = [json.dumps(entry, separators=(",", ":")) for entry in batch]
    local = device.out / "android-retained-fixtures.pb"
    local.write_bytes(saves.encode(saves.KEY, raw) + saves.encode(saves.OFFERED, ["sudoku"]))
    device.call("shell", "am", "force-stop", PACKAGE)
    remote = "/data/local/tmp/daybook-retained-fixtures.pb"
    device.call("push", local, remote)
    owner = device.call("shell", f"stat -c %u:%g /data/data/{PACKAGE}").strip()
    context = device.call("shell", f"stat -c %C /data/data/{PACKAGE}").strip()
    device.call("shell", f"cp {remote} {saves.STORE} && chown {owner} {saves.STORE} && chcon {context} {saves.STORE}")
    return sorted(raw)


def free_port():
    with socket.socket() as sock:
        sock.bind(("127.0.0.1", 0))
        return sock.getsockname()[1]


@contextmanager
def emulator(sdk, work, out, software=False):
    avd = work / "avd"
    avd.mkdir()
    user = work / "android"
    user.mkdir()
    port = free_port()
    env = dict(os.environ, ANDROID_HOME=str(sdk), ANDROID_AVD_HOME=str(avd),
               ANDROID_USER_HOME=str(user), ANDROID_EMULATOR_HOME=str(user),
               ADB_VENDOR_KEYS=str(user / "adbkey"), ADB_SERVER_SOCKET=f"tcp:127.0.0.1:{port}",
               ANDROID_ADB_SERVER_PORT=str(port), ADB_MDNS_AUTO_CONNECT="0")
    env.pop("ANDROID_SDK_ROOT", None)
    env.pop("ANDROID_SDK_HOME", None)
    # The adb server's USB allowlist is a nonexistent serial, so it cannot connect to personal USB devices.
    adb = sdk / "platform-tools/adb"
    subprocess.run([str(adb), "keygen", str(user / "adbkey")], env=env, check=True, capture_output=True, timeout=15)
    server_env = dict(env)
    server_env.pop("ADB_SERVER_SOCKET", None)
    server_log = (out / "adb-server.log").open("w")
    server = subprocess.Popen([str(adb), "-L", f"tcp:localhost:{port}", "--one-device", f"no-usb-{work.name}",
                               "server", "nodaemon"], env=server_env, stdout=server_log, stderr=subprocess.STDOUT)
    process = None
    try:
        deadline = time.monotonic() + 30
        while True:
            if server.poll() is not None:
                raise RuntimeError("private adb server exited; see adb-server.log")
            try:
                with socket.create_connection(("127.0.0.1", port), timeout=0.2):
                    break
            except OSError:
                if time.monotonic() >= deadline:
                    raise TimeoutError("private adb server did not start; see adb-server.log")
                time.sleep(0.2)
        name = "daybook-" + work.name
        with (out / "avd-create.log").open("w") as log:
            subprocess.run([str(sdk / "cmdline-tools/latest/bin/avdmanager"), "create", "avd", "-n", name,
                            "-k", IMAGE, "-p", str(avd / "device.avd"), "--device", "pixel_6"],
                           input="no\n", text=True, check=True, env=env, stdout=log, stderr=subprocess.STDOUT, timeout=180)
        # A fresh server may see other emulator ports; the transport is always pinned below.
        for console in range(5554, 5682, 2):
            with socket.socket() as first, socket.socket() as second:
                try:
                    first.bind(("127.0.0.1", console))
                    second.bind(("127.0.0.1", console + 1))
                    break
                except OSError:
                    continue
        else:
            raise RuntimeError("no free emulator console/adb port pair")
        env["ANDROID_SERIAL"] = f"emulator-{console}"
        with (out / "emulator.log").open("w") as log:
            args = [str(sdk / "emulator/emulator"), "-avd", name, "-port", str(console), "-no-window", "-no-metrics",
                    "-no-audio", "-no-snapshot", "-no-boot-anim", "-gpu", "swiftshader", "-feature", "-Vulkan", "-cores", "4",
                    "-show-kernel"]
            if software:
                args += ["-accel", "off"]
            process = subprocess.Popen(args, env=env, stdout=log, stderr=subprocess.STDOUT)
            device = Device(adb, env, out, 900 if software else 240)
            device.boot(process)
            if device.call("shell", "getprop", "ro.kernel.qemu").strip() != "1":
                raise RuntimeError("refusing a transport that is not an emulator")
            actual = device.call("emu", "avd", "name").splitlines()[0].strip()
            if actual != name:
                raise RuntimeError(f"refusing AVD {actual!r}; this harness did not create it")
            device.call("root")
            time.sleep(2)
            device.boot(process)
            yield device
    finally:
        if process is not None:
            process.terminate()
            try:
                process.wait(timeout=20)
            except subprocess.TimeoutExpired:
                process.kill()
                process.wait(timeout=10)
        server.terminate()
        try:
            server.wait(timeout=15)
        except subprocess.TimeoutExpired:
            server.kill()
            server.wait(timeout=10)
        server_log.close()


class Device:
    def __init__(self, adb, env, out, timeout):
        self.adb, self.env, self.out, self.timeout = str(adb), env, out, timeout

    def call(self, *args, check=True, binary=False):
        result = subprocess.run([self.adb, "-s", self.env["ANDROID_SERIAL"], *map(str, args)], env=self.env,
                                capture_output=True, check=check, timeout=90)
        return result.stdout if binary else result.stdout.decode(errors="replace")

    def boot(self, process=None):
        deadline = time.monotonic() + self.timeout
        while time.monotonic() < deadline:
            if process is not None and process.poll() is not None:
                raise RuntimeError(f"emulator exited ({process.returncode}); see emulator.log")
            if self.call("shell", "getprop", "sys.boot_completed", check=False).strip() == "1":
                return
            time.sleep(2)
        raise TimeoutError("emulator did not boot; see emulator.log")

    def system_language(self, language):
        if language not in ("en-US", "ru-RU"):
            raise ValueError("only the two supported test system languages are allowed")
        self.call("shell", f"setprop persist.sys.locale {language}; setprop sys.boot_completed 0; stop; sleep 2; start")
        self.boot()
        if self.call("shell", "getprop", "persist.sys.locale").strip() != language:
            raise RuntimeError("the system language did not change")

    def app_language(self, expected):
        output = self.call("shell", "cmd", "locale", "get-app-locales", PACKAGE, "--user", "0")
        match = re.search(r"are \[(.*?)\]", output)
        if not match or match[1] != expected:
            raise RuntimeError(f"native app-language setting was not {expected!r}: {output.strip()}")

    def set_system_app_language(self, language):
        if language not in ("en", "ru", ""):
            raise ValueError("unsupported native app language")
        self.call("shell", f"cmd locale set-app-locales {PACKAGE} --user 0 --locales '{language}'")
        self.app_language(language)

    def nodes(self):
        raw = self.call("exec-out", "uiautomator", "dump", "/dev/tty", check=False)
        if "<?xml" not in raw or "</hierarchy>" not in raw:
            return []
        root = ET.fromstring(raw[raw.index("<?xml"):raw.rindex("</hierarchy>") + len("</hierarchy>")])
        return list(root.iter("node"))

    def find(self, text):
        for node in self.nodes():
            if any(text in (value or "").splitlines() for value in (node.get("text"), node.get("content-desc"))):
                bounds = list(map(int, re.findall(r"\d+", node.get("bounds", ""))))
                if len(bounds) == 4 and bounds[3] > bounds[1]:
                    return (bounds[0] + bounds[2]) // 2, (bounds[1] + bounds[3]) // 2
        return None

    def wait(self, text):
        deadline = time.monotonic() + 90
        while time.monotonic() < deadline:
            if xy := self.find(text):
                return xy
            time.sleep(1)
        (self.out / "android-failure-ui.json").write_text(json.dumps([n.attrib for n in self.nodes()], ensure_ascii=False))
        raise RuntimeError(f"Android did not show {text!r}")

    def tap(self, text):
        self.call("shell", "input", "tap", *self.wait(text))

    def start(self):
        self.call("shell", "am", "force-stop", PACKAGE)
        self.call("shell", "am", "start", "-W", "-n", f"{PACKAGE}/.MainActivity")

    def screenshot(self, name):
        (self.out / f"android-{name}.png").write_bytes(self.call("exec-out", "screencap", "-p", binary=True))

    def settings(self, russian):
        self.tap("Настройки" if russian else "Settings")
        self.wait("Язык" if russian else "Language")


def check(device, apk, repo):
    device.system_language("en-US")
    # Reuse the release gate, but bind every adb subprocess to our private server and owned AVD.
    env = dict(device.env, PATH=str(Path(device.adb).parent) + os.pathsep + os.environ["PATH"],
               RUNNER_TEMP=str(device.out))
    with (device.out / "android-save-check.log").open("w") as log:
        subprocess.run([sys.executable, str(repo / "tools/save-check/check.py"), str(apk)],
                       check=True, env=env, stdout=log, stderr=subprocess.STDOUT, timeout=900)
    spec = importlib.util.spec_from_file_location("save_check", repo / "tools/save-check/check.py")
    saves = importlib.util.module_from_spec(spec)
    spec.loader.exec_module(saves)

    def stored():
        raw = device.call("exec-out", f"cat {saves.STORE}", binary=True)
        return sorted(saves.decode(raw))

    batch = retained_fixture_batch(repo / "app/src/test/resources/saves")
    retained_ids = {entry["key"].split("|")[0] for entry in batch}
    if set(RUSSIAN_PUZZLES) - retained_ids:
        raise RuntimeError("retained fixtures must include all seven Russian puzzle IDs")
    before = seed_retained_fixtures(device, saves, batch)
    if stored() != before:
        raise RuntimeError("could not seed the mixed English/Russian retained save fixtures")
    device.start()
    device.settings(False)
    device.screenshot("system-en")
    device.tap("Language")
    device.tap("Русский")
    device.wait("Язык")
    device.app_language("ru")
    device.screenshot("explicit-ru")
    device.start()
    device.settings(True)
    device.wait("Язык")
    device.system_language("ru-RU")
    device.start()
    device.settings(True)
    device.tap("Язык")
    device.tap("English")
    device.wait("Language")
    device.app_language("en")
    device.screenshot("explicit-en-on-ru-system")
    # A real in-place install must retain the chosen language and saved boards.
    device.call("install", "-r", str(apk))
    device.start()
    device.settings(False)
    device.tap("Language")
    device.tap("System default")
    device.wait("Язык")
    device.app_language("")
    device.screenshot("system-ru")
    # Android Settings and the in-app picker must share LocaleManager, not independent preferences.
    device.set_system_app_language("en")
    device.start()
    device.settings(False)
    device.screenshot("native-settings-en")
    device.set_system_app_language("")
    device.start()
    device.settings(True)
    device.system_language("en-US")
    device.start()
    device.settings(False)
    device.wait("Language")
    device.screenshot("system-en-restored")
    device.call("shell", "am", "force-stop", PACKAGE)
    if stored() != before:
        raise RuntimeError("saved game fixtures changed during language switching or in-place reinstall")
    return {"avd": "private API 34 google_apis x86_64", "serial": device.env["ANDROID_SERIAL"],
            "save_fixtures_retained_across_language_changes": len(before),
            "retained_save_puzzle_ids": sorted(retained_ids),
            "cases": ["system en", "explicit ru on en", "explicit en on ru", "in-place reinstall",
                      "system ru", "native Android app-language override", "restart persistence", "system en restored"]}
