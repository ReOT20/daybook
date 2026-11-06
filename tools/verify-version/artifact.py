"""Inspect the actual APK, including native libraries; no device or signing key needed."""
import hashlib
import re
import struct
import subprocess
import zipfile

PACKAGE = "com.joebywan.daybook"


def command(args, **kwargs):
    try:
        return subprocess.run([str(a) for a in args], check=True, capture_output=True, **kwargs).stdout
    except subprocess.CalledProcessError as error:
        details = error.stderr or error.stdout or "no diagnostic output"
        if isinstance(details, bytes):
            details = details.decode(errors="replace")
        raise RuntimeError(f"{args[0]} exited {error.returncode}: {details[-4000:].strip()}") from error


def arm64_alignment(data):
    if data[:6] != b"\x7fELF\x02\x01" or struct.unpack_from("<H", data, 18)[0] != 183:
        raise ValueError("expected a little-endian arm64 ELF64 library")
    offset = struct.unpack_from("<Q", data, 32)[0]
    size, count = struct.unpack_from("<HH", data, 54)
    if size < 56 or not count or offset + size * count > len(data):
        raise ValueError("invalid ELF program headers")
    loads = []
    for n in range(count):
        start = offset + n * size
        if struct.unpack_from("<I", data, start)[0] == 1:  # PT_LOAD
            file_offset, address = struct.unpack_from("<QQ", data, start + 8)
            alignment = struct.unpack_from("<Q", data, start + 48)[0]
            if alignment < 16384 or alignment & (alignment - 1) or address % alignment != file_offset % alignment:
                raise ValueError(f"LOAD segment is not 16 KiB aligned (p_align={alignment})")
            loads.append(alignment)
    if not loads:
        raise ValueError("ELF has no LOAD segments")
    return loads


def verify(apk, sdk, repo, production):
    tools = sdk / "build-tools/36.0.0"
    signature = command([tools / "apksigner", "verify", "--verbose", "--print-certs", apk], text=True)
    if "Verified using v2 scheme (APK Signature Scheme v2): true" not in signature:
        raise ValueError("apksigner did not prove a v2 signature")
    command([tools / "zipalign", "-c", "-P", "16", "4", apk])
    badging = command([tools / "aapt2", "dump", "badging", apk], text=True)
    package = re.search(r"^package: name='([^']+)' versionCode='([^']+)' versionName='([^']*)'", badging, re.M)
    if not package or package[1] != PACKAGE or "application-debuggable" in badging:
        raise ValueError("APK must be the non-debuggable Daybook package")
    if "android.permission.INTERNET" in badging:
        raise ValueError("APK unexpectedly requests INTERNET permission")
    native = {}
    with zipfile.ZipFile(apk) as archive:
        for name in archive.namelist():
            if name.startswith("lib/arm64-v8a/") and name.endswith(".so"):
                native[name] = arm64_alignment(archive.read(name))
    if not native:
        raise ValueError("APK contains no arm64 native libraries; nothing was checked")
    if production:
        command([repo / "tools/verify-apk.sh", apk])
    return {"file": str(apk), "sha256": hashlib.sha256(apk.read_bytes()).hexdigest(),
            "signing": "production certificate pinned" if production else "DISPOSABLE TEST SIGNATURE; not distributable",
            "version_code": package[2], "version_name": package[3], "arm64_load_alignments": native,
            "signature_verification": signature.strip()}


def sign_test_copy(unsigned, sdk, java, work, output):
    key = work / "test-only.jks"
    command([java / "bin/keytool", "-genkeypair", "-keystore", key, "-alias", "test-only",
             "-storepass", "daybook-test", "-keypass", "daybook-test", "-keyalg", "RSA",
             "-keysize", "2048", "-validity", "2", "-dname", "CN=Daybook disposable verification only"])
    # Alignment precedes signing; the original Gradle artifact is never modified.
    aligned = work / "aligned.apk"
    command([sdk / "build-tools/36.0.0/zipalign", "-P", "16", "4", unsigned, aligned])
    command([sdk / "build-tools/36.0.0/apksigner", "sign", "--ks", key, "--ks-key-alias", "test-only",
             "--ks-pass", "pass:daybook-test", "--key-pass", "pass:daybook-test", "--out", output, aligned])
    return output
